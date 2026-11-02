/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakayori.music.constants.HideExplicitKey
import com.sakayori.music.constants.HideVideoSongsKey
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.db.entities.Song
import com.sakayori.music.playback.AudioRenditionStore
import com.sakayori.music.extensions.filterExplicit
import com.sakayori.music.extensions.filterVideoSongs
import com.sakayori.music.utils.dataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CachePlaylistViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        private val renditions: AudioRenditionStore,
    ) : ViewModel() {
        private data class Inputs(
            val flagged: List<Song>,
            val hideExplicit: Boolean,
            val hideVideoSongs: Boolean,
        )

        @OptIn(ExperimentalCoroutinesApi::class)
        val cachedSongs: StateFlow<List<Song>> =
            combine(
                database.cachePlaylistSongs(),
                context.dataStore.data.map { it[HideExplicitKey] ?: false }.distinctUntilChanged(),
                context.dataStore.data.map { it[HideVideoSongsKey] ?: false }.distinctUntilChanged(),
                ::Inputs,
            ).mapLatest { (flagged, hideExplicit, hideVideoSongs) ->
                val partition = partitionCachedSongs(flagged) { songId, contentLength ->
                    renditions.isFullyCached(songId, contentLength)
                }

                // Clearing the flag removes these songs from cachePlaylistSongs(), so this
                // re-emits once and then settles rather than looping.
                partition.stale.forEach { song ->
                    database.query { clearPlaybackCached(song.song.id) }
                }

                partition.stillCached
                    .sortedByDescending { it.song.dateDownload }
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)
            }.flowOn(Dispatchers.IO)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        fun removeSongFromCache(songId: String) {
            viewModelScope.launch(Dispatchers.IO) {
                renditions.removePlayerCopies(songId)
                // Saved downloads are separate; removing playback-cache copies must retain them.
                database.query {
                    val song = getSongByIdBlocking(songId) ?: return@query
                    val partition = partitionCachedSongs(listOf(song)) { id, contentLength ->
                        renditions.isFullyCached(id, contentLength)
                    }
                    partition.stale.forEach { clearPlaybackCached(it.song.id) }
                }
            }
        }
    }

/**
 * Result of checking which Cache Playlist entries still have their bytes on disk.
 */
internal data class CachedSongPartition(
    val stillCached: List<Song>,
    val stale: List<Song>,
)

/**
 * Splits songs flagged as belonging to the Cache Playlist into those whose data is still
 * present and those whose flag must be cleared.
 *
 * A downloaded song is always considered present: its file is managed by the download service
 * rather than the player cache. Missing Room format metadata is not proof of a cache miss:
 * the rendition store can know the actual file length independently.
 *
 * [isCached] receives the song id and its content length, and reports whether the complete file
 * is available in either cache.
 */
internal fun partitionCachedSongs(
    flagged: List<Song>,
    isCached: (songId: String, contentLength: Long?) -> Boolean,
): CachedSongPartition {
    val stillCached = mutableListOf<Song>()
    val stale = mutableListOf<Song>()

    for (song in flagged) {
        val contentLength = song.format?.contentLength
        val present = song.song.isDownloaded || isCached(song.song.id, contentLength)
        if (present) stillCached += song else stale += song
    }

    return CachedSongPartition(stillCached, stale)
}
