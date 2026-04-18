package com.sakayori.domain.extension

import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AllExtTest {

    @Test
    fun isBefore_pastDate_returnsTrue() {
        val a = LocalDateTime(2024, 1, 1, 0, 0)
        val b = LocalDateTime(2025, 1, 1, 0, 0)
        assertTrue(a.isBefore(b))
        assertFalse(b.isBefore(a))
    }

    @Test
    fun isAfter_futureDate_returnsTrue() {
        val a = LocalDateTime(2025, 1, 1, 0, 0)
        val b = LocalDateTime(2024, 1, 1, 0, 0)
        assertTrue(a.isAfter(b))
        assertFalse(b.isAfter(a))
    }

    @Test
    fun plusSeconds_addsCorrectAmount() {
        val date = LocalDateTime(2025, 6, 15, 12, 0, 0)
        val later = date.plusSeconds(60)
        assertEquals(12, later.hour)
        assertEquals(1, later.minute)
    }

    @Test
    fun plusSeconds_addsHours() {
        val date = LocalDateTime(2025, 6, 15, 12, 0, 0)
        val later = date.plusSeconds(3600)
        assertEquals(13, later.hour)
        assertEquals(0, later.minute)
    }

    @Test
    @Suppress("DEPRECATION")
    fun beforeXDays_subtractsDays() {
        val date = LocalDateTime(2025, 6, 15, 12, 0, 0)
        val earlier = date.beforeXDays(5)
        assertEquals(10, earlier.dayOfMonth)
        assertEquals(6, earlier.monthNumber)
    }

    @Test
    @Suppress("DEPRECATION")
    fun beforeXDays_crossesMonth() {
        val date = LocalDateTime(2025, 6, 3, 12, 0, 0)
        val earlier = date.beforeXDays(5)
        assertEquals(29, earlier.dayOfMonth)
        assertEquals(5, earlier.monthNumber)
    }

    @Test
    fun decodeHtmlEntities_apostrophe_decoded() {
        assertEquals("It's", decodeHtmlEntities("It&apos;s"))
        assertEquals("It's", decodeHtmlEntities("It&#x27;s"))
    }

    @Test
    fun decodeHtmlEntities_quotes_decoded() {
        assertEquals("\"hello\"", decodeHtmlEntities("&quot;hello&quot;"))
        assertEquals("\"hello\"", decodeHtmlEntities("&#x22;hello&#x22;"))
    }

    @Test
    fun decodeHtmlEntities_ampersand_decoded() {
        assertEquals("a & b", decodeHtmlEntities("a &amp; b"))
        assertEquals("a & b", decodeHtmlEntities("a &#x26; b"))
    }

    @Test
    fun decodeHtmlEntities_brackets_decoded() {
        assertEquals("<tag>", decodeHtmlEntities("&lt;tag&gt;"))
        assertEquals("<tag>", decodeHtmlEntities("&#x3C;tag&#x3E;"))
    }

    @Test
    fun decodeHtmlEntities_noEntities_unchanged() {
        assertEquals("plain text", decodeHtmlEntities("plain text"))
    }

    @Test
    fun decodeHtmlEntities_mixedEntities_allDecoded() {
        assertEquals(
            "It's \"awesome\" & cool",
            decodeHtmlEntities("It&apos;s &quot;awesome&quot; &amp; cool"),
        )
    }

    @Test
    fun decodeHtmlEntities_caseInsensitive() {
        assertEquals("It's", decodeHtmlEntities("It&APOS;s"))
        assertEquals("a & b", decodeHtmlEntities("a &AMP; b"))
    }

    @Test
    fun decodeHtmlEntities_emptyString_returnsEmpty() {
        assertEquals("", decodeHtmlEntities(""))
    }
}
