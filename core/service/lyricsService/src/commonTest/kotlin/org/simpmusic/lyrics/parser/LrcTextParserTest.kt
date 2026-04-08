package org.SakayoriMusic.lyrics.parser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LrcTextParserTest {

    @Test
    fun parseSyncedLyrics_simpleLine_parsesCorrectly() {
        val data = "[00:01.50] Hello world"
        val result = parseSyncedLyrics(data)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertEquals(1, lines.size)
        assertEquals("LINE_SYNCED", result.lyrics?.syncType)
        assertEquals("1500", lines[0].startTimeMs)
        assertEquals("Hello world", lines[0].words)
    }

    @Test
    fun parseSyncedLyrics_multipleLines_allParsed() {
        val data = """
            [00:00.00] First line
            [00:05.50] Second line
            [00:10.00] Third line
        """.trimIndent()
        val result = parseSyncedLyrics(data)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertEquals(3, lines.size)
        assertEquals("0", lines[0].startTimeMs)
        assertEquals("5500", lines[1].startTimeMs)
        assertEquals("10000", lines[2].startTimeMs)
    }

    @Test
    fun parseSyncedLyrics_minutesParsed() {
        val data = "[02:30.00] Mid song"
        val result = parseSyncedLyrics(data)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertEquals("150000", lines[0].startTimeMs)
    }

    @Test
    fun parseSyncedLyrics_invalidLines_skipped() {
        val data = """
            not a valid line
            [00:01.00] Valid line
            another invalid
        """.trimIndent()
        val result = parseSyncedLyrics(data)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertEquals(1, lines.size)
        assertEquals("Valid line", lines[0].words)
    }

    @Test
    fun parseSyncedLyrics_emptyInput_emptyResult() {
        val result = parseSyncedLyrics("")
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertEquals(0, lines.size)
    }

    @Test
    fun parseRichSyncLyrics_basicParsed() {
        val data = "[00:01.500] Hello world"
        val result = parseRichSyncLyrics(data)
        assertEquals("RICH_SYNCED", result.lyrics?.syncType)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertTrue(lines.isNotEmpty())
    }

    @Test
    fun parseRichSyncLyrics_threeDigitMs_parsed() {
        val data = "[00:01.500] Line with ms"
        val result = parseRichSyncLyrics(data)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertEquals("1500", lines[0].startTimeMs)
    }

    @Test
    fun parseRichSyncLyrics_twoDigitCentis_converted() {
        val data = "[00:01.50] Line with cs"
        val result = parseRichSyncLyrics(data)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertEquals("1500", lines[0].startTimeMs)
    }

    @Test
    fun parseRichSyncLyrics_offsetTag_skipped() {
        val data = """
            [offset: -100]
            [00:01.00] First line
        """.trimIndent()
        val result = parseRichSyncLyrics(data)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertEquals(1, lines.size)
        assertEquals("First line", lines[0].words)
    }

    @Test
    fun parseRichSyncLyrics_blankLinesFiltered() {
        val data = """
            [00:01.00] Line A

            [00:02.00] Line B
        """.trimIndent()
        val result = parseRichSyncLyrics(data)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertEquals(2, lines.size)
    }

    @Test
    fun parseUnsyncedLyrics_allLinesIncluded() {
        val data = "Line one\nLine two\nLine three"
        val result = parseUnsyncedLyrics(data)
        assertEquals("UNSYNCED", result.lyrics?.syncType)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertEquals(3, lines.size)
        assertEquals("Line one", lines[0].words)
        assertEquals("0", lines[0].startTimeMs)
    }

    @Test
    fun parseTtmlLyrics_simpleLineParsed() {
        val data = """<p begin="00:00:01.500" end="00:00:03.000">Hello world</p>"""
        val result = parseTtmlLyrics(data)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertEquals(1, lines.size)
        assertEquals("1500", lines[0].startTimeMs)
        assertEquals("3000", lines[0].endTimeMs)
        assertEquals("Hello world", lines[0].words)
        assertEquals("LINE_SYNCED", result.lyrics?.syncType)
    }

    @Test
    fun parseTtmlLyrics_withWordTiming_richSynced() {
        val data = """<p begin="00:00:01.000" end="00:00:05.000"><span begin="00:00:01.000" end="00:00:02.000">Hello</span> <span begin="00:00:02.000" end="00:00:03.000">world</span></p>"""
        val result = parseTtmlLyrics(data)
        assertEquals("RICH_SYNCED", result.lyrics?.syncType)
        val lines = result.lyrics?.lines
        assertNotNull(lines)
        assertTrue(lines[0].words.contains("<00:01.00>"))
    }
}
