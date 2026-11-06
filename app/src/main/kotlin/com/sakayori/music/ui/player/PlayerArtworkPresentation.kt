/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.player

import androidx.media3.common.MediaItem
import coil3.Image

internal data class ReadyPlayerArtwork(
    val key: String,
    val mediaId: String,
    val uri: String?,
    val image: Image?,
)

/** Display state only; the native queue, metadata and scroll own their behavior. */
internal data class PlayerArtworkPresentation(
    val mediaItems: MediaItemsData,
    val current: ReadyPlayerArtwork,
    val previous: ReadyPlayerArtwork?,
) {
    fun imageFor(item: MediaItem): Image? {
        val key = playerArtworkKey(item)
        return current.takeIf { it.key == key }?.image
            ?: previous?.takeIf { it.key == key }?.image
    }
}

/** Pending loads may retain the incumbent; a resolved failure owns its own tile. */
internal fun updatePlayerArtworkPresentation(
    previous: PlayerArtworkPresentation?,
    mediaItems: MediaItemsData,
    incoming: MediaItem?,
    incomingUri: String?,
    readyImage: Image?,
    imageUnavailable: Boolean,
): PlayerArtworkPresentation? {
    if (incoming == null) return null
    if (readyImage == null && !imageUnavailable) return previous
    val ready = ReadyPlayerArtwork(
        key = playerArtworkKey(incoming),
        mediaId = incoming.mediaId,
        uri = incomingUri,
        image = readyImage,
    )
    return PlayerArtworkPresentation(
        mediaItems = mediaItems,
        current = ready,
        previous = if (previous?.current?.key != ready.key) previous?.current else previous.previous,
    )
}
