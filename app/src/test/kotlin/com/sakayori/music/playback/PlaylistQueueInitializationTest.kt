/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sakayori.innertube.models.SongItem
import com.sakayori.innertube.InnerTube
import com.sakayori.innertube.YouTube
import com.sakayori.innertube.models.WatchEndpoint
import com.sakayori.music.extensions.metadata
import com.sakayori.music.extensions.toMediaItem
import com.sakayori.music.models.MediaMetadata
import com.sakayori.music.db.InternalDatabase
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.playback.flac.ArchiveFlacSource
import com.sakayori.music.playback.flac.LosslessApiHttp
import com.sakayori.music.playback.flac.LosslessSourceRegistry
import com.sakayori.music.playback.queues.Queue
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
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
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaylistQueueInitializationTest {
    private val storeField = Class.forName("com.sakayori.music.utils.DataStoreKt")
        .getDeclaredField("dataStoreInstance").apply { isAccessible = true }
    private var previousStore: Any? = null
    private lateinit var service: MusicService
    private lateinit var nativePlayer: ExoPlayer
    private lateinit var player: ExoPlayer
    private val gates = mutableListOf<HeldQueue>()
    private val storeScopes = mutableListOf<CoroutineScope>()
    private val callbackListeners = mutableListOf<Player.Listener>()
    private val callbackProviders = mutableListOf<StandaloneDatabaseProvider>()
    private val callbackDatabases = mutableListOf<MusicDatabase>()
    @get:Rule val files = TemporaryFolder()
    private var prepared = true
    private var playWhenReady = false
    private var preparationCount = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        previousStore = storeField.get(null)
        storeField.set(null, ReadOnlyStore(flowOf(emptyPreferences())))
        // No onCreate: the real queue methods are exercised without Hilt,
        // network, decoders, sessions, caches or production listener work.
        service = Robolectric.buildService(MusicService::class.java).get()
        nativePlayer = ExoPlayer.Builder(context).build()
        player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PlaylistInitializationPlayer"
                "prepare" -> { prepared = true; preparationCount++; null }
                "getPlaybackState" -> if (prepared) Player.STATE_READY else Player.STATE_IDLE
                "setPlayWhenReady" -> { playWhenReady = args!![0] as Boolean; null }
                "getPlayWhenReady" -> playWhenReady
                "isPlaying" -> playWhenReady && prepared
                else -> try {
                    // Real Media3 timeline/index/Window identity; sources are
                    // never prepared, so this cannot perform network playback.
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
        callbackListeners.forEach { nativePlayer.removeListener(it) }
        gates.forEach { it.release.complete(Unit) }
        ReflectionHelpers.getField<CoroutineScope>(service, "scope").cancel()
        shadowOf(Looper.getMainLooper()).idle()
        nativePlayer.release()
        storeScopes.forEach { it.cancel() }
        callbackProviders.forEach { it.close() }
        callbackDatabases.forEach { it.close() }
        storeField.set(null, previousStore)
    }

    @Test
    fun `cleared recommendation cannot crash or append through a stale add callback`() {
        val selected = item("recommendation")
        service.automixItems.value = emptyList()

        service.addToQueueAutomix(selected, 0)

        assertEquals(listOf("incumbent"), ids())
        assertTrue(service.automixItems.value.isEmpty())
    }

    @Test
    fun `cleared recommendation cannot crash through a stale play next callback`() {
        service.playNextAutomix(item("recommendation"), 0)

        assertEquals(listOf("incumbent"), ids())
    }

    @Test
    fun `a valid position in a replacement pool cannot remove or append the old add item`() {
        val replacement = item("replacement")
        val remainder = item("remainder")
        service.automixItems.value = listOf(replacement, remainder)

        service.addToQueueAutomix(item("old"), 0)

        assertEquals(listOf(replacement, remainder), service.automixItems.value)
        assertEquals(listOf("incumbent"), ids())
    }

    @Test
    fun `a valid position in a replacement pool cannot remove the old play next item`() {
        val replacement = item("replacement")
        service.automixItems.value = listOf(replacement)

        service.playNextAutomix(item("old"), 0)

        assertEquals(listOf(replacement), service.automixItems.value)
        assertEquals(listOf("incumbent"), ids())
    }

    @Test
    fun `repeating an old callback cannot consume the next recommendation`() {
        val selected = item("selected")
        val remaining = item("remaining")
        service.automixItems.value = listOf(selected, remaining)

        service.addToQueueAutomix(selected, 0)
        service.addToQueueAutomix(selected, 0)

        assertEquals(listOf("incumbent", "selected"), ids())
        assertEquals(listOf(remaining), service.automixItems.value)
    }

    @Test
    fun `held playlist initialization completes without automatic promotion as control`() {
        val queue = startHeldQueue()
        finish(queue)

        assertEquals(listOf("before", "selected", "after"), ids())
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals("playlist", service.queueTitle)
        assertEquals(1, preparationCount)
    }

    @Test
    fun `automatic recommendation cannot displace the pending playlist remainder`() {
        val queue = startHeldQueue()
        val pool = listOf(item("recommendation"))
        service.automixItems.value = pool

        service.promoteAutomixIfNeeded(false, pool)
        finish(queue)

        assertEquals(listOf("before", "selected", "after"), ids())
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals("playlist", service.queueTitle)
        assertEquals(pool, service.automixItems.value)
        assertTrue(playWhenReady)
        assertEquals(1, preparationCount)
    }

    @Test
    fun `automatic append must recheck live next even if its UI capability is stale`() {
        val queue = startHeldQueue()
        finish(queue)
        val pool = listOf(item("recommendation"))
        service.automixItems.value = pool

        service.promoteAutomixIfNeeded(false, pool)

        assertEquals(listOf("before", "selected", "after"), ids())
        assertEquals(pool, service.automixItems.value)
    }

    @Test
    fun `replaced recommendation snapshot cannot authorize an automatic append`() {
        val queue = startHeldQueue(startIndex = 2)
        finish(queue)
        val oldPool = listOf(item("old"))
        val currentPool = listOf(item("current"))
        service.automixItems.value = currentPool

        service.promoteAutomixIfNeeded(false, oldPool)

        assertEquals(listOf("before", "selected", "after"), ids())
        assertEquals(currentPool, service.automixItems.value)
    }

    @Test
    fun `new request defeats the old held playlist result`() {
        val first = startHeldQueue()
        val second = startHeldQueue(listOf(item("new-before"), item("new-selected"), item("new-after")))
        finish(second)
        first.release.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("new-before", "new-selected", "new-after"), ids())
        assertEquals("new-selected", player.currentMediaItem?.mediaId)
    }

    @Test
    fun `manual play next remains immediate while the remainder is pending`() {
        val queue = startHeldQueue()
        val chosen = item("manual")
        service.automixItems.value = listOf(chosen)

        service.playNextAutomix(chosen, 0)
        assertEquals(listOf("selected", "manual"), ids())
        finish(queue, allowCancellation = true)

        assertEquals(listOf("selected", "manual"), ids())
    }

    @Test
    fun `a complete local list retains all 75 and 106 occurrences`() {
        for (size in listOf(75, 106)) {
            val items = (0 until size).map { item("song-$it") }
            val queue = startHeldQueue(items, startIndex = 22)
            finish(queue)
            assertEquals(items.map { it.mediaId }, ids())
            assertEquals(22, player.currentMediaItemIndex)
        }
    }

    @Test
    fun `a committed final item still permits one legitimate automatic recommendation`() {
        val queue = startHeldQueue(startIndex = 2)
        finish(queue)
        val first = item("recommendation")
        val second = item("remaining")
        val pool = listOf(first, second)
        service.automixItems.value = pool

        service.promoteAutomixIfNeeded(false, pool)

        assertEquals(listOf("before", "selected", "after", "recommendation"), ids())
        assertEquals(listOf(second), service.automixItems.value)
        assertEquals(2, player.currentMediaItemIndex)
    }

    @Test
    fun `a cancelled initial request cannot grant automatic promotion authority`() {
        val queue = startHeldQueue()
        queue.job.cancel()
        pumpUntil { queue.job.isCompleted }
        val pool = listOf(item("recommendation"))
        service.automixItems.value = pool

        service.promoteAutomixIfNeeded(false, pool)

        assertEquals(listOf("selected"), ids())
        assertEquals(pool, service.automixItems.value)
    }

    @Test
    fun `same recording replaced with a new native window defeats an old initial load`() {
        val queue = startHeldQueue()
        nativePlayer.setMediaItem(item("selected"))
        finish(queue)

        assertEquals(listOf("selected"), ids())
        assertNull(service.queueTitle)
    }

    @Test
    fun `valid preload completion preserves a newer seek and paused state`() {
        val queue = startHeldQueue()
        nativePlayer.seekTo(1234)
        playWhenReady = false
        finish(queue)

        assertEquals(listOf("before", "selected", "after"), ids())
        assertEquals(1234L, player.currentPosition)
        assertFalse(playWhenReady)
        assertEquals(1, preparationCount)
    }

    @Test
    fun `a callback from a cancelled service scope cannot consume recommendations`() {
        val recommendation = item("recommendation")
        val pool = listOf(recommendation)
        service.automixItems.value = pool
        ReflectionHelpers.getField<CoroutineScope>(service, "scope").cancel()

        service.addToQueueAutomix(recommendation, 0)

        assertEquals(pool, service.automixItems.value)
        assertEquals(listOf("incumbent"), ids())
    }

    @Test
    fun `a full initial commit submits the complete persisted native queue`() = runBlocking {
        val directory = files.newFolder("queue")
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
            override fun getFilesDir() = directory
        }
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        storeScopes += storeScope
        val store = PlaybackQueueStore(context, storeScope)
        ReflectionHelpers.setField(service, "queueStore", store)
        ReflectionHelpers.setField(service, "cachedPersistentQueue", true)
        service.playQueue(ListQueue("persisted", listOf(item("a"), item("b"), item("c")), 1))
        val job = ReflectionHelpers.getField<Job>(service, "queueLoadJob")
        pumpUntil { job.isCompleted }
        store.awaitPendingWrites()

        val snapshot = store.load()
        assertEquals(listOf("a", "b", "c"), snapshot.queue?.items?.map { it.id })
        assertEquals(1, snapshot.queue?.mediaItemIndex)
        assertEquals("persisted", snapshot.queue?.title)
    }

    @Test
    fun `pending initial snapshot cannot replace last full recovery`() = runBlocking {
        val directory = files.newFolder("pending-final")
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
            override fun getFilesDir() = directory
        }
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        storeScopes += storeScope
        val store = PlaybackQueueStore(context, storeScope)
        ReflectionHelpers.setField(service, "queueStore", store)
        ReflectionHelpers.setField(service, "cachedPersistentQueue", true)

        service.playQueue(
            ListQueue("known-good", listOf(item("a"), item("b"), item("c")), 1, 10_000L),
            playWhenReady = false,
        )
        val initial = ReflectionHelpers.getField<Job>(service, "queueLoadJob")
        val failure = AtomicReference<Throwable?>()
        initial.invokeOnCompletion { failure.set(it) }
        pumpUntil { initial.isCompleted }
        assertNull("Initial fixture commit must succeed", failure.get())
        assertTrue(service.queueLoadState.value.committed)
        withTimeout(5_000) { store.awaitPendingWrites() }
        val lastValid = store.load()
        assertEquals(listOf("a", "b", "c"), lastValid.queue?.items?.map { it.id })
        assertEquals(1, lastValid.queue?.mediaItemIndex)
        assertEquals(10_000L, lastValid.queue?.position)
        assertEquals("known-good", lastValid.queue?.title)
        val recoveryBytes = listOf(
            PlaybackQueueStore.QUEUE_FILE,
            PlaybackQueueStore.AUTOMIX_FILE,
            PlaybackQueueStore.PLAYER_STATE_FILE,
        ).associateWith { directory.resolve(it).readBytes() }

        val pending = startHeldQueue()
        assertFalse(service.queueLoadState.value.committed)
        assertFalse(pending.job.isCompleted)
        assertEquals(listOf("selected"), ids())

        // Repeat/shuffle and initialized-service shutdown use this actual saver.
        ReflectionHelpers.callInstanceMethod<Any?>(service, "saveQueueToDisk")
        withTimeout(5_000) { store.awaitPendingWrites() }
        assertEquals(
            "Uncommitted preload must not replace the last valid recovery queue",
            listOf("a", "b", "c"),
            store.load().queue?.items?.map { it.id },
        )
        recoveryBytes.forEach { (name, bytes) ->
            assertArrayEquals("Pending save must preserve $name", bytes, directory.resolve(name).readBytes())
        }
    }

    @Test
    fun `paused non-current removal checkpoints the edited native queue`() = runBlocking {
        val directory = files.newFolder("paused-edit")
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
            override fun getFilesDir() = directory
        }
        initializeCallbackDependencies(context)
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        storeScopes += storeScope
        val store = PlaybackQueueStore(context, storeScope)
        ReflectionHelpers.setField(service, "queueStore", store)
        ReflectionHelpers.setField(service, "cachedPersistentQueue", true)
        service.playQueue(
            ListQueue("paused-edit", listOf(item("a"), item("b"), item("c")), 1, 10_000L),
            playWhenReady = false,
        )
        val initial = ReflectionHelpers.getField<Job>(service, "queueLoadJob")
        val failure = AtomicReference<Throwable?>()
        initial.invokeOnCompletion { failure.set(it) }
        pumpUntil { initial.isCompleted }
        assertNull("Seed commit must succeed", failure.get())
        withTimeout(5_000) { store.awaitPendingWrites() }
        assertEquals(listOf("a", "b", "c"), store.load().queue?.items?.map { it.id })

        var editEventsHandled = false
        val bridge = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                service.onTimelineChanged(timeline, reason)
            }

            override fun onEvents(native: Player, events: Player.Events) {
                // Keep the real native event batch while using this fixture's
                // no-decoder READY/paused player view, as queue methods do.
                service.onEvents(player, events)
                if (events.contains(Player.EVENT_TIMELINE_CHANGED)) editEventsHandled = true
            }
        }
        callbackListeners += bridge
        nativePlayer.addListener(bridge)
        nativePlayer.removeMediaItem(2)
        pumpUntil { editEventsHandled }
        assertEquals(listOf("a", "b"), ids())
        assertFalse(player.isPlaying)
        assertEquals("b", player.currentMediaItem?.mediaId)
        assertEquals(10_000L, player.currentPosition)
        withTimeout(5_000) { store.awaitPendingWrites() }

        val saved = store.load().queue
        assertEquals("Paused edit must checkpoint the native order", listOf("a", "b"), saved?.items?.map { it.id })
        assertEquals(1, saved?.mediaItemIndex)
        assertEquals(10_000L, saved?.position)
    }

    private fun initializeCallbackDependencies(context: Context) {
        val database = MusicDatabase(Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).build())
        val provider = StandaloneDatabaseProvider(context)
        callbackDatabases += database
        callbackProviders += provider
        val unusedCache = Proxy.newProxyInstance(Cache::class.java.classLoader, arrayOf(Cache::class.java)) { _, method, _ ->
            throw AssertionError("Paused callback fixture must not access cache: ${method.name}")
        } as Cache
        val cancelledScope = CoroutineScope(SupervisorJob().apply { cancel() } + Dispatchers.IO)
        val http = PlaybackHttpClient()
        val sources = LosslessSourceRegistry(context, cancelledScope, LosslessApiHttp(http), ArchiveFlacSource(http))
        service.losslessResolver = LosslessResolver(
            database,
            AudioRenditionStore(database, provider, unusedCache, unusedCache),
            sources,
            cancelledScope,
        )
    }

    @Test
    fun `committed radio stays recovery eligible`() = radioCase(pending = false, stale = false)

    @Test
    fun `pending playlist can adopt completed eligible radio`() = radioCase(pending = true, stale = false)

    @Test
    fun `old radio cannot claim newer same ID pending queue`() = radioCase(pending = true, stale = true)

    @Test
    fun `related radio fallback preserves the committed recipe`() = radioCase(pending = false, stale = false, relatedFallback = true)

    @Test
    fun `pending playlist can adopt completed related radio fallback`() = radioCase(pending = true, stale = false, relatedFallback = true)

    @Test
    fun `old related radio fallback cannot claim newer same ID pending queue`() = radioCase(pending = true, stale = true, relatedFallback = true)

    private fun radioCase(pending: Boolean, stale: Boolean, relatedFallback: Boolean = false) = runBlocking {
        val directory = files.newFolder("radio-fixture")
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
            override fun getFilesDir() = directory
        }
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        storeScopes += storeScope
        val store = PlaybackQueueStore(context, storeScope)
        ReflectionHelpers.setField(service, "queueStore", store)
        ReflectionHelpers.setField(service, "cachedPersistentQueue", true)
        commitForRadio(ListQueue("known-good", listOf(item("before"), item("selected"), item("after")), 1, 10_000L))
        withTimeout(5_000) { store.awaitPendingWrites() }
        val recoveryBytes = listOf(
            PlaybackQueueStore.QUEUE_FILE,
            PlaybackQueueStore.AUTOMIX_FILE,
            PlaybackQueueStore.PLAYER_STATE_FILE,
        ).associateWith { directory.resolve(it).readBytes() }
        val initial = if (pending) startHeldQueue() else null
        val recipeBeforeRadio = ReflectionHelpers.getField<Queue>(service, "currentQueue")
        val scope = ReflectionHelpers.getField<CoroutineScope>(service, "scope")
        val tube = ReflectionHelpers.getField<InnerTube>(YouTube, "innerTube")
        val originalClient = ReflectionHelpers.getField<HttpClient>(tube, "httpClient")
        val entered = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        val requests = AtomicInteger()
        val initialFailures = AtomicInteger()
        val fallbackNextCalls = AtomicInteger()
        val relatedCalls = AtomicInteger()
        val client = HttpClient(MockEngine { request ->
            val body = Json.parseToJsonElement((request.body as TextContent).text).jsonObject
            val headers = headersOf(HttpHeaders.ContentType, "application/json")
            if (relatedFallback) {
                requests.incrementAndGet()
                when {
                    request.url.encodedPath.endsWith("/next") && body["playlistId"]?.jsonPrimitive?.content == "RDAMVMselected" -> {
                        initialFailures.incrementAndGet()
                        respond("{}", status = HttpStatusCode.BadRequest, headers = headers)
                    }
                    request.url.encodedPath.endsWith("/next") && body["videoId"]?.jsonPrimitive?.content == "selected" -> {
                        fallbackNextCalls.incrementAndGet()
                        respond(fallbackNextForFixture(), headers = headers)
                    }
                    request.url.encodedPath.endsWith("/browse") && body["browseId"]?.jsonPrimitive?.content == "related-fixture" -> {
                        relatedCalls.incrementAndGet()
                        entered.countDown()
                        release.await()
                        respond(relatedSongForFixture(), headers = headers)
                    }
                    else -> throw AssertionError("Unexpected related-fallback HTTP route")
                }
            } else {
                if (requests.incrementAndGet() != 1 || !request.url.encodedPath.endsWith("/next") ||
                    body["playlistId"]?.jsonPrimitive?.content != "RDAMVMselected"
                ) throw AssertionError("Unexpected HTTP request in successful-radio fixture")
                entered.countDown()
                release.await()
                respond(radioPageForFixture(), headers = headers)
            }
        }) {
            expectSuccess = true
            defaultRequest { url("https://example.test/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        }
        ReflectionHelpers.setField(tube, "httpClient", client)
        try {
            val before = scope.coroutineContext[Job]!!.children.toSet()
            service.startRadioSeamlessly()
            val radio = (scope.coroutineContext[Job]!!.children.toSet() - before).single()
            val error = AtomicReference<Throwable?>()
            radio.invokeOnCompletion { error.set(it) }
            pumpUntil { entered.count == 0L || radio.isCompleted }
            assertEquals("Actual radio HTTP gate must be reached", 0L, entered.count)
            val oldUid = player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window()).uid
            val oldGeneration = service.queueLoadState.value.generation
            val newer = if (stale) startHeldQueue(listOf(item("new-before"), item("selected"), item("new-after"))) else null
            val newerState = service.queueLoadState.value
            if (stale) {
                assertFalse(newerState.committed)
                assertTrue(newerState.generation != oldGeneration)
                assertTrue(oldUid != player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window()).uid)
            }
            release.complete(Unit)
            pumpUntil { radio.isCompleted }
            val failure = error.get()
            if (!stale || failure !is CancellationException) {
                assertNull("HTTP/parser/service failure is not a preservation red", failure)
            }
            if (relatedFallback) {
                assertEquals(3, initialFailures.get())
                assertEquals(1, fallbackNextCalls.get())
                assertEquals(1, relatedCalls.get())
                assertEquals(5, requests.get())
            } else {
                assertEquals(1, requests.get())
            }
            if (stale) {
                assertEquals(newerState, service.queueLoadState.value)
                assertFalse(service.queueLoadState.value.committed)
                assertEquals(listOf("selected"), ids())
                assertTrue(ReflectionHelpers.getField<Queue>(service, "currentQueue") === newer)
                ReflectionHelpers.callInstanceMethod<Any?>(service, "saveQueueToDisk")
                withTimeout(5_000) { store.awaitPendingWrites() }
                recoveryBytes.forEach { (name, bytes) ->
                    assertArrayEquals(name, bytes, directory.resolve(name).readBytes())
                }
                finish(newer!!)
                assertEquals(listOf("new-before", "selected", "new-after"), ids())
                assertTrue(service.queueLoadState.value.committed)
                assertEquals(newerState.generation, service.queueLoadState.value.generation)
                withTimeout(5_000) { store.awaitPendingWrites() }
                assertEquals(listOf("new-before", "selected", "new-after"), store.load().queue?.items?.map { it.id })
            } else {
                val expected = if (pending) listOf("selected", "radio") else listOf("before", "selected", "radio")
                assertEquals(expected, ids())
                if (relatedFallback && !pending) {
                    assertTrue(ReflectionHelpers.getField<Queue>(service, "currentQueue") === recipeBeforeRadio)
                    assertEquals(oldGeneration, service.queueLoadState.value.generation)
                }
                ReflectionHelpers.callInstanceMethod<Any?>(service, "saveQueueToDisk")
                withTimeout(5_000) { store.awaitPendingWrites() }
                assertEquals("Completed radio must be recoverable", expected, store.load().queue?.items?.map { it.id })
                assertTrue(service.queueLoadState.value.committed)
                initial?.let { finish(it, allowCancellation = true) }
                assertEquals(expected, ids())
            }
        } finally {
            release.complete(Unit)
            scope.cancel()
            pumpUntil { scope.coroutineContext[Job]!!.isCompleted }
            ReflectionHelpers.setField(tube, "httpClient", originalClient)
            client.close()
        }
    }

    private fun commitForRadio(queue: ListQueue) {
        service.playQueue(queue, playWhenReady = false)
        val job = ReflectionHelpers.getField<Job>(service, "queueLoadJob")
        val error = AtomicReference<Throwable?>()
        job.invokeOnCompletion { error.set(it) }
        pumpUntil { job.isCompleted }
        assertNull("Real seed commit must succeed", error.get())
        assertTrue(service.queueLoadState.value.committed)
    }

    private fun radioPageForFixture(): String {
        val rows = listOf("selected", "radio").joinToString(",") { id ->
            """{"playlistPanelVideoRenderer":{"videoId":"$id","selected":${id == "selected"},
            "title":{"runs":[{"text":"$id"}]},"longBylineText":{"runs":[{"text":"Artist"}]},
            "lengthText":{"runs":[{"text":"1:00"}]},"thumbnail":{"thumbnails":[{"url":"https://example.test/art"}]},
            "navigationEndpoint":{"watchEndpoint":{"videoId":"$id"}}}}"""
        }
        return """{"contents":{},"continuationContents":{"playlistPanelContinuation":{"contents":[$rows]}}}"""
    }

    @Test
    fun `related radio payloads pass the actual parser canary`() = runBlocking {
        val tube = ReflectionHelpers.getField<InnerTube>(YouTube, "innerTube")
        val originalClient = ReflectionHelpers.getField<HttpClient>(tube, "httpClient")
        val calls = AtomicInteger()
        val client = HttpClient(MockEngine { request ->
            calls.incrementAndGet()
            val body = when {
                request.url.encodedPath.endsWith("/next") -> fallbackNextForFixture()
                request.url.encodedPath.endsWith("/browse") -> relatedSongForFixture()
                else -> throw AssertionError("Unexpected related parser route")
            }
            respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            expectSuccess = true
            defaultRequest { url("https://example.test/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        }
        ReflectionHelpers.setField(tube, "httpClient", client)
        try {
            val next = YouTube.next(WatchEndpoint(videoId = "selected")).getOrThrow()
            val related = checkNotNull(next.relatedEndpoint)
            assertEquals("related-fixture", related.browseId)
            assertEquals(listOf("radio"), YouTube.related(related).getOrThrow().songs.map { it.id })
            assertEquals(2, calls.get())
        } finally {
            ReflectionHelpers.setField(tube, "httpClient", originalClient)
            client.close()
        }
    }

    private fun fallbackNextForFixture() = """
        {"contents":{"singleColumnMusicWatchNextResultsRenderer":{"tabbedRenderer":{
        "watchNextTabbedResultsRenderer":{"tabs":[
        {"tabRenderer":{}},{"tabRenderer":{}},
        {"tabRenderer":{"endpoint":{"browseEndpoint":{"browseId":"related-fixture"}}}}
        ]}}}}}
    """.trimIndent()

    private fun relatedSongForFixture() = """
        {"responseContext":{},"contents":{"sectionListRenderer":{"contents":[
        {"musicCarouselShelfRenderer":{"itemSize":"COLLECTION_STYLE_ITEM_SIZE_SMALL","contents":[
        {"musicResponsiveListItemRenderer":{
        "playlistItemData":{"videoId":"radio"},
        "flexColumns":[
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"radio"}]}}},
        {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist"}]}}}
        ],
        "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"1:00"}]}}}],
        "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.test/art"}]}}},
        "overlay":{"musicItemThumbnailOverlayRenderer":{"content":{"musicPlayButtonRenderer":{
        "playNavigationEndpoint":{"watchEndpoint":{"videoId":"radio","watchEndpointMusicSupportedConfigs":{
        "watchEndpointMusicConfig":{"musicVideoType":"MUSIC_VIDEO_TYPE_ATV"}}}}
        }}}}
        }}
        ]}}
        ]}}}
    """.trimIndent()

    @Test
    fun `a newer listener admitted queue wins over older add bookkeeping`() {
        assertReentrantQueueWins { service.addToQueue(listOf(item("old-manual"))) }
    }

    @Test
    fun `a newer listener admitted queue wins over older play next bookkeeping`() {
        assertReentrantQueueWins { service.playNext(listOf(item("old-manual"))) }
    }

    @Test
    fun `older recommendation consumption cannot remove a carried pool after a newer admission`() {
        val recommendation = item("old-manual")
        val pool = listOf(recommendation)
        service.automixItems.value = pool
        assertReentrantQueueWins { service.addToQueueAutomix(recommendation, 0) }

        assertEquals(pool, service.automixItems.value)
    }

    private fun assertReentrantQueueWins(command: () -> Unit) {
        var admitted = false
        nativePlayer.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (!admitted && nativePlayer.mediaItemCount == 2) {
                    admitted = true
                    service.playQueue(ListQueue("newer", listOf(item("new-a"), item("new-b")), 0))
                }
            }
        })
        command()
        val latest = ReflectionHelpers.getField<Job>(service, "queueLoadJob")
        pumpUntil { latest.isCompleted }

        assertTrue("The actual native callback must admit the newer request", admitted)
        assertEquals(listOf("new-a", "new-b"), ids())
        assertEquals("newer", service.queueTitle)
    }

    @Test
    fun `switching rows inside an already committed list queue seeks without rebuilding the timeline or re-preparing`() {
        val items = listOf(item("a"), item("b"), item("c"))
        service.playQueue(ListQueue("playlist", items, startIndex = 0))
        val firstJob = ReflectionHelpers.getField<Job>(service, "queueLoadJob")
        pumpUntil { firstJob.isCompleted }
        assertEquals(listOf("a", "b", "c"), ids())
        assertEquals(0, player.currentMediaItemIndex)
        val preparationsAfterInitialCommit = preparationCount

        service.playQueue(ListQueue("playlist", items, startIndex = 2))

        assertEquals(listOf("a", "b", "c"), ids())
        assertEquals(2, player.currentMediaItemIndex)
        assertEquals("playlist", service.queueTitle)
        assertEquals(preparationsAfterInitialCommit, preparationCount)
    }

    private fun startHeldQueue(
        items: List<MediaItem> = listOf(item("before"), item("selected"), item("after")),
        startIndex: Int = 1,
    ): HeldQueue {
        val queue = HeldQueue(items, startIndex)
        gates += queue
        service.playQueue(queue)
        queue.job = ReflectionHelpers.getField(service, "queueLoadJob")
        queue.job.invokeOnCompletion { queue.failure.set(it) }
        pumpUntil { queue.entered.count == 0L }
        assertEquals(listOf(items[startIndex].mediaId), ids())
        assertFalse(queue.job.isCompleted)
        return queue
    }

    private fun finish(queue: HeldQueue, allowCancellation: Boolean = false) {
        queue.release.complete(Unit)
        pumpUntil { queue.job.isCompleted }
        val failure = queue.failure.get()
        if (!allowCancellation || failure !is CancellationException) {
            assertNull("Fixture must not mistake asynchronous exceptions for queue truncation", failure)
        }
    }

    private fun pumpUntil(done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!done() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        assertTrue("Main continuation completed", done())
    }

    private fun ids() = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
    private fun item(id: String) = SongItem(id, id, emptyList(), thumbnail = "").toMediaItem()

    private class HeldQueue(private val items: List<MediaItem>, private val startIndex: Int) : Queue {
        val entered = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        val failure = AtomicReference<Throwable?>()
        lateinit var job: Job
        override val preloadItem: MediaMetadata? = items[startIndex].metadata
        override suspend fun getInitialStatus(): Queue.Status {
            entered.countDown()
            release.await()
            return Queue.Status("playlist", items, startIndex, 0)
        }
        override fun hasNextPage() = false
        override suspend fun nextPage(): List<MediaItem> = throw AssertionError("No network/paging in fixture")
    }

    private class ReadOnlyStore(override val data: Flow<Preferences>) : DataStore<Preferences> {
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw AssertionError("Queue command fixture must not write preferences")
    }
}
