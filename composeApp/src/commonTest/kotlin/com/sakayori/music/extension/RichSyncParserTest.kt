package com.sakayori.music.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RichSyncParserTest {

    @Test
    fun parseRichSyncWords_blankInput_returnsNull() {
        assertNull(parseRichSyncWords("", "0", "1000"))
        assertNull(parseRichSyncWords("   ", "0", "1000"))
    }

    @Test
    fun parseRichSyncWords_singleWord_parsesCorrectly() {
        val result = parseRichSyncWords("<00:01.50> hello", "1500", "2000")
        assertNotNull(result)
        assertEquals(1, result.words.size)
        assertEquals("hello", result.words[0].text)
        assertEquals(1500L, result.words[0].startTimeMs)
    }

    @Test
    fun parseRichSyncWords_multipleWords_parsesAll() {
        val result = parseRichSyncWords(
            "<00:00.00> Hello <00:00.50> world <00:01.00> test",
            "0",
            "2000",
        )
        assertNotNull(result)
        assertEquals(3, result.words.size)
        assertEquals("Hello", result.words[0].text)
        assertEquals("world", result.words[1].text)
        assertEquals("test", result.words[2].text)
        assertEquals(0L, result.words[0].startTimeMs)
        assertEquals(500L, result.words[1].startTimeMs)
        assertEquals(1000L, result.words[2].startTimeMs)
    }

    @Test
    fun parseRichSyncWords_threeDigitFraction_parsesAsMs() {
        val result = parseRichSyncWords("<00:01.500> word", "1500", "2000")
        assertNotNull(result)
        assertEquals(1500L, result.words[0].startTimeMs)
    }

    @Test
    fun parseRichSyncWords_twoDigitFraction_parsesAsCentiseconds() {
        val result = parseRichSyncWords("<00:01.50> word", "1500", "2000")
        assertNotNull(result)
        assertEquals(1500L, result.words[0].startTimeMs)
    }

    @Test
    fun parseRichSyncWords_lineTimingsParsed() {
        val result = parseRichSyncWords("<00:01.00> word", "1000", "5000")
        assertNotNull(result)
        assertEquals(1000L, result.lineStartTimeMs)
        assertEquals(5000L, result.lineEndTimeMs)
    }

    @Test
    fun parseRichSyncWords_invalidLineTimings_usesDefaults() {
        val result = parseRichSyncWords("<00:01.00> word", "invalid", "invalid")
        assertNotNull(result)
        assertEquals(0L, result.lineStartTimeMs)
        assertEquals(Long.MAX_VALUE, result.lineEndTimeMs)
    }

    @Test
    fun parseRichSyncWords_noTimestamps_returnsNull() {
        val result = parseRichSyncWords("just plain text", "0", "1000")
        assertNull(result)
    }

    @Test
    fun parseRichSyncWords_emptyTextBetweenTimestamps_skipped() {
        val result = parseRichSyncWords("<00:00.00>   <00:01.00> word", "0", "2000")
        assertNotNull(result)
        assertEquals(1, result.words.size)
        assertEquals("word", result.words[0].text)
    }

    @Test
    fun parseRichSyncWords_minutesParsedCorrectly() {
        val result = parseRichSyncWords("<02:30.00> word", "150000", "200000")
        assertNotNull(result)
        assertEquals(150000L, result.words[0].startTimeMs)
    }

    @Test
    fun parsedRichSyncLine_dataClass_equality() {
        val word = WordTiming("test", 1000L)
        val line1 = ParsedRichSyncLine(listOf(word), 0L, 5000L)
        val line2 = ParsedRichSyncLine(listOf(word), 0L, 5000L)
        assertEquals(line1, line2)
    }
}
