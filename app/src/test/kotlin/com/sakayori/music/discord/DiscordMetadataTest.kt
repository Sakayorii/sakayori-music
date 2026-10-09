package com.sakayori.music.discord

import com.sakayori.music.db.entities.AlbumEntity
import com.sakayori.music.db.entities.Song
import com.sakayori.music.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscordMetadataTest {
    private fun song(storedName: String?, linkedName: String? = null) = Song(
        song = SongEntity(id = "track", title = "Track", albumName = storedName),
        artists = emptyList(),
        album = linkedName?.let {
            AlbumEntity(id = "album", title = it, songCount = 1, duration = 180)
        },
    )

    @Test
    fun albumTemplateUsesTrackAlbumWhenLibraryAlbumIsMissing() {
        val albumName = song("Track Album").discordAlbumName()

        assertEquals("Track Album", albumName)
        assertEquals(
            "From Track Album",
            DiscordTemplateRenderer.render("From {album.name}", "Track", "Artist", albumName),
        )
    }

    @Test
    fun linkedAlbumTakesPrecedence() {
        assertEquals("Library Album", song("Track Album", "Library Album").discordAlbumName())
    }

    @Test
    fun blankLinkedAlbumFallsBackToTrackAlbum() {
        assertEquals("Track Album", song("Track Album", " ").discordAlbumName())
    }

    @Test
    fun missingOrBlankAlbumKeepsUnknownAlbumFallback() {
        for (storedName in listOf(null, "", " ")) {
            val albumName = song(storedName).discordAlbumName()
            assertNull(albumName)
            assertEquals(
                DiscordDefaults.UNKNOWN_ALBUM,
                DiscordTemplateRenderer.render("{album.name}", "Track", "Artist", albumName),
            )
        }
    }
}
