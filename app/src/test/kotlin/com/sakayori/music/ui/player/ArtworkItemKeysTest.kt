/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ArtworkItemKeysTest {
    @Test
    fun `unique artwork keeps its original keys`() {
        assertEquals(listOf("a", "b", "c"), ArtworkItemKeys().resolve(listOf(1 to "a", 2 to "b", 3 to "c")))
    }

    @Test
    fun `duplicate occurrence retains its key when the three item window advances`() {
        val keys = ArtworkItemKeys()
        val first = keys.resolve(listOf(1 to "a", 2 to "a", 3 to "b"))
        assertNotEquals(first[0], first[1])
        val next = keys.resolve(listOf(2 to "a", 3 to "b", 4 to "c"))
        assertEquals(first.drop(1), next.take(2))
    }

    @Test
    fun `colliding uid hash codes do not merge different occurrences`() {
        assertEquals("Aa".hashCode(), "BB".hashCode())
        val keys = ArtworkItemKeys().resolve(listOf("Aa" to "a", "BB" to "a"))
        assertEquals(2, keys.toSet().size)
    }

    @Test
    fun `metadata identity changes and an empty queue retire prior entries`() {
        val keys = ArtworkItemKeys()
        keys.resolve(listOf(1 to "a", 2 to "a"))
        assertEquals(listOf("updated"), keys.resolve(listOf(2 to "updated")))
        assertEquals(emptyList<String>(), keys.resolve(emptyList()))
        assertEquals(listOf("a"), keys.resolve(listOf(3 to "a")))
    }
}
