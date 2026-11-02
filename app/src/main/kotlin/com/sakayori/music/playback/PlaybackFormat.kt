/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import com.sakayori.music.db.entities.FormatEntity

/** Presentation/normalization facts without pretending every provider has a YouTube itag. */
data class PlaybackFormat(
    val id: String,
    val itag: Int?,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long?,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double?,
    val bitDepth: Int? = null,
)

fun FormatEntity.toPlaybackFormat() = PlaybackFormat(id, itag, mimeType, codecs, bitrate, sampleRate,
    contentLength.takeIf { it > 0 }, loudnessDb, perceptualLoudnessDb)

fun AudioRendition.toPlaybackFormat() = PlaybackFormat(mediaId, itag, mimeType, codecs, bitrate, sampleRate,
    contentLength?.takeIf { it > 0 }, loudnessDb, perceptualLoudnessDb, bitDepth)
