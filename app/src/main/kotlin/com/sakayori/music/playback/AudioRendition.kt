/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import com.sakayori.music.db.entities.FormatEntity
import com.sakayori.music.utils.YTPlayerUtils
import kotlinx.serialization.Serializable
import java.security.MessageDigest
import java.util.UUID

@Serializable
enum class LegacyAudioCache { PLAYER, DOWNLOAD }

/** Identity of encoded bytes, separate from a queue item's stable song ID. */
@Serializable
data class AudioRendition(
    val mediaId: String,
    val provider: String,
    val resourceId: String,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int? = null,
    val bitDepth: Int? = null,
    val contentLength: Long? = null,
    val version: String? = null,
    val itag: Int? = null,
    val audioTrackId: String? = null,
    val loudnessDb: Double? = null,
    val perceptualLoudnessDb: Double? = null,
    val clientName: String? = null,
    // Only existing cache files may keep the historical song-ID key.
    val legacy: Boolean = false,
    val legacyCache: LegacyAudioCache? = null,
    val channels: Int? = null,
    val sourceId: String? = null,
    val sourceResourceId: String? = null,
    val totalSamples: Long? = null,
    val contentHash: String? = null,
) {
    val cacheKey: String
        get() {
            if (legacy) return mediaId
            val parts = arrayOf(provider, resourceId, mimeType, codecs, sampleRate?.toString(), bitDepth?.toString(),
                contentLength?.toString(), version, itag?.toString(), audioTrackId)
            return "audio:v1:" + if (provider == DIRECT_FLAC) identityDigest(*parts, channels?.toString()) else identityDigest(*parts)
        }

    internal fun requiredYouTubeFormat(): YTPlayerUtils.RequiredStreamFormat {
        require(provider == YOUTUBE && itag != null && !legacy)
        return YTPlayerUtils.RequiredStreamFormat(
            itag, mimeType, codecs, contentLength, version?.toLongOrNull(), audioTrackId, sampleRate,
        )
    }

    internal val canRenewYouTube: Boolean
        get() = provider == YOUTUBE && !legacy && itag != null && version?.toLongOrNull() != null

    internal fun toFormatEntity(playbackUrl: String? = null, knownLength: Long? = contentLength): FormatEntity? {
        // Unknown legacy formats and non-YouTube sources must not invent a YouTube itag.
        val youtubeItag = itag?.takeIf { provider == YOUTUBE && mimeType.isNotBlank() } ?: return null
        return FormatEntity(
            id = mediaId,
            itag = youtubeItag,
            mimeType = mimeType,
            codecs = codecs,
            bitrate = bitrate,
            sampleRate = sampleRate,
            contentLength = knownLength ?: -1L,
            loudnessDb = loudnessDb,
            perceptualLoudnessDb = perceptualLoudnessDb,
            playbackUrl = playbackUrl,
        )
    }

    companion object {
        const val YOUTUBE = "youtube"
        const val DIRECT_FLAC = "direct-flac"
        const val DIRECT_FLAC_PROFILE = "DIRECT_FLAC"

        fun fromYouTube(mediaId: String, data: YTPlayerUtils.PlaybackData): AudioRendition {
            val format = data.format
            return AudioRendition(
                mediaId = mediaId,
                provider = YOUTUBE,
                resourceId = mediaId,
                mimeType = format.mimeType.substringBefore(';').trim(),
                codecs = codecsOf(format.mimeType),
                bitrate = format.bitrate,
                sampleRate = format.audioSampleRate,
                contentLength = format.contentLength?.takeIf { it > 0 },
                // An unversioned response is usable for this lease, but cannot prove that a
                // later URL serves the same bytes. Give later selections a different key.
                version = format.lastModified?.toString() ?: "lease:${UUID.randomUUID()}",
                itag = format.itag,
                audioTrackId = format.audioTrack?.id,
                loudnessDb = data.audioConfig?.loudnessDb,
                perceptualLoudnessDb = data.audioConfig?.perceptualLoudnessDb,
                clientName = data.streamClient,
            )
        }

        internal fun legacy(mediaId: String, format: FormatEntity): AudioRendition = AudioRendition(
            mediaId = mediaId,
            provider = YOUTUBE,
            resourceId = mediaId,
            mimeType = format.mimeType,
            codecs = format.codecs,
            bitrate = format.bitrate,
            sampleRate = format.sampleRate,
            contentLength = format.contentLength.takeIf { it > 0 },
            itag = format.itag,
            loudnessDb = format.loudnessDb,
            perceptualLoudnessDb = format.perceptualLoudnessDb,
            legacy = true,
        )

        internal fun codecsOf(mimeType: String): String = mimeType
            .substringAfter("codecs=", "").substringBefore(';').trim().removeSurrounding("\"")
    }
}

/** Length prefixes prevent delimiter collisions without storing signed URLs or credentials in keys. */
internal fun identityDigest(vararg parts: String?): String {
    val input = parts.joinToString("") { part -> if (part == null) "-1:" else "${part.length}:$part" }
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    val digits = "0123456789abcdef"
    return buildString(digest.size * 2) {
        digest.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(digits[value ushr 4])
            append(digits[value and 15])
        }
    }
}
