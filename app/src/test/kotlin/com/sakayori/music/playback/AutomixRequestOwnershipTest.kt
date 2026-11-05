/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.app.Application
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.sakayori.innertube.InnerTube
import com.sakayori.innertube.YouTube
import com.sakayori.innertube.models.SongItem
import com.sakayori.music.extensions.toMediaItem
import com.sakayori.music.constants.SimilarContent
import com.sakayori.music.constants.DisableLoadMoreWhenRepeatAllKey
import com.sakayori.music.playback.queues.ListQueue
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AutomixRequestOwnershipTest {
    private val storeField = Class.forName("com.sakayori.music.utils.DataStoreKt")
        .getDeclaredField("dataStoreInstance").apply { isAccessible = true }
    private val tube = ReflectionHelpers.getField<InnerTube>(YouTube, "innerTube")
    private var previousStore: Any? = null
    private lateinit var previousClient: HttpClient
    private lateinit var client: HttpClient
    private lateinit var service: MusicService
    private lateinit var nativePlayer: ExoPlayer
    private lateinit var scope: CoroutineScope
    private val routes = mutableMapOf<String, ArrayDeque<Response>>()
    private val releases = mutableListOf<Response>()
    private val mockFailure = AtomicReference<String?>()

    @Before
    fun setUp() {
        previousStore = storeField.get(null)
        storeField.set(null, object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw AssertionError("Automix fixture must not write preferences")
        })
        previousClient = ReflectionHelpers.getField(tube, "httpClient")
        client = HttpClient(MockEngine { request ->
            val body = (request.body as TextContent).text
            val data = Json.parseToJsonElement(body).jsonObject
            val route = data["playlistId"]?.jsonPrimitive?.content ?: data["videoId"]?.jsonPrimitive?.content.orEmpty()
            val response = synchronized(routes) { routes[route]?.removeFirstOrNull() }
            if (response == null) {
                mockFailure.compareAndSet(null, "Unexpected request route: $route")
                respond("{}", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "application/json"))
            } else {
                response.entered.countDown()
                if (response.held) response.release.await()
                respond(page(response.id), status = response.status, headers = headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }) {
            expectSuccess = true
            defaultRequest { url("https://example.test/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        }
        ReflectionHelpers.setField(tube, "httpClient", client)
        service = Robolectric.buildService(MusicService::class.java).get()
        scope = ReflectionHelpers.getField(service, "scope")
        nativePlayer = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Application>()).build()
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "AutomixRequestPlayer"
                "prepare", "setPlayWhenReady" -> null
                "getPlaybackState" -> Player.STATE_READY
                else -> try {
                    method.invoke(nativePlayer, *(args ?: emptyArray()))
                } catch (failure: InvocationTargetException) {
                    throw failure.targetException
                }
            }
        } as ExoPlayer
        ReflectionHelpers.setField(service, "player", player)
        service.playerVolume = MutableStateFlow(1f)
        ReflectionHelpers.getField<MutableStateFlow<Boolean>>(service, "playerInitialized").value = true
        ReflectionHelpers.setField(service, "startupPrefs", emptyPreferences())
        ReflectionHelpers.setField(service, "cachedPersistentQueue", false)
        nativePlayer.setMediaItems(listOf(item("incumbent")))
    }

    @After
    fun tearDown() {
        releases.forEach { it.release.complete(Unit) }
        scope.cancel()
        pumpUntil { scope.coroutineContext[Job]!!.isCompleted }
        ReflectionHelpers.setField(tube, "httpClient", previousClient)
        client.close()
        nativePlayer.release()
        storeField.set(null, previousStore)
    }

    @Test
    fun `two successful next calls publish the second result as parser control`() {
        enqueue("control", Response("first"), Response("second"))
        val job = start("control")
        pumpUntil { job.isCompleted }

        assertEquals(listOf("second"), ids())
        assertNull(mockFailure.get())
    }

    @Test
    fun `an older second response cannot overwrite a newer recommendation request`() {
        val held = Response("old-second", held = true)
        enqueue("old", Response("old-first"), held)
        val old = start("old")
        pumpUntil { held.entered.count == 0L }
        enqueue("new", Response("new-first"), Response("new-second"))
        val current = start("new")
        pumpUntil { current.isCompleted }
        assertEquals(listOf("new-second"), ids())
        held.release.complete(Unit)
        pumpUntil { old.isCompleted }

        assertEquals(listOf("new-second"), ids())
        assertNull(mockFailure.get())
    }

    @Test
    fun `clear invalidates a response that is still in flight`() {
        val held = Response("old-second", held = true)
        enqueue("old", Response("old-first"), held)
        val old = start("old")
        pumpUntil { held.entered.count == 0L }
        service.clearAutomix()
        held.release.complete(Unit)
        pumpUntil { old.isCompleted }

        assertTrue(service.automixItems.value.isEmpty())
        assertNull(mockFailure.get())
    }

    @Test
    fun `cancelled second request cannot publish the cached first-result fallback`() {
        val held = Response("old-second", held = true)
        enqueue("old", Response("old-first"), held)
        val old = start("old")
        pumpUntil { held.entered.count == 0L }
        old.cancel()
        pumpUntil { old.isCompleted }

        assertTrue(service.automixItems.value.isEmpty())
        assertNull(mockFailure.get())
    }

    @Test
    fun `new playlist admission prevents an old lookup acquiring its authority`() {
        val held = Response("old-second", held = true)
        enqueue("old", Response("old-first"), held)
        val old = start("old")
        pumpUntil { held.entered.count == 0L }
        service.playQueue(ListQueue("new-playlist", listOf(item("a"), item("b")), 0))
        val queueJob = ReflectionHelpers.getField<Job>(service, "queueLoadJob")
        pumpUntil { queueJob.isCompleted }
        held.release.complete(Unit)
        pumpUntil { old.isCompleted }

        assertTrue(service.automixItems.value.isEmpty())
        assertEquals("new-playlist", service.queueTitle)
        assertNull(mockFailure.get())
    }

    @Test
    fun `disabled recommendation admission uses its seeded snapshot without blocking preferences`() {
        ReflectionHelpers.setField(service, "startupPrefs", preferencesOf(SimilarContent to false))
        storeField.set(null, object : DataStore<Preferences> {
            override val data = flow<Preferences> { throw AssertionError("A seeded admission must not collect DataStore") }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw AssertionError("No preference writes")
        })

        val previous = scope.coroutineContext[Job]!!.children.toSet()
        service.getAutomix("disabled")
        val owned = scope.coroutineContext[Job]!!.children.toSet() - previous
        pumpUntil { owned.all { it.isCompleted } }

        assertTrue(service.automixItems.value.isEmpty())
        assertNull("Disabled recommendations must not issue HTTP requests", mockFailure.get())
    }

    @Test
    fun `accepted album playback can load recommendations without waiting for queue completion`() {
        val request = service.playQueue(ListQueue("album", listOf(item("a"), item("b")), 0))
        enqueue("album", Response("first"), Response("second"))
        service.getAutomix("album", request)
        val job = ReflectionHelpers.getField<Job>(service, "automixJob")
        pumpUntil { job.isCompleted && service.queueLoadState.value.committed }

        assertEquals(listOf("second"), ids())
        assertEquals(request, service.queueLoadState.value.generation)
        assertNull(mockFailure.get())
    }

    @Test
    fun `cold deferred playback retains its admitted recommendation generation`() {
        val ready = ReflectionHelpers.getField<MutableStateFlow<Boolean>>(service, "playerInitialized")
        ready.value = false
        val request = service.playQueue(ListQueue("cold", listOf(item("a"), item("b")), 0))
        enqueue("cold", Response("first"), Response("second"))
        service.getAutomix("cold", request)
        val job = ReflectionHelpers.getField<Job>(service, "automixJob")
        pumpUntil { job.isCompleted }
        ready.value = true
        pumpUntil { service.queueLoadState.value.committed }

        assertEquals(request, service.queueLoadState.value.generation)
        assertEquals(listOf("second"), ids())
        assertEquals("cold", service.queueTitle)
        assertNull(mockFailure.get())
    }

    @Test
    fun `an unaccepted token cannot issue a lookup or cancel a valid newer request`() {
        val request = service.playQueue(ListQueue("current", listOf(item("a"), item("b")), 0))
        val held = Response("valid", held = true)
        enqueue("current", Response("first"), held)
        service.getAutomix("current", request)
        val job = ReflectionHelpers.getField<Job>(service, "automixJob")
        pumpUntil { held.entered.count == 0L }
        service.getAutomix("unaccepted", null)
        service.getAutomix("stale", request!! - 1)
        held.release.complete(Unit)
        pumpUntil { job.isCompleted }

        assertEquals(listOf("valid"), ids())
        assertNull(mockFailure.get())
    }

    @Test
    fun `queue admission retains an already accepted saved recommendation batch`() {
        val saved = listOf(item("saved"))
        service.automixItems.value = saved
        service.playQueue(ListQueue("restored", listOf(item("a"), item("b")), 0), playWhenReady = false, restoringQueue = true)
        pumpUntil { service.queueLoadState.value.committed }

        assertEquals(saved, service.automixItems.value)
    }

    @Test
    fun `ordinary second HTTP failure retains the valid first-result fallback`() {
        enqueue("fallback", Response("first"), Response("unused", status = HttpStatusCode.InternalServerError))
        val job = start("fallback")
        pumpUntil { job.isCompleted }

        assertEquals(listOf("first"), ids())
        assertNull(mockFailure.get())
    }

    @Test
    fun `repeat all suppression is preserved from the observed preference snapshot`() {
        nativePlayer.repeatMode = Player.REPEAT_MODE_ALL
        ReflectionHelpers.setField(service, "startupPrefs", preferencesOf(DisableLoadMoreWhenRepeatAllKey to true))
        val previous = scope.coroutineContext[Job]!!.children.toSet()

        service.getAutomix("suppressed")

        assertTrue(service.automixItems.value.isEmpty())
        assertEquals(previous, scope.coroutineContext[Job]!!.children.toSet())
        assertNull(mockFailure.get())
    }

    private fun start(playlist: String): Job {
        val previous = scope.coroutineContext[Job]!!.children.toSet()
        service.getAutomix(playlist)
        return (scope.coroutineContext[Job]!!.children.toSet() - previous).single()
    }

    private fun enqueue(route: String, vararg responses: Response) {
        synchronized(routes) { routes[route] = ArrayDeque(responses.toList()) }
        releases += responses
    }

    private fun pumpUntil(done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!done() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        assertTrue("Service/mock continuation completed", done())
    }

    private fun ids() = service.automixItems.value.map { it.mediaId }
    private fun item(id: String) = SongItem(id, id, emptyList(), thumbnail = "").toMediaItem()
    private data class Response(val id: String, val held: Boolean = false, val status: HttpStatusCode = HttpStatusCode.OK) {
        val entered = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
    }

    private fun page(id: String) = """
        {"contents":{},"continuationContents":{"playlistPanelContinuation":{"contents":[
          {"playlistPanelVideoRenderer":{"videoId":"$id","selected":false,
          "title":{"runs":[{"text":"$id"}]},"longBylineText":{"runs":[{"text":"Artist"}]},
          "lengthText":{"runs":[{"text":"1:00"}]},"thumbnail":{"thumbnails":[{"url":"https://example.test/art"}]},
          "navigationEndpoint":{"watchEndpoint":{"videoId":"$id"}}}}
        ]}}}
    """.trimIndent()
}
