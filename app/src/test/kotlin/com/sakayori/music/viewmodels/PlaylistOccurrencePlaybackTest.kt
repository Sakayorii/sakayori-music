/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.viewmodels

import android.app.Application
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.sakayori.innertube.models.MusicResponsiveListItemRenderer
import com.sakayori.innertube.models.SongItem
import com.sakayori.innertube.pages.PlaylistPage
import com.sakayori.music.playback.queues.YouTubePlaylistQueue
import com.sakayori.music.ui.player.playerArtworkKey
import com.sakayori.music.ui.player.getMediaItems
import com.sakayori.music.extensions.toMediaItem
import com.sakayori.music.extensions.toPlaylistMediaItems
import com.sakayori.music.extensions.metadata
import com.sakayori.music.extensions.PLAYLIST_OCCURRENCE_EXTRA
import com.sakayori.music.models.playlistOccurrenceKeys
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaylistOccurrencePlaybackTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val first = SongItem("a", "A", emptyList(), thumbnail = "", setVideoId = "first")
    private val b = SongItem("b", "B", emptyList(), thumbnail = "", setVideoId = "b")

    @Test
    fun `repeated recordings have distinct large artwork keys`() = runBlocking {
        val songs = listOf(first, b, first.copy(setVideoId = "second"))
        val queue = YouTubePlaylistQueue("playlist", initialSongs = songs, startIndex = 2).getInitialStatus()
        assertEquals(2, queue.mediaItemIndex)
        assertEquals(3, queue.items.map(::playerArtworkKey).toSet().size)
    }

    @Test
    fun `anonymous repeats have distinct playback artwork keys`() = runBlocking {
        val anonymous = first.copy(setVideoId = null)
        val queue = YouTubePlaylistQueue("playlist", initialSongs = listOf(anonymous, b, anonymous)).getInitialStatus()
        assertEquals(3, queue.items.map(::playerArtworkKey).toSet().size)
    }

    @Test
    fun `equal adjacent media items keep the selected artwork at the current slot`() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            val item = first.copy(setVideoId = null).toMediaItem()
            player.setMediaItems(listOf(item, item, b.toMediaItem()), 1, 0)
            assertEquals(1, getMediaItems(player, true).currentIndex)
        } finally {
            player.release()
        }
    }

    @Test
    fun `equal media items supplied directly to a queue cannot collide in the artwork grid`() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext()).build()
        try {
            val item = first.copy(setVideoId = null).toMediaItem()
            player.setMediaItems(listOf(item, item, b.toMediaItem()), 1, 0)
            assertEquals(3, getMediaItems(player, true).items.map(::playerArtworkKey).toSet().size)
        } finally {
            player.release()
        }
    }

    @Test
    fun `playable playlist row without occurrence id is retained`() {
        val renderer = json.decodeFromString<MusicResponsiveListItemRenderer>("""
                {"flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"A"}]}}}],
                 "playlistItemData":{"videoId":"a"},
                 "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[{"url":"https://example.test/art"}]}}}}
            """.trimIndent())
        assertNotNull(PlaylistPage.fromMusicResponsiveListItemRenderer(renderer))
    }

    @Test
    fun `queue filtering preserves the selected occurrence and its recording cache identity`() = runBlocking {
        val songs = listOf(first.copy(explicit = true), b, first.copy(setVideoId = "second"))
        val queue = YouTubePlaylistQueue("playlist", initialSongs = songs, startIndex = 2).getInitialStatus().filterExplicit(true)
        assertEquals(1, queue.mediaItemIndex)
        assertEquals("second", queue.items[1].metadata?.setVideoId)
        assertEquals("a", queue.items[1].mediaId)
        assertEquals("a", queue.items[1].localConfiguration?.customCacheKey)
    }

    @Test
    fun `filtered queue keeps the anonymous original row identity`() = runBlocking {
        val songs = listOf(first.copy(setVideoId = null), b, first.copy(setVideoId = null))
        val keys = playlistOccurrenceKeys(songs)
        val queue = YouTubePlaylistQueue("playlist", initialSongs = listOf(songs[0], songs[2]),
            initialOccurrenceKeys = listOf(keys[0], keys[2]), startIndex = 1).getInitialStatus()
        assertEquals(keys[2], queue.items[1].mediaMetadata.extras?.getString(PLAYLIST_OCCURRENCE_EXTRA))
        assertNull(queue.items[1].metadata?.setVideoId)
    }

    @Test
    fun `selection playback also keeps both anonymous entries distinct`() {
        val anonymous = first.copy(setVideoId = null)
        val items = listOf(anonymous, b, anonymous).toPlaylistMediaItems()
        assertEquals(3, items.map(::playerArtworkKey).toSet().size)
        assertEquals(listOf("a", "b", "a"), items.map { it.mediaId })
    }

    @Test
    fun `selected anonymous identity survives playback order changes`() {
        val anonymous = first.copy(setVideoId = null)
        val original = listOf(anonymous, b, anonymous)
        val keys = playlistOccurrenceKeys(original)
        val selected = listOf(original[0], original[2]).toPlaylistMediaItems("playlist", listOf(keys[0], keys[2]))
        val reordered = selected.reversed()
        assertEquals(listOf(keys[2], keys[0]), reordered.map { it.mediaMetadata.extras!!.getString(PLAYLIST_OCCURRENCE_EXTRA) })
    }
}
