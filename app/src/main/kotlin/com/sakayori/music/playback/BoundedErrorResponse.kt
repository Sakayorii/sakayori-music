/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.buffer

internal const val MAX_ERROR_RESPONSE_BYTES = 64 * 1024L

/**
 * Media3 1.10.1 reads the whole error body before starting transfer notifications. Limit that
 * diagnostic path separately from audio/preload reads, including unknown-length HTTP bodies.
 * Status, headers and a useful body prefix remain available to normal recovery handlers.
 */
internal fun Response.withBoundedErrorBody(): Response {
    val original = body ?: return this
    if (isSuccessful) return this
    val limited = object : ResponseBody() {
        private val input = object : ForwardingSource(original.source()) {
            private var remaining = MAX_ERROR_RESPONSE_BYTES

            override fun read(sink: Buffer, byteCount: Long): Long {
                require(byteCount >= 0)
                if (byteCount == 0L) return 0L
                if (remaining == 0L) return -1L
                return super.read(sink, minOf(byteCount, remaining)).also {
                    if (it > 0) remaining -= it
                }
            }
        }.buffer()

        override fun contentType() = original.contentType()

        override fun contentLength(): Long = original.contentLength().let {
            if (it < 0) -1 else minOf(it, MAX_ERROR_RESPONSE_BYTES)
        }

        override fun source() = input
    }
    return newBuilder().body(limited).build()
}
