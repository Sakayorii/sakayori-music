/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback.flac

import java.io.IOException

/** Check the actual response, before an HTTP datasource can skip or cache bytes from it. */
internal fun validateFlacResponse(
    resource: FlacResource,
    position: Long,
    length: Long,
    responseCode: Int,
    etag: String?,
    contentRange: String?,
    contentLength: Long?,
    contentEncoding: String?,
) {
    if (etag != resource.etag) throw IOException("FLAC resource revision changed or was not supplied")
    if (contentEncoding != null && !contentEncoding.equals("identity", ignoreCase = true)) {
        throw IOException("Encoded HTTP bodies cannot resume FLAC byte ranges")
    }
    when (responseCode) {
        200 -> {
            if (position != 0L) throw IOException("FLAC server ignored the requested byte range")
            if (contentLength != null && contentLength != resource.contentLength) throw IOException("FLAC file length changed")
        }
        206 -> {
            val match = contentRange?.trim()?.let(CONTENT_RANGE::matchEntire)
                ?: throw IOException("Missing or invalid FLAC Content-Range")
            val start = match.groupValues[1].toLongOrNull()
            val end = match.groupValues[2].toLongOrNull()
            val total = match.groupValues[3].toLongOrNull()
            if (start != position || end == null || end != position + length - 1 || total != resource.contentLength ||
                (contentLength != null && contentLength != length)
            ) throw IOException("FLAC byte range does not match the pinned resource")
        }
        else -> throw IOException("Unexpected FLAC response status: $responseCode")
    }
}

private val CONTENT_RANGE = Regex("bytes (\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
