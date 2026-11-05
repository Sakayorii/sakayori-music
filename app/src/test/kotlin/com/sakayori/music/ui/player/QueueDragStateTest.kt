/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueDragStateTest {
    private val player = Any()
    private val original = QueueDragSnapshot(player, listOf("a", "b", "c"), false)

    @Test
    fun `clearing the queue before a queued move cancels without throwing`() {
        val drag = QueueDragState()
        drag.start(original, "b")
        val empty = original.copy(uids = emptyList())

        assertNull(drag.move(empty, empty.uids, "b".hashCode(), "c".hashCode()))
        assertNull(drag.finish(empty))
    }

    @Test
    fun `clearing the queue before drop cancels without throwing`() {
        val drag = QueueDragState()
        drag.start(original, "a")
        assertEquals(0 to 2, drag.move(original, original.uids, "a".hashCode(), "c".hashCode()))

        assertNull(drag.finish(original.copy(uids = emptyList())))
    }

    @Test
    fun `replacement of equal length cannot receive the previous queues drop`() {
        val drag = QueueDragState()
        drag.start(original, "a")
        assertEquals(0 to 2, drag.move(original, original.uids, "a".hashCode(), "c".hashCode()))
        val replacement = original.copy(uids = listOf("x", "y", "z"))

        assertNull(drag.finish(replacement))
    }

    @Test
    fun `replacement before the first move cannot inherit a drag`() {
        val drag = QueueDragState()
        drag.start(original, "b")
        val replacement = original.copy(uids = listOf("x", "y", "z"))

        assertNull(drag.move(replacement, replacement.uids, "b".hashCode(), "c".hashCode()))
        assertNull(drag.finish(replacement))
    }

    @Test
    fun `removing the dragged occurrence cancels instead of moving its successor`() {
        val drag = QueueDragState()
        drag.start(original, "b")
        assertEquals(1 to 2, drag.move(original, original.uids, "b".hashCode(), "c".hashCode()))

        assertNull(drag.finish(original.copy(uids = listOf("a", "c"))))
    }

    @Test
    fun `ordinary multi step drag commits exactly once`() {
        val drag = QueueDragState()
        drag.start(original, "a")
        assertEquals(0 to 2, drag.move(original, original.uids, "a".hashCode(), "c".hashCode()))
        assertEquals(listOf("b", "c", "a"), drag.synchronize(original))
        assertEquals(2 to 1, drag.move(original, drag.synchronize(original), "a".hashCode(), "c".hashCode()))

        assertEquals(0 to 1, drag.finish(original))
        assertNull(drag.finish(original))
        assertNull(drag.move(original, original.uids, "a".hashCode(), "c".hashCode()))
    }

    @Test
    fun `metadata only snapshot refresh preserves the pending display order`() {
        val drag = QueueDragState()
        drag.start(original, "b")
        drag.move(original, original.uids, "b".hashCode(), "a".hashCode())
        val refreshed = original.copy(uids = original.uids.toList())

        assertEquals(listOf("b", "a", "c"), drag.synchronize(refreshed))
        assertEquals(1 to 0, drag.finish(refreshed))
    }

    @Test
    fun `shuffle reorder uses displayed occurrence positions`() {
        val shuffled = original.copy(uids = listOf("c", "a", "b"), shuffled = true)
        val drag = QueueDragState()
        drag.start(shuffled, "b")
        assertEquals(2 to 0, drag.move(shuffled, shuffled.uids, "b".hashCode(), "c".hashCode()))

        assertEquals(listOf("b", "c", "a"), drag.synchronize(shuffled))
        assertEquals(2 to 0, drag.finish(shuffled))
    }

    @Test
    fun `shuffle mode change cancels even when the displayed order is unchanged`() {
        val drag = QueueDragState()
        drag.start(original, "a")
        drag.move(original, original.uids, "a".hashCode(), "c".hashCode())

        assertNull(drag.finish(original.copy(shuffled = true)))
    }

    @Test
    fun `player replacement cannot inherit another players drag`() {
        val drag = QueueDragState()
        drag.start(original, "a")
        drag.move(original, original.uids, "a".hashCode(), "c".hashCode())

        assertNull(drag.finish(original.copy(player = Any())))
    }

    @Test
    fun `cancelled drag cannot resume when an old snapshot reappears`() {
        val drag = QueueDragState()
        drag.start(original, "a")
        drag.move(original, original.uids, "a".hashCode(), "c".hashCode())
        val replacement = original.copy(uids = listOf("x", "y", "z"))
        assertEquals(replacement.uids, drag.synchronize(replacement))

        assertNull(drag.move(original, original.uids, "a".hashCode(), "c".hashCode()))
        assertNull(drag.finish(original))
        drag.start(original, "b")
        drag.move(original, original.uids, "b".hashCode(), "a".hashCode())
        assertEquals(1 to 0, drag.finish(original))
    }

    @Test
    fun `empty single item and no movement do not send a command`() {
        val drag = QueueDragState()
        val empty = original.copy(uids = emptyList())
        drag.start(empty, "a")
        assertNull(drag.finish(empty))
        val single = original.copy(uids = listOf("a"))
        drag.start(single, "a")
        assertNull(drag.finish(single))
        drag.start(original, "b")
        assertNull(drag.finish(original))
    }

    @Test
    fun `non queue header or stale callback key cannot move another occurrence`() {
        val drag = QueueDragState()
        drag.start(original, "a")
        assertNull(drag.move(original, original.uids, "a".hashCode(), "queue_top_spacer"))
        assertNull(drag.finish(original))
        drag.start(original, "a")
        assertNull(drag.move(original, original.uids, "b".hashCode(), "c".hashCode()))
        assertNull(drag.finish(original))
    }

    @Test
    fun `repeated recording occurrences stay distinct`() {
        val repeated = original.copy(uids = listOf("song-a-first", "song-b", "song-a-second"))
        val drag = QueueDragState()
        drag.start(repeated, "song-a-second")

        assertEquals(2 to 0, drag.move(repeated, repeated.uids, "song-a-second".hashCode(), "song-a-first".hashCode()))
        assertEquals(listOf("song-a-second", "song-a-first", "song-b"), drag.synchronize(repeated))
        assertEquals(2 to 0, drag.finish(repeated))
    }
}
