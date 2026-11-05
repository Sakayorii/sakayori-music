/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.player

internal data class ArtworkSwipeContext(
    val player: Any,
    val playingUid: Any?,
    val displayedKeys: List<Any>,
    val displayedIndex: Int,
)

internal enum class ArtworkSwipeCommand { NEXT, PREVIOUS }

/** Playback-command decisions only; the native artwork scroll owns all motion. */
internal class ArtworkSwipeState {
    private data class Gesture(val token: Any, val context: ArtworkSwipeContext, val anchorIndex: Int)
    private var gesture: Gesture? = null

    fun start(token: Any, context: ArtworkSwipeContext, anchorIndex: Int) {
        gesture = if (context.playingUid != null && anchorIndex in context.displayedKeys.indices) {
            Gesture(token, context.copy(displayedKeys = context.displayedKeys.toList()), anchorIndex)
        } else null
    }

    fun cancel(token: Any? = null) {
        if (token == null || gesture?.token === token) gesture = null
    }

    fun onScroll(
        context: ArtworkSwipeContext,
        index: Int,
        offset: Int,
        scrolling: Boolean,
        enabled: Boolean,
        canSkipNext: Boolean,
        canSkipPrevious: Boolean,
    ): ArtworkSwipeCommand? {
        val current = gesture ?: return null
        val owner = current.context
        if (!enabled || owner.player !== context.player || owner.playingUid != context.playingUid ||
            owner.displayedKeys != context.displayedKeys || owner.displayedIndex != context.displayedIndex ||
            index !in context.displayedKeys.indices
        ) {
            cancel()
            return null
        }
        if (offset != 0) return null
        if (index == current.anchorIndex) {
            if (!scrolling) cancel()
            return null
        }
        // Consume before dispatch: retained artwork, repeated offsets and the
        // player's synchronous transition callback cannot repeat this gesture.
        cancel()
        return when {
            index > current.anchorIndex && canSkipNext -> ArtworkSwipeCommand.NEXT
            index < current.anchorIndex && canSkipPrevious -> ArtworkSwipeCommand.PREVIOUS
            else -> null
        }
    }
}
