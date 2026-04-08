package com.sakayori.data.db

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun fromString_nullInput_returnsNull() {
        assertNull(converters.fromString(null))
    }

    @Test
    fun fromString_validJson_parsesCorrectly() {
        val json = """["a","b","c"]"""
        val result = converters.fromString(json)
        assertNotNull(result)
        assertEquals(3, result.size)
        assertEquals("a", result[0])
        assertEquals("c", result[2])
    }

    @Test
    fun fromString_invalidJson_returnsNull() {
        val result = converters.fromString("not json")
        assertNull(result)
    }

    @Test
    fun fromString_emptyArray_returnsEmptyList() {
        val result = converters.fromString("[]")
        assertNotNull(result)
        assertEquals(0, result.size)
    }

    @Test
    fun fromArrayList_nullInput_returnsNull() {
        assertNull(converters.fromArrayList(null))
    }

    @Test
    fun fromArrayList_validList_encodesToJson() {
        val list = listOf("one", "two", "three")
        val result = converters.fromArrayList(list)
        assertNotNull(result)
        assertTrue(result.contains("one"))
        assertTrue(result.contains("two"))
        assertTrue(result.contains("three"))
    }

    @Test
    fun fromArrayList_emptyList_encodesToEmptyArray() {
        val result = converters.fromArrayList(emptyList())
        assertEquals("[]", result)
    }

    @Test
    fun roundTrip_stringList_preservesData() {
        val original = listOf("alpha", "beta", "gamma", "delta")
        val encoded = converters.fromArrayList(original)
        assertNotNull(encoded)
        val decoded = converters.fromString(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun fromStringNull_withNullValues_parsesCorrectly() {
        val json = """["a",null,"c"]"""
        val result = converters.fromStringNull(json)
        assertNotNull(result)
        assertEquals(3, result.size)
        assertEquals("a", result[0])
        assertNull(result[1])
        assertEquals("c", result[2])
    }

    @Test
    fun fromArrayListNull_withNullValues_encodesCorrectly() {
        val list = listOf("x", null, "z")
        val result = converters.fromArrayListNull(list)
        assertNotNull(result)
        val decoded = converters.fromStringNull(result)
        assertEquals(list, decoded)
    }

    @Test
    fun fromTimestamp_nullInput_returnsNull() {
        assertNull(converters.fromTimestamp(null))
    }

    @Test
    fun fromTimestamp_validTimestamp_returnsLocalDateTime() {
        val timestamp = 1_700_000_000_000L
        val result = converters.fromTimestamp(timestamp)
        assertNotNull(result)
        assertEquals(2023, result.year)
    }

    @Test
    fun dateToTimestamp_nullInput_returnsNull() {
        assertNull(converters.dateToTimestamp(null))
    }

    @Test
    fun dateToTimestamp_roundTrip_preservesValue() {
        val original = 1_700_000_000_000L
        val date = converters.fromTimestamp(original)
        assertNotNull(date)
        val roundTrip = converters.dateToTimestamp(date)
        assertEquals(original, roundTrip)
    }

    @Test
    fun fromListMapToString_emptyList_encodesCorrectly() {
        val result = converters.fromListMapToString(emptyList())
        assertEquals("[]", result)
    }

    @Test
    fun fromListMapToString_withData_encodesCorrectly() {
        val list = listOf(mapOf("key" to "value", "key2" to "value2"))
        val result = converters.fromListMapToString(list)
        assertTrue(result.contains("key"))
        assertTrue(result.contains("value"))
    }

    @Test
    fun roundTrip_listMap_preservesData() {
        val original = listOf(
            mapOf("a" to "1", "b" to "2"),
            mapOf("c" to "3"),
        )
        val encoded = converters.fromListMapToString(original)
        val decoded = converters.fromStringToListMap(encoded)
        assertEquals(original, decoded)
    }
}
