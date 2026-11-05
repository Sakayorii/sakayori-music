/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback.queues

import androidx.media3.common.MediaItem
import com.sakayori.innertube.YouTube
import com.sakayori.innertube.models.SongItem
import com.sakayori.music.extensions.toPlaylistMediaItems
import com.sakayori.music.models.MediaMetadata
import com.sakayori.music.models.playlistOccurrenceKeys
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class YouTubePlaylistQueue(
    private val playlistId: String,
    private val playlistTitle: String? = null,
    private val initialSongs: List<SongItem> = emptyList(),
    private val initialContinuation: String? = null,
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
    private val initialOccurrenceKeys: List<String>? = null,
) : Queue {
    private var continuation: String? = initialContinuation
    private var retryCount = 0
    private val maxRetries = 3
    private var continuationPageIndex = 0

    override suspend fun getInitialStatus(): Queue.Status {
        return withContext(IO) {
            if (initialSongs.isNotEmpty()) {
                Queue.Status(
                    title = playlistTitle,
                    items = initialSongs.toPlaylistMediaItems(playlistId, initialOccurrenceKeys ?: playlistOccurrenceKeys(initialSongs)),
                    mediaItemIndex = startIndex,
                )
            } else {
                val playlistPage = YouTube.playlist(playlistId).getOrThrow()
                continuation = playlistPage.songsContinuation
                Queue.Status(
                    title = playlistPage.playlist.title,
                    items = playlistPage.songs.toPlaylistMediaItems(playlistId),
                    mediaItemIndex = startIndex,
                )
            }
        }
    }

    override fun hasNextPage(): Boolean = continuation != null

    override suspend fun nextPage(): List<MediaItem> {
        return withContext(IO) {
            val currentContinuation = continuation ?: return@withContext emptyList()
            var lastException: Throwable? = null
            
            for (attempt in 0 until maxRetries) {
                try {
                    val continuationPage = YouTube.playlistContinuation(currentContinuation).getOrThrow()
                    continuation = continuationPage.continuation
                    retryCount = 0
                    return@withContext continuationPage.songs.toPlaylistMediaItems(
                        playlistId,
                        playlistOccurrenceKeys(continuationPage.songs, "page:${continuationPageIndex++}"),
                    )
                } catch (e: Exception) {
                    lastException = e
                    retryCount++
                    if (retryCount >= maxRetries) {
                        continuation = null
                    }
                }
            }
            throw lastException ?: Exception("Failed to get next page")
        }
    }
}
