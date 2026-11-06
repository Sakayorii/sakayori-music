/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.viewmodels

import android.app.Application
import android.os.Looper
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.widget.PlaylistWidgetManager
import com.sakayori.music.widget.SakayoriWidgetManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import com.sakayori.innertube.models.SongItem
import com.sakayori.music.constants.PreferLosslessKey
import com.sakayori.music.db.InternalDatabase
import com.sakayori.music.extensions.toMediaItem
import com.sakayori.music.playback.queues.buildPlaylistQueueFromDisplayed
import com.sakayori.music.utils.initialPreferenceValue
import com.sakayori.music.utils.updateCachedPreferences
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LyricsAndWidgetOptimizationTest {
    private val scopes = mutableListOf<CoroutineScope>()
    private var database: MusicDatabase? = null

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        database?.close()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `reprocessing identical lyrics preserves lines and mergedLyricsList instances`() {
        val viewModel = LyricsViewModel()
        val lrc = "[00:05.00]First line\n[00:08.00]Second line after gap"

        viewModel.processLyrics(lrc, emptyList(), false, true)
        pumpUntil { viewModel.mergedLyricsList.value.isNotEmpty() }

        val firstLines = viewModel.lines.value
        val firstMerged = viewModel.mergedLyricsList.value
        assertEquals(3, firstLines.size)
        assertEquals(3, firstMerged.size)

        viewModel.processLyrics(lrc, emptyList(), false, true)
        val job = ReflectionHelpers.getField<kotlinx.coroutines.Job?>(viewModel, "processJob")
        if (job != null) pumpUntil { job.isCompleted }

        assertSame(firstLines, viewModel.lines.value)
        assertSame(firstMerged, viewModel.mergedLyricsList.value)
    }

    @Test
    fun `widget update skips album art work when no home screen widgets exist`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = MusicDatabase(Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).build())
        database = db
        val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val playlistWidgetManager = PlaylistWidgetManager(context, db, appScope)
        val manager = SakayoriWidgetManager(context, db, playlistWidgetManager)

        manager.updateWidgets(
            title = "Song",
            artist = "Artist",
            artworkUri = "https://example.test/artwork.jpg",
            isPlaying = true,
            isLiked = false,
            duration = 180_000L,
            currentPosition = 12_000L,
        )

        assertNull(ReflectionHelpers.getField<String?>(manager, "cachedArtworkUri"))
    }

    @Test
    fun `filtered playlist row tap builds ListQueue from displayed filtered songs`() {
        val allSongs = listOf("a", "b", "c", "d").map { SongItem(it, it, emptyList(), thumbnail = "") }
        val filteredSongs = listOf(allSongs[1], allSongs[3])

        val queue = buildPlaylistQueueFromDisplayed(
            title = "Filtered Playlist",
            allItems = allSongs,
            displayedItems = filteredSongs,
            clickedItem = allSongs[3],
            isSameItem = { first, second -> first.id == second.id },
            toMediaItem = { it.toMediaItem() },
        )

        assertEquals(listOf("b", "d"), queue.items.map { it.mediaId })
        assertEquals(1, queue.startIndex)
        assertEquals("d", queue.preloadItem?.id)
    }

    @Test
    fun `cached preference snapshot supplies persisted initial value without waiting for flow emission`() {
        val store = object : DataStore<Preferences> {
            override val data = flowOf(preferencesOf(PreferLosslessKey to true))
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw AssertionError("Read-only test store")
        }
        updateCachedPreferences(store, preferencesOf(PreferLosslessKey to true))

        assertTrue(initialPreferenceValue(store, PreferLosslessKey, false))
    }

    private fun pumpUntil(done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!done() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        assertTrue("Condition met before timeout", done())
    }
}
