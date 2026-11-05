/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.player

/** Keep existing artwork keys when unique; disambiguate only colliding queue occurrences. */
internal class ArtworkItemKeys {
    private data class Entry(val base: String, val key: String)
    private val entries = mutableMapOf<Any, Entry>()
    private var nextKey = 0L

    fun resolve(windows: List<Pair<Any, String>>): List<String> {
        val bases = windows.toMap()
        entries.entries.removeAll { (uid, entry) -> bases[uid] != entry.base }
        val used = entries.values.mapTo(mutableSetOf()) { it.key }
        return windows.map { (uid, base) ->
            entries.getOrPut(uid) {
                var key = base
                while (!used.add(key)) key = "artwork-occurrence:${nextKey++}:$base"
                Entry(base, key)
            }.key
        }
    }
}
