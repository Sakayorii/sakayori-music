package com.sakayori.music.playback

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.Source
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedErrorResponseTest {
    private class LargeBody(private val advertisedLength: Long) : ResponseBody() {
        var produced = 0L
        var closed = false
        private val block = ByteArray(8192) { 'x'.code.toByte() }
        private val input = object : Source {
            override fun read(sink: Buffer, byteCount: Long): Long {
                val count = minOf(byteCount, block.size.toLong()).toInt()
                sink.write(block, 0, count)
                produced += count
                return count.toLong()
            }

            override fun timeout() = Timeout.NONE

            override fun close() { closed = true }
        }.buffer()

        override fun contentType() = "text/plain".toMediaType()
        override fun contentLength() = advertisedLength
        override fun source() = input
    }

    private fun response(code: Int, body: ResponseBody): Response = Response.Builder()
        .request(Request.Builder().url("https://example.com/audio").build())
        .protocol(Protocol.HTTP_1_1).code(code).message("fixture")
        .header("Retry-After", "3").body(body).build()

    @Test
    fun `large forbidden responses retain status and bounded diagnostics and close the source`() {
        val body = LargeBody(Long.MAX_VALUE)
        val limited = response(403, body).withBoundedErrorBody()
        assertEquals(403, limited.code)
        assertEquals("3", limited.header("Retry-After"))
        assertEquals(MAX_ERROR_RESPONSE_BYTES, checkNotNull(limited.body).contentLength())
        assertEquals(MAX_ERROR_RESPONSE_BYTES.toInt(), checkNotNull(limited.body).bytes().size)
        assertTrue(body.produced <= MAX_ERROR_RESPONSE_BYTES + 8192)
        assertTrue(body.closed)
    }

    @Test
    fun `unknown-length error streams are bounded too`() {
        val body = LargeBody(-1)
        val limited = response(500, body).withBoundedErrorBody()
        assertEquals(-1L, checkNotNull(limited.body).contentLength())
        assertEquals(MAX_ERROR_RESPONSE_BYTES.toInt(), checkNotNull(limited.body).bytes().size)
        assertTrue(body.closed)
    }

    @Test
    fun `successful audio responses keep the original body and full content`() {
        val bytes = ByteArray(MAX_ERROR_RESPONSE_BYTES.toInt() + 1) { 7 }
        val original = response(206, bytes.toResponseBody("audio/webm".toMediaType()))
        val actual = original.withBoundedErrorBody()
        assertSame(original, actual)
        assertEquals(bytes.size, checkNotNull(actual.body).bytes().size)
    }
}
