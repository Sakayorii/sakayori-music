/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.viewmodels

import com.sakayori.innertube.models.SongItem
import com.sakayori.music.models.distinctPlaylistOccurrences
import com.sakayori.music.models.isPlayingPlaylistOccurrence
import com.sakayori.music.models.playlistOccurrenceKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistOccurrencesTest {
    private val first = SongItem("a", "A", emptyList(), thumbnail = "", setVideoId = "a-first")
    private val b = SongItem("b", "B", emptyList(), thumbnail = "", setVideoId = "b-only")
    private val second = first.copy(setVideoId = "a-second")

    @Test
    fun `two occurrences of a recording survive list filtering`() {
        assertEquals(listOf(first, b, second), distinctPlaylistOccurrences(listOf(first, b, second)))
    }

    @Test
    fun `anonymous repeated entries are preserved`() {
        val anonymous = first.copy(setVideoId = null)
        assertEquals(listOf(anonymous, b, anonymous), distinctPlaylistOccurrences(listOf(anonymous, b, anonymous)))
    }

    @Test
    fun `selecting one duplicate does not select the other`() {
        val songs = listOf(first, b, second)
        val keys = playlistOccurrenceKeys(songs)
        val selected = setOf(keys[2])
        assertEquals(listOf(second), songs.filterIndexed { index, _ -> keys[index] in selected })
    }

    @Test
    fun `anonymous duplicate row keys are unique`() {
        val anonymous = first.copy(setVideoId = null)
        val keys = playlistOccurrenceKeys(listOf(anonymous, b, anonymous))
        assertEquals(3, keys.toSet().size)
    }

    @Test
    fun `clicking the second occurrence must not toggle playback of the first`() {
        val songs = listOf(first, b, second)
        assertFalse(isPlayingPlaylistOccurrence(second, "a-second", "playlist", songs,
            first.id, first.setVideoId, null, null))
    }

    @Test
    fun `duplicate representation of one setVideoId is collapsed without losing other occurrences`() {
        assertEquals(listOf(first, b, second), distinctPlaylistOccurrences(listOf(first, b, first.copy(title = "Updated"), second)))
    }

    @Test
    fun `appending a page keeps existing anonymous selection keys stable`() {
        val initial = listOf(first.copy(setVideoId = null), b)
        assertEquals(playlistOccurrenceKeys(initial), playlistOccurrenceKeys(initial + second).take(initial.size))
    }

    @Test
    fun `search filtering retains original occurrence keys for range selection`() {
        val songs = listOf(first.copy(setVideoId = null), b, second.copy(setVideoId = null))
        val keys = playlistOccurrenceKeys(songs)
        val filtered = songs.withIndex().filter { it.value.id == "a" }
        val chosen = setOf(keys[filtered.last().index])
        assertEquals(listOf(2), filtered.filter { keys[it.index] in chosen }.map { it.index })
    }

    @Test
    fun `unique recording retains existing toggle behavior across playlists`() {
        assertTrue(isPlayingPlaylistOccurrence(first, "key", "playlist", listOf(first, b), first.id, null, null, null))
    }

    @Test
    fun `anonymous current row requires the matching playlist and original occurrence key`() {
        val anonymous = first.copy(setVideoId = null)
        val songs = listOf(anonymous, b, anonymous)
        val keys = playlistOccurrenceKeys(songs)
        assertTrue(isPlayingPlaylistOccurrence(anonymous, keys[2], "playlist", songs, "a", null, "playlist", keys[2]))
        assertFalse(isPlayingPlaylistOccurrence(anonymous, keys[0], "playlist", songs, "a", null, "playlist", keys[2]))
        assertFalse(isPlayingPlaylistOccurrence(anonymous, keys[2], "other", songs, "a", null, "playlist", keys[2]))
    }

    @Test
    fun `empty and anonymous missing or blank ids do not discard represented rows`() {
        assertTrue(distinctPlaylistOccurrences(emptyList()).isEmpty())
        val unknown = listOf(first.copy(setVideoId = null), first.copy(setVideoId = ""))
        assertEquals(unknown, distinctPlaylistOccurrences(unknown))
        assertEquals(2, playlistOccurrenceKeys(unknown).toSet().size)
    }
}
