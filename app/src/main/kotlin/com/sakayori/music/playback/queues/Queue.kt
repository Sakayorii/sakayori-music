/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback.queues

import androidx.media3.common.MediaItem
import com.sakayori.music.extensions.metadata
import com.sakayori.music.models.MediaMetadata

interface Queue {
    val preloadItem: MediaMetadata?
    val preloadPosition: Long get() = 0L

    suspend fun getInitialStatus(): Status

    fun hasNextPage(): Boolean

    suspend fun nextPage(): List<MediaItem>

    data class Status(
        val title: String?,
        val items: List<MediaItem>,
        val mediaItemIndex: Int,
        val position: Long = 0L,
    ) {
        fun filterExplicit(enabled: Boolean = true) =
            if (enabled) {
                filterItems { it.metadata?.explicit != true }
            } else {
                this
            }

        fun filterVideoSongs(disableVideos: Boolean = false) =
            if (disableVideos) {
                filterItems { it.metadata?.isVideoSong != true }
            } else {
                this
            }

        private fun filterItems(include: (MediaItem) -> Boolean): Status {
            val indices = items.indices.filter { include(items[it]) }
            val selected = mediaItemIndex.coerceIn(0, items.lastIndex.coerceAtLeast(0))
            val retained = indices.indexOf(selected)
            val newIndex = if (retained >= 0) retained else indices.indexOfFirst { it >= selected }
                .takeIf { it >= 0 } ?: indices.lastIndex.coerceAtLeast(0)
            return copy(items = indices.map(items::get), mediaItemIndex = newIndex,
                position = if (retained >= 0) position else 0L)
        }
    }
}

fun List<MediaItem>.filterExplicit(enabled: Boolean = true) =
    if (enabled) {
        filterNot {
            it.metadata?.explicit == true
        }
    } else {
        this
    }

fun List<MediaItem>.filterVideoSongs(disableVideos: Boolean = false) =
    if (disableVideos) {
        filterNot { it.metadata?.isVideoSong == true }
    } else {
        this
    }
