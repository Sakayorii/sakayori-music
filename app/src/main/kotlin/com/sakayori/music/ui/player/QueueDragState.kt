/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.player

internal data class QueueDragSnapshot(
    val player: Any,
    val uids: List<Any>,
    val shuffled: Boolean,
)

/** Queue reorder bookkeeping, kept independent of Compose for regression tests. */
internal class QueueDragState {
    private var source: QueueDragSnapshot? = null
    private var draggedUid: Any? = null
    private var order: MutableList<Any>? = null
    private var moved = false

    fun start(snapshot: QueueDragSnapshot, uid: Any) {
        cancel()
        if (uid !in snapshot.uids) return
        source = snapshot.copy(uids = snapshot.uids.toList())
        draggedUid = uid
        order = snapshot.uids.toMutableList()
    }

    fun move(
        snapshot: QueueDragSnapshot,
        displayedUids: List<Any>,
        fromKey: Any,
        toKey: Any,
    ): Pair<Int, Int>? {
        if (!owns(snapshot) || displayedUids != order) {
            cancel()
            return null
        }
        val currentOrder = order ?: return null
        // Keep the existing lazy-list keys, but resolve them against the owned
        // occurrences, not indices from a layout that may predate a list update.
        val fromUid = currentOrder.singleOrNull { it.hashCode() == fromKey }
        val toUid = currentOrder.singleOrNull { it.hashCode() == toKey }
        if (fromUid != draggedUid || toUid == null) {
            cancel()
            return null
        }
        val from = currentOrder.indexOf(fromUid)
        val to = currentOrder.indexOf(toUid)
        currentOrder.add(to, currentOrder.removeAt(from))
        moved = true
        return from to to
    }

    /** Preserve a pending reorder across metadata-only updates; cancel structural changes. */
    fun synchronize(snapshot: QueueDragSnapshot): List<Any> {
        if (!owns(snapshot)) cancel()
        return order?.toList() ?: snapshot.uids
    }

    fun finish(snapshot: QueueDragSnapshot): Pair<Int, Int>? {
        val move = if (moved && owns(snapshot)) {
            snapshot.uids.indexOf(draggedUid) to checkNotNull(order).indexOf(draggedUid)
        } else null
        cancel()
        return move
    }

    private fun owns(snapshot: QueueDragSnapshot): Boolean = source?.let {
        it.player === snapshot.player && it.shuffled == snapshot.shuffled && it.uids == snapshot.uids
    } == true

    private fun cancel() {
        source = null
        draggedUid = null
        order = null
        moved = false
    }
}
