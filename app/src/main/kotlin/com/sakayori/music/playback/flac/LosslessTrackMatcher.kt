/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback.flac

import com.sakayori.music.models.MediaMetadata
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

/** Metadata agreement is a selection guard, not an audio-fingerprint equivalence claim. */
internal data class LosslessTrackCandidate(
    val id: String,
    val title: String,
    val artists: List<String>,
    val durationMs: Long,
    val album: String? = null,
    val isrc: String? = null,
    val explicit: Boolean? = null,
    val version: String? = null,
)

internal fun matchLosslessTrack(target: MediaMetadata, candidates: List<LosslessTrackCandidate>): LosslessTrackCandidate? {
    if (target.title.isBlank() || target.duration <= 0 || target.artists.isEmpty() ||
        target.isEpisode || target.isVideoSong || target.uploadEntityId != null
    ) return null
    val title = normalizedRecordingText(target.title)
    val artists = target.artists.map { normalizedRecordingText(it.name) }.toSet()
    if (title.isBlank() || "" in artists) return null
    val album = target.album?.title?.takeIf { it.isNotBlank() }?.let(::normalizedRecordingText)
    val primaryTargetArtist = normalizedRecordingText(target.artists.first().name)
    val matching = candidates.filter { candidate ->
        val candidateArtists = candidate.artists.map(::normalizedRecordingText).toSet()
        val artistMatches = candidateArtists == artists ||
            (primaryTargetArtist in candidateArtists) ||
            (candidateArtists.any { it in artists })
        candidate.id.isNotBlank() && candidate.durationMs > 0 &&
            normalizedRecordingText(candidate.recordingTitle()) == title &&
            artistMatches &&
            abs(candidate.durationMs - target.duration.toLong() * 1_000) <= 4_000 &&
            (candidate.explicit == null || candidate.explicit == target.explicit) &&
            (album == null || candidate.album.isNullOrBlank() || normalizedRecordingText(candidate.album.orEmpty()) == album)
    }.distinctBy { it.id }
    if (matching.size == 1) return matching.single()
    // Multiple indistinguishable recordings do not become a guessed substitution. An identical
    // nonempty ISRC across the candidates permits equivalent catalogue entries, still subject to
    // the same full title/credits/duration/version checks above.
    val isrcs = matching.map { it.isrc?.trim()?.uppercase(Locale.ROOT) }.distinct()
    return matching.firstOrNull()?.takeIf { isrcs.size == 1 && !isrcs.single().isNullOrBlank() }
}

private val RECORDING_PUNCTUATION = Regex("[\\p{P}\\p{S}]")
private val RECORDING_WHITESPACE = Regex("\\s+")

private fun LosslessTrackCandidate.recordingTitle(): String {
    val tag = version?.trim()?.takeIf { it.isNotEmpty() } ?: return title
    val suffix = "($tag)"
    // Do not silently discard a separate Live/Remaster/Mix field. Only an explicitly tagged
    // target title can match it; avoid duplicating a tag already present in the API's title.
    return if (title.trimEnd().endsWith(suffix, ignoreCase = true)) title else "$title $suffix"
}

private fun normalizedRecordingText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(RECORDING_PUNCTUATION, " ").replace(RECORDING_WHITESPACE, " ").trim()
