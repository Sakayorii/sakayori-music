package com.sakayori.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class ModelToEntityTest {

    @Test
    fun connectArtists_emptyList_returnsEmptyString() {
        assertEquals("", emptyList<String>().connectArtists())
    }

    @Test
    fun connectArtists_singleArtist_returnsName() {
        assertEquals("Taylor Swift", listOf("Taylor Swift").connectArtists())
    }

    @Test
    fun connectArtists_twoArtists_separatedByComma() {
        assertEquals("A, B", listOf("A", "B").connectArtists())
    }

    @Test
    fun connectArtists_multipleArtists_separatedByComma() {
        assertEquals(
            "Drake, Rihanna, Future",
            listOf("Drake", "Rihanna", "Future").connectArtists(),
        )
    }

    @Test
    fun connectArtists_lastArtistNoTrailingComma() {
        val result = listOf("X", "Y", "Z").connectArtists()
        assertEquals("X, Y, Z", result)
        assertEquals(false, result.endsWith(","))
        assertEquals(false, result.endsWith(", "))
    }
}
