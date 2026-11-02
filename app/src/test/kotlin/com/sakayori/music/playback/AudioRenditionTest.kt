package com.sakayori.music.playback

import com.sakayori.innertube.models.response.PlayerResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRenditionTest {
    private val original = AudioRendition(
        mediaId = "song",
        provider = AudioRendition.YOUTUBE,
        resourceId = "song",
        mimeType = "audio/webm",
        codecs = "opus",
        bitrate = 160_000,
        sampleRate = 48_000,
        contentLength = 12_345,
        version = "1000",
        itag = 251,
    )

    @Test
    fun `quality resource revision and audio language cannot share byte keys`() {
        assertNotEquals(original.cacheKey, original.copy(itag = 140, mimeType = "audio/mp4", codecs = "mp4a.40.2").cacheKey)
        assertNotEquals(original.cacheKey, original.copy(version = "1001").cacheKey)
        assertNotEquals(original.cacheKey, original.copy(contentLength = 54_321).cacheKey)
        assertNotEquals(original.cacheKey, original.copy(audioTrackId = "dubbed").cacheKey)
        assertNotEquals(original.cacheKey, original.copy(provider = "another-source").cacheKey)
    }

    @Test
    fun `metadata and client changes do not rename the same encoded bytes`() {
        assertEquals(original.cacheKey, original.copy(clientName = "WEB_REMIX", loudnessDb = -8.0).cacheKey)
        assertTrue(original.cacheKey.startsWith("audio:v1:"))
        assertEquals("song", original.copy(legacy = true).cacheKey)
    }

    @Test
    fun `key encoding separates null empty and delimiter-containing fields`() {
        assertNotEquals(identityDigest(null, "x"), identityDigest("", "x"))
        assertNotEquals(identityDigest("a:b", "c"), identityDigest("a", "b:c"))
    }

    @Test
    fun `direct sources do not invent a YouTube itag`() {
        assertNull(original.copy(provider = AudioRendition.DIRECT_FLAC, itag = null).toFormatEntity())
    }

    @Test
    fun `a pinned download accepts only the same upstream encoding`() {
        val format = PlayerResponse.StreamingData.Format(
            itag = 251, url = "https://example.com/audio", mimeType = "audio/webm; codecs=\"opus\"",
            bitrate = 160_000, width = null, height = null, contentLength = 12_345,
            quality = "tiny", fps = null, qualityLabel = null, averageBitrate = null,
            audioQuality = "AUDIO_QUALITY_MEDIUM", approxDurationMs = "1000",
            audioSampleRate = 48_000, audioChannels = 2, loudnessDb = null, lastModified = 1000,
            signatureCipher = null, cipher = null, audioTrack = null,
        )
        val required = original.requiredYouTubeFormat()
        assertTrue(required.matches(format))
        assertEquals(false, required.matches(format.copy(lastModified = 1001)))
        assertEquals(false, required.matches(format.copy(itag = 140)))
        assertEquals(false, required.matches(format.copy(contentLength = 54_321)))
    }
}
