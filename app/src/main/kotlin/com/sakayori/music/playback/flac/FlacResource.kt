/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback.flac

import com.sakayori.music.playback.AudioRendition
import com.sakayori.music.playback.identityDigest

/**
 * A source adapter must supply a stable resource, exact logical-recording match and strong file
 * revision. These transport facts alone do not prove that its source was never lossy-transcoded.
 * URLs/credentials belong to [FlacLocation], never to persisted rendition identity.
 */
internal data class FlacResource(
    val mediaId: String,
    val sourceId: String,
    val resourceId: String,
    val etag: String,
    val contentLength: Long,
    val streamInfo: FlacStreamInfo,
    val contentHash: String? = null,
    val sourceLocator: String? = null,
) {
    init {
        require(mediaId.isNotBlank() && sourceId.isNotBlank() && resourceId.isNotBlank())
        require(isStrongEtag(etag)) { "FLAC resource requires a strong ETag" }
        require(contentLength >= FlacStreamInfo.HEADER_LENGTH)
        require(streamInfo.sampleRate > 0 && streamInfo.channels in 1..8 && streamInfo.bitDepth in 4..32)
        require(streamInfo.totalSamples in 0..0xfffffffffL)
    }

    fun rendition() = AudioRendition(
        mediaId = mediaId,
        provider = AudioRendition.DIRECT_FLAC,
        resourceId = when {
            sourceLocator != null -> identityDigest(sourceId, sourceLocator, resourceId, contentHash)
            contentHash != null -> identityDigest(sourceId, resourceId, contentHash)
            else -> identityDigest(sourceId, resourceId)
        },
        mimeType = "audio/flac",
        codecs = "flac",
        // Average encoded bitrate is descriptive, never proof of source quality.
        bitrate = if (streamInfo.totalSamples == 0L) 0 else
            (contentLength.toDouble() * 8 * streamInfo.sampleRate / streamInfo.totalSamples).toInt(),
        sampleRate = streamInfo.sampleRate,
        bitDepth = streamInfo.bitDepth,
        channels = streamInfo.channels,
        contentLength = contentLength,
        version = etag,
        sourceId = sourceId,
        sourceResourceId = resourceId,
        totalSamples = streamInfo.totalSamples,
        contentHash = contentHash,
        sourceLocator = sourceLocator,
        clientName = when (sourceId) {
            "internet-archive" -> "Internet Archive"
            "monochrome" -> "Monochrome"
            "hifi-api" -> "HiFi API"
            else -> sourceId
        },
    )
}

internal data class FlacLocation(val url: String, val requestHeaders: Map<String, String> = emptyMap())

internal fun isStrongEtag(etag: String): Boolean =
    etag.length >= 2 && etag.first() == '"' && etag.last() == '"' &&
        etag.substring(1, etag.lastIndex).all { it in '!'..'~' && it != '"' }
