package com.sakayori.music.playback

import com.sakayori.music.models.MediaMetadata
import com.sakayori.music.playback.flac.ArchiveFlacSource
import com.sakayori.music.playback.flac.FlacResource
import com.sakayori.music.playback.flac.FlacStreamInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveFlacSourceTest {
    private val target = MediaMetadata(
        id = "video-id", title = "A Track", artists = listOf(MediaMetadata.Artist(null, "An Artist")),
        duration = 120, album = MediaMetadata.Album("album-id", "An Album"),
    )
    private val md5 = "0123456789abcdef0123456789abcdef"

    private fun document(originalFormat: String = "WAVE", seconds: String = "120.2") = Json.parseToJsonElement("""
        {
          "metadata": {"collection": ["netlabels"], "creator": "An Artist", "title": "An Album"},
          "files": [
            {"name": "01. A Track.wav", "source": "original", "format": "$originalFormat"},
            {"name": "01. A Track.flac", "source": "derivative", "format": "Flac",
             "original": "01. A Track.wav", "length": "$seconds", "size": "1000000", "md5": "$md5"}
          ]
        }
    """.trimIndent()).jsonObject

    @Test
    fun `matching track from an original lossless master is eligible`() {
        val matches = ArchiveFlacSource.matchingFiles(target, "an-album", document())
        assertEquals(1, matches.size)
        assertEquals("01. A Track.flac", matches.single().fileName)
    }

    @Test
    fun `FLAC derived from a lossy upload is refused`() {
        assertTrue(ArchiveFlacSource.matchingFiles(target, "an-album", document(originalFormat = "VBR MP3")).isEmpty())
    }

    @Test
    fun `artist album version and duration mismatches stay on normal playback`() {
        assertTrue(ArchiveFlacSource.matchingFiles(target.copy(artists = listOf(MediaMetadata.Artist(null, "Another Artist"))), "an-album", document()).isEmpty())
        assertTrue(ArchiveFlacSource.matchingFiles(target.copy(album = MediaMetadata.Album("other", "Another Album")), "an-album", document()).isEmpty())
        assertTrue(ArchiveFlacSource.matchingFiles(target.copy(title = "A Track (Live)"), "an-album", document()).isEmpty())
        assertTrue(ArchiveFlacSource.matchingFiles(target, "an-album", document(seconds = "125")).isEmpty())
        val extraCreator = Json.parseToJsonElement(document().toString().replace("\"creator\":\"An Artist\"", "\"creator\":[\"An Artist\",\"Another Artist\"]")).jsonObject
        assertTrue(ArchiveFlacSource.matchingFiles(target, "an-album", extraCreator).isEmpty())
    }

    @Test
    fun `different files under the same metadata are ambiguous`() {
        val first = ArchiveFlacSource.ArchiveCandidate("one", "track.flac", 1000, md5)
        val second = first.copy(itemId = "two", md5 = "ffffffffffffffffffffffffffffffff")
        assertNull(ArchiveFlacSource.uniqueCandidate(listOf(first, second)))
        assertNotNull(ArchiveFlacSource.uniqueCandidate(listOf(first, first.copy(itemId = "duplicate"))))
    }

    @Test
    fun `video episodes uploads and incomplete identities are not substituted`() {
        assertTrue(ArchiveFlacSource.matchingFiles(target.copy(isEpisode = true), "an-album", document()).isEmpty())
        assertTrue(ArchiveFlacSource.matchingFiles(target.copy(uploadEntityId = "private"), "an-album", document()).isEmpty())
        assertTrue(ArchiveFlacSource.matchingFiles(target.copy(album = null), "an-album", document()).isEmpty())
        assertTrue(ArchiveFlacSource.matchingFiles(target.copy(musicVideoType = "MUSIC_VIDEO_TYPE_OMV"), "an-album", document()).isEmpty())
    }

    @Test
    fun `persisted public resource keeps identity and safely encodes its file path`() {
        val source = ArchiveFlacSource(PlaybackHttpClient())
        val resource = FlacResource("video-id", ArchiveFlacSource.SOURCE_ID, "an-album/01. A Track.flac",
            "\"original-v1\"", 1_000_000, FlacStreamInfo(44_100, 2, 16, 5_292_000), md5)
        val restored = source.restore(resource.rendition())
        assertEquals(resource, restored)
        assertTrue(source.location(checkNotNull(restored)).url.contains("01.%20A%20Track.flac"))
        assertNull(source.restore(resource.rendition().copy(sourceResourceId = "an-album/../track.flac")))
    }
}
