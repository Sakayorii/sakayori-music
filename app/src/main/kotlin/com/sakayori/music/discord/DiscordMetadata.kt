package com.sakayori.music.discord

import com.sakayori.music.db.entities.Song

// Tracks can carry an album name without a linked album in the local library.
internal fun Song.discordAlbumName(): String? =
    album?.title?.takeIf { it.isNotBlank() }
        ?: song.albumName?.takeIf { it.isNotBlank() }
