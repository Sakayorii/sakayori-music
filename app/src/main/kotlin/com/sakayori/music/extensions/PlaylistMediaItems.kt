/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.extensions

import android.os.Bundle
import androidx.media3.common.MediaItem
import com.sakayori.innertube.models.SongItem
import com.sakayori.music.models.playlistOccurrenceKeys

internal const val PLAYLIST_ID_EXTRA = "sakayori.playlist.id"
internal const val PLAYLIST_OCCURRENCE_EXTRA = "sakayori.playlist.occurrence"

/** Playback/UI identity only. The recording ID and encoded-byte cache key stay unchanged. */
internal fun List<SongItem>.toPlaylistMediaItems(
    playlistId: String = "",
    occurrenceKeys: List<String> = playlistOccurrenceKeys(this),
): List<MediaItem> {
    val keys = occurrenceKeys.takeIf { it.size == size } ?: playlistOccurrenceKeys(this)
    return mapIndexed { index, song ->
        val item = song.toMediaItem()
        val extras = item.mediaMetadata.extras?.let(::Bundle) ?: Bundle()
        extras.putString(PLAYLIST_ID_EXTRA, playlistId)
        extras.putString(PLAYLIST_OCCURRENCE_EXTRA, keys[index])
        item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
    }
}
