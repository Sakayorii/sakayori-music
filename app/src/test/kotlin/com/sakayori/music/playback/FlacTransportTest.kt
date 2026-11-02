package com.sakayori.music.playback

import com.sakayori.music.playback.flac.FlacResource
import com.sakayori.music.playback.flac.FlacStreamInfo
import com.sakayori.music.playback.flac.isStrongEtag
import com.sakayori.music.playback.flac.validateFlacResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class FlacTransportTest {
    private val info = FlacStreamInfo(44_100, 2, 16, 7_938_000)
    private val resource = FlacResource("song", "source", "recording", "\"original-v1\"", 1_000, info)

    private fun header(sampleRate: Int = 44_100, channels: Int = 2, bitDepth: Int = 16, samples: Long = 7_938_000): ByteArray {
        val bytes = ByteArray(42)
        "fLaC".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        bytes[4] = 0x80.toByte()
        bytes[7] = 34
        bytes[8] = 0x10
        bytes[10] = 0x10
        val packed = (sampleRate.toLong() shl 44) or ((channels - 1).toLong() shl 41) or
            ((bitDepth - 1).toLong() shl 36) or samples
        for (index in 0..7) bytes[18 + index] = (packed ushr (56 - index * 8)).toByte()
        return bytes
    }

    @Test
    fun `STREAMINFO yields source rate depth channels and duration`() {
        val parsed = FlacStreamInfo.parse(header())
        assertEquals(info, parsed)
        assertEquals(180_000_000L, parsed.durationUs)
    }

    @Test
    fun `a filename or MIME label cannot replace the FLAC signature`() {
        assertThrows(IOException::class.java) { FlacStreamInfo.parse(ByteArray(42)) }
    }

    @Test
    fun `truncated STREAMINFO is rejected`() {
        assertThrows(IOException::class.java) { FlacStreamInfo.parse(header().copyOf(41)) }
    }

    @Test
    fun `the first block must be STREAMINFO with its mandated length`() {
        assertThrows(IOException::class.java) { FlacStreamInfo.parse(header().also { it[4] = 1 }) }
        assertThrows(IOException::class.java) { FlacStreamInfo.parse(header().also { it[7] = 33 }) }
    }

    @Test
    fun `invalid sample fields are rejected and zero total samples stays unknown`() {
        assertThrows(IOException::class.java) { FlacStreamInfo.parse(header(sampleRate = 0)) }
        assertThrows(IOException::class.java) { FlacStreamInfo.parse(header(bitDepth = 1)) }
        assertNull(FlacStreamInfo.parse(header(samples = 0)).durationUs)
    }

    @Test
    fun `partial response must describe the exact requested resource range`() {
        validateFlacResponse(resource, 100, 200, 206, resource.etag, "bytes 100-299/1000", 200, null)
        assertThrows(IOException::class.java) {
            validateFlacResponse(resource, 100, 200, 206, resource.etag, "bytes 0-199/1000", 200, null)
        }
        assertThrows(IOException::class.java) {
            validateFlacResponse(resource, 100, 200, 206, resource.etag, "bytes 100-299/2000", 200, null)
        }
    }

    @Test
    fun `a server ignoring Range is accepted only at the beginning`() {
        validateFlacResponse(resource, 0, 100, 200, resource.etag, null, 1000, null)
        assertThrows(IOException::class.java) {
            validateFlacResponse(resource, 100, 200, 200, resource.etag, null, 1000, null)
        }
    }

    @Test
    fun `missing or changed validators cannot append bytes to an existing file`() {
        assertThrows(IOException::class.java) {
            validateFlacResponse(resource, 0, 1000, 200, null, null, 1000, null)
        }
        assertThrows(IOException::class.java) {
            validateFlacResponse(resource, 0, 1000, 200, "\"replacement\"", null, 1000, null)
        }
    }

    @Test
    fun `HTTP content encoding cannot alter the meaning of cached offsets`() {
        assertThrows(IOException::class.java) {
            validateFlacResponse(resource, 0, 1000, 200, resource.etag, null, 1000, "gzip")
        }
    }

    @Test
    fun `changed FLAC revisions and sources get distinct byte keys without itags`() {
        assertNotEquals(resource.rendition().cacheKey, resource.copy(etag = "\"original-v2\"").rendition().cacheKey)
        assertNotEquals(resource.rendition().cacheKey, resource.copy(sourceId = "other").rendition().cacheKey)
        assertNull(resource.rendition().toFormatEntity())
        assertEquals(16, resource.rendition().bitDepth)
    }

    @Test
    fun `weak and malformed ETags are not file revision identities`() {
        assertTrue(isStrongEtag("\"original-v1\""))
        assertFalse(isStrongEtag("W/\"original-v1\""))
        assertFalse(isStrongEtag("original-v1"))
        assertFalse(isStrongEtag("\"a\r\nb\""))
    }
}
