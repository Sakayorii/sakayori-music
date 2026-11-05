/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkSwipeStateTest {
    private val player = Any()
    private val heldArtwork = ArtworkSwipeContext(player, "playing-c", listOf("a", "b", "c"), 1)

    private fun scroll(
        state: ArtworkSwipeState,
        index: Int,
        offset: Int = 0,
        context: ArtworkSwipeContext = heldArtwork,
        scrolling: Boolean = true,
    ) = state.onScroll(context, index, offset, scrolling, enabled = true, canSkipNext = true, canSkipPrevious = true)

    @Test
    fun `a reverse gesture while artwork is held uses the tile where that gesture began`() {
        val state = ArtworkSwipeState()
        // Next was tapped while B's image was retained, then a swipe moved the
        // grid to its last tile. Audio is now D but the displayed selection is B.
        val nowPlayingD = heldArtwork.copy(playingUid = "playing-d")
        state.start(Any(), nowPlayingD, anchorIndex = 2)
        assertNull(scroll(state, 1, offset = 30, context = nowPlayingD))

        assertEquals(ArtworkSwipeCommand.PREVIOUS, scroll(state, 1, context = nowPlayingD))
    }

    @Test
    fun `moving back and forth while holding one drag sends at most one command`() {
        val state = ArtworkSwipeState()
        state.start(Any(), heldArtwork, anchorIndex = 1)
        assertEquals(ArtworkSwipeCommand.PREVIOUS, scroll(state, 0))
        assertNull(scroll(state, 0, offset = 40))
        assertNull(scroll(state, 1))
        assertNull(scroll(state, 0, offset = 40))

        assertNull(scroll(state, 0))
    }

    @Test
    fun `cancelled gesture cannot turn subsequent scroll offsets into commands`() {
        val state = ArtworkSwipeState()
        val token = Any()
        state.start(token, heldArtwork, anchorIndex = 1)
        state.cancel(token)

        assertNull(scroll(state, 0))
    }

    @Test
    fun `ordinary next and previous gestures each dispatch once`() {
        val state = ArtworkSwipeState()
        state.start(Any(), heldArtwork, anchorIndex = 1)
        assertNull(scroll(state, 1, offset = 30))
        assertEquals(ArtworkSwipeCommand.NEXT, scroll(state, 2))
        assertNull(scroll(state, 2))
        state.start(Any(), heldArtwork, anchorIndex = 1)
        assertEquals(ArtworkSwipeCommand.PREVIOUS, scroll(state, 0))
        assertNull(scroll(state, 0))
    }

    @Test
    fun `programmatic scroll has no gesture authority`() {
        val state = ArtworkSwipeState()
        assertNull(scroll(state, 0))
        assertNull(scroll(state, 2))
        state.start(Any(), heldArtwork, anchorIndex = 1)
        state.cancel() // The native positioning call takes over.
        assertNull(scroll(state, 2))
    }

    @Test
    fun `settled final frame dispatches even if the scrolling flag already cleared`() {
        val state = ArtworkSwipeState()
        state.start(Any(), heldArtwork, anchorIndex = 1)
        assertEquals(ArtworkSwipeCommand.NEXT, scroll(state, 2, scrolling = false))
        assertNull(scroll(state, 2, scrolling = false))
    }

    @Test
    fun `partial swipe snapping back does not dispatch`() {
        val state = ArtworkSwipeState()
        state.start(Any(), heldArtwork, anchorIndex = 1)
        assertNull(scroll(state, 0, offset = 80))
        assertNull(scroll(state, 1, scrolling = false))
        assertNull(scroll(state, 0))
    }

    @Test
    fun `external next during a pending gesture invalidates its old playback owner`() {
        val state = ArtworkSwipeState()
        state.start(Any(), heldArtwork, anchorIndex = 1)
        assertNull(scroll(state, 2, context = heldArtwork.copy(playingUid = "playing-d")))
        assertNull(scroll(state, 2))
    }

    @Test
    fun `player replacement cannot inherit a swipe`() {
        val state = ArtworkSwipeState()
        state.start(Any(), heldArtwork, anchorIndex = 1)
        assertNull(scroll(state, 2, context = heldArtwork.copy(player = Any())))
    }

    @Test
    fun `late artwork window replacement cannot reinterpret a gesture`() {
        val state = ArtworkSwipeState()
        state.start(Any(), heldArtwork, anchorIndex = 1)
        assertNull(scroll(state, 2, context = heldArtwork.copy(displayedKeys = listOf("b", "c", "d"))))
        state.start(Any(), heldArtwork, anchorIndex = 1)
        assertNull(scroll(state, 2, context = heldArtwork.copy(displayedIndex = 0)))
    }

    @Test
    fun `cancellation from an old drag cannot cancel a new gesture`() {
        val state = ArtworkSwipeState()
        val oldToken = Any()
        state.start(oldToken, heldArtwork, anchorIndex = 1)
        state.start(Any(), heldArtwork, anchorIndex = 1)
        state.cancel(oldToken)
        assertEquals(ArtworkSwipeCommand.NEXT, scroll(state, 2))
    }

    @Test
    fun `disabled swiping and missing playback cannot dispatch`() {
        val state = ArtworkSwipeState()
        state.start(Any(), heldArtwork, anchorIndex = 1)
        assertNull(state.onScroll(heldArtwork, 2, 0, true, enabled = false, canSkipNext = true, canSkipPrevious = true))
        assertNull(scroll(state, 2))
        state.start(Any(), heldArtwork.copy(playingUid = null), anchorIndex = 1)
        assertNull(scroll(state, 2))
    }

    @Test
    fun `unavailable skip is consumed rather than replayed when capabilities change`() {
        val state = ArtworkSwipeState()
        state.start(Any(), heldArtwork, anchorIndex = 1)
        assertNull(state.onScroll(heldArtwork, 2, 0, true, enabled = true, canSkipNext = false, canSkipPrevious = true))
        assertNull(scroll(state, 2))
    }
}
