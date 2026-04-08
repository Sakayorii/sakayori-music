package com.sakayori.music.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StringMatcherTest {

    @Test
    fun levenshtein_identicalStrings_returnsZero() {
        assertEquals(0, levenshtein("hello", "hello"))
    }

    @Test
    fun levenshtein_emptyStrings_returnsZero() {
        assertEquals(0, levenshtein("", ""))
    }

    @Test
    fun levenshtein_oneEmpty_returnsLength() {
        assertEquals(5, levenshtein("hello", ""))
        assertEquals(5, levenshtein("", "hello"))
    }

    @Test
    fun levenshtein_singleSubstitution_returnsOne() {
        assertEquals(1, levenshtein("cat", "bat"))
    }

    @Test
    fun levenshtein_singleInsertion_returnsOne() {
        assertEquals(1, levenshtein("cat", "cats"))
    }

    @Test
    fun levenshtein_singleDeletion_returnsOne() {
        assertEquals(1, levenshtein("cats", "cat"))
    }

    @Test
    fun levenshtein_completelyDifferent_returnsMax() {
        assertEquals(3, levenshtein("abc", "xyz"))
    }

    @Test
    fun bestMatchingIndex_exactMatch_returnsIndex() {
        val list = listOf("apple", "banana", "cherry")
        val result = bestMatchingIndex("banana", list)
        assertEquals(1, result)
    }

    @Test
    fun bestMatchingIndex_closeMatch_returnsClosestIndex() {
        val list = listOf("apple", "banana", "cherry")
        val result = bestMatchingIndex("banan", list)
        assertEquals(1, result)
    }

    @Test
    fun bestMatchingIndex_noCloseMatch_returnsNull() {
        val list = listOf("a", "b", "c")
        val result = bestMatchingIndex("very long different string here", list)
        assertNull(result)
    }

    @Test
    fun bestMatchingIndex_emptyList_returnsNull() {
        val result = bestMatchingIndex("query", emptyList())
        assertNull(result)
    }
}
