/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.models

import com.sakayori.innertube.models.SongItem

internal fun playlistOccurrenceKeys(songs: List<SongItem>, anonymousPrefix: String = "anonymous"): List<String> =
    songs.mapIndexed { index, song ->
        song.setVideoId?.takeIf { it.isNotBlank() }?.let { "set:$it" }
            ?: "$anonymousPrefix:${song.id.length}:${song.id}:$index"
    }

internal fun distinctPlaylistOccurrences(songs: List<SongItem>): List<SongItem> {
    val seen = HashSet<String>()
    return songs.filter { song -> song.setVideoId?.takeIf { it.isNotBlank() }?.let(seen::add) ?: true }
}

internal fun isPlayingPlaylistOccurrence(
    song: SongItem,
    occurrenceKey: String,
    playlistId: String,
    songs: List<SongItem>,
    playingId: String?,
    playingSetVideoId: String?,
    playingPlaylistId: String?,
    playingOccurrenceKey: String?,
): Boolean {
    if (song.id != playingId) return false
    // Retain the existing recording-level toggle when there is no ambiguous row.
    if (songs.count { it.id == song.id } <= 1) return true
    if (!song.setVideoId.isNullOrBlank() && !playingSetVideoId.isNullOrBlank()) {
        return song.setVideoId == playingSetVideoId
    }
    return playingPlaylistId == playlistId && playingOccurrenceKey == occurrenceKey
}
