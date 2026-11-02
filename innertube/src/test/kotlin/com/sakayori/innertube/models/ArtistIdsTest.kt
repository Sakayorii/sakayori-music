package com.sakayori.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ArtistIdsTest {
    private fun song(id: String, artist: Artist) = SongItem(
        id = id,
        title = "Title $id",
        artists = listOf(artist),
        duration = 217,
        thumbnail = "https://example.com/$id.jpg",
        libraryAddToken = "add-$id",
    )

    @Test
    fun `enrichment preserves existing IDs playback fields and item order`() {
        val known = song("first", Artist("Same name", "UC-known"))
        val missing = song("second", Artist("Same name", null))
        val original: List<YTItem> = listOf(known, missing, known)
        val result = original.withResolvedArtistIds(mapOf("Same name" to "UC-resolved"))

        assertSame(known, result[0])
        assertSame(known, result[2])
        assertEquals(missing.copy(artists = listOf(Artist("Same name", "UC-resolved"))), result[1])
        assertEquals(listOf("first", "second", "first"), result.map { it.id })
    }

    @Test
    fun `irrelevant or empty enrichment retains the original list instance`() {
        val original: List<YTItem> = listOf(song("first", Artist("Artist", null)))
        assertSame(original, original.withResolvedArtistIds(emptyMap()))
        assertSame(original, original.withResolvedArtistIds(mapOf("Someone else" to "UC-other")))
    }

    @Test
    fun `only missing nonblank names are submitted once across sections`() {
        val items: List<YTItem> = listOf(
            song("a", Artist("Artist", null)),
            song("b", Artist("Artist", null)),
            song("c", Artist("Known", "UC-known")),
            song("d", Artist("  ", null)),
        )
        assertEquals(setOf("Artist"), items.missingArtistNames())
    }
}
