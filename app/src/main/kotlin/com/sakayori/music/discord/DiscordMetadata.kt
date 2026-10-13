package com.sakayori.music.discord

import com.sakayori.music.db.entities.Song

// Tracks can carry an album name without a linked album in the local library.
internal fun Song.discordAlbumName(): String? {
    album?.title?.takeIf { it.isNotBlank() }?.let { return it }
    val metaName = song.albumName?.takeIf { it.isNotBlank() } ?: return null
    if (metaName.equals(song.title, ignoreCase = true)) return null
    return metaName
}
