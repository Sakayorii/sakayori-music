package com.sakayori.domain.extension

import kotlin.test.Test
import kotlin.test.assertEquals

class AllExtTest {

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
