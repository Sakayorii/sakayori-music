/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.menu

import com.sakayori.innertube.models.SongItem
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.db.entities.Playlist
import com.sakayori.music.models.toMediaMetadata

internal suspend fun addOnlinePlaylistSelection(
    database: MusicDatabase,
    playlist: Playlist,
    songs: List<SongItem>,
) {
    database.withTransaction {
        songs.forEach { insert(it.toMediaMetadata()) }
        // This is a copy into another playlist: its occurrence IDs are new, not
        // the source playlist's setVideoIds. Preserve every selected position.
        addSongsToPlaylist(playlist, songs.map { it.id to null })
    }
}
