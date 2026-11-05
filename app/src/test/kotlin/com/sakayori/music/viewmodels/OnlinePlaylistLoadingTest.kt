/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.viewmodels

import android.app.Application
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sakayori.innertube.InnerTube
import com.sakayori.innertube.YouTube
import com.sakayori.innertube.utils.completed
import com.sakayori.music.db.InternalDatabase
import com.sakayori.music.db.MusicDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class OnlinePlaylistLoadingTest {
    private val storeField = Class.forName("com.sakayori.music.utils.DataStoreKt")
        .getDeclaredField("dataStoreInstance").apply { isAccessible = true }
    private val tube = ReflectionHelpers.getField<InnerTube>(YouTube, "innerTube")
    private lateinit var originalClient: HttpClient
    private lateinit var client: HttpClient
    private lateinit var database: MusicDatabase
    private var originalStore: Any? = null
    private var viewModel: OnlinePlaylistViewModel? = null
    private val requests = AtomicInteger()
    private var repeatCursor = false
    private var holdProactive = false
    private val proactiveStarted = CompletableDeferred<Unit>()
    private val releaseProactive = CompletableDeferred<Unit>()

    @Before
    fun setUp() {
        originalStore = storeField.get(null)
        storeField.set(null, object : DataStore<Preferences> {
            override val data = flowOf(emptyPreferences())
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences) =
                throw AssertionError("Loading must not write settings")
        })
        database = MusicDatabase(Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InternalDatabase::class.java).build())
        originalClient = ReflectionHelpers.getField(tube, "httpClient")
        client = HttpClient(MockEngine {
            val index = requests.getAndIncrement()
            if (repeatCursor && index >= 2) awaitCancellation()
            if (holdProactive && index == 1) {
                proactiveStarted.complete(Unit)
                releaseProactive.await()
            }
            val response = if (index == 0) initialPage else continuationPage
            respond(response, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            defaultRequest { url("https://example.test/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        }
        ReflectionHelpers.setField(tube, "httpClient", client)
    }

    @After
    fun tearDown() {
        viewModel?.let { runBlocking { it.viewModelScope.coroutineContext[Job]!!.cancelAndJoin() } }
        ReflectionHelpers.setField(tube, "httpClient", originalClient)
        client.close()
        storeField.set(null, originalStore)
        database.close()
    }

    @Test
    fun `actual initial and continuation loading preserve recording occurrences`() = runBlocking {
        val model = OnlinePlaylistViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(mapOf("playlistId" to "playlist")), database)
        viewModel = model
        withTimeout(5_000) {
            while (model.isLoading.value || requests.get() < 2 ||
                ReflectionHelpers.getField<Job?>(model, "proactiveLoadJob")?.isCompleted != true) delay(10)
        }

        assertEquals(listOf("a", "b", "a", "a", "a"), model.playlistSongs.value.map { it.id })
        assertEquals(listOf("first", "b", "second", null, null), model.playlistSongs.value.map { it.setVideoId })
        assertEquals(2, requests.get())
    }

    @Test
    fun `a repeated continuation cursor cannot replay anonymous occurrences indefinitely`() = runBlocking {
        repeatCursor = true
        val model = OnlinePlaylistViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(mapOf("playlistId" to "playlist")), database)
        viewModel = model
        withTimeout(5_000) {
            while (requests.get() < 3 &&
                (model.isLoading.value || ReflectionHelpers.getField<Job?>(model, "proactiveLoadJob")?.isCompleted != true)) delay(10)
        }
        assertEquals(2, requests.get())
    }

    @Test
    fun `manual load replacing an in flight background page appends each occurrence once`() = runBlocking {
        holdProactive = true
        val model = OnlinePlaylistViewModel(ApplicationProvider.getApplicationContext(), SavedStateHandle(mapOf("playlistId" to "playlist")), database)
        viewModel = model
        withTimeout(5_000) { proactiveStarted.await() }
        model.loadMoreSongs()
        withTimeout(5_000) {
            while (model.isLoadingMore.value || model.playlistSongs.value.size != 5) delay(10)
        }
        releaseProactive.complete(Unit)
        model.loadMoreSongs() // The consumed final cursor must not be requested again.

        assertEquals(listOf("first", "b", "second", null, null), model.playlistSongs.value.map { it.setVideoId })
        assertEquals(3, requests.get())
        assertFalse(model.isLoadingMore.value)
    }

    @Test
    fun `anonymous rows remain useful without becoming destructive sync authority`() = runBlocking {
        val initial = YouTube.playlist("playlist").getOrThrow()
        assertTrue(initial.isSnapshotUsableForSync)
        val page = YouTube.playlistContinuation("next").getOrThrow()
        assertEquals(3, page.songs.size)
        assertFalse(page.isSnapshotUsableForSync)
        val result = Result.success(initial).completed()
        assertTrue(result.isFailure)
    }

    private fun row(id: String, occurrence: String?): String = """
        {"musicResponsiveListItemRenderer":{
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$id"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Artist"}]}}}],
          "playlistItemData":{"videoId":"$id","playlistSetVideoId":${occurrence?.let { "\"$it\"" } ?: "null"}},
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.test/art"}]}}}}}
    """.trimIndent()

    private val initialPage get() = """
        {"responseContext":{},"contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{"contents":[
          {"musicPlaylistShelfRenderer":{"contents":[${row("a", "first")},${row("b", "b")}],
           "continuations":[{"nextContinuationData":{"continuation":"next"}}]}}]}}}}}
    """.trimIndent()

    private val continuationPage get() = """
        {"responseContext":{},"continuationContents":{"musicPlaylistShelfContinuation":{"contents":[
          ${row("a", "second")},${row("a", null)},${row("a", null)}]
          ${if (repeatCursor) ",\"continuations\":[{\"nextContinuationData\":{\"continuation\":\"next\"}}]" else ""}}}}
    """.trimIndent()
}
