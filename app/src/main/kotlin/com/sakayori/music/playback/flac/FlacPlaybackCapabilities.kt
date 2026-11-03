/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback.flac

import android.media.MediaCodecList
import android.os.Build
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSourceException

internal enum class FlacPlaybackCapability {
    SUPPORTED,
    ANDROID_26_REQUIRES_DECODER,
    PROFILE_NOT_SUPPORTED,
    PLATFORM_DECODER_UNAVAILABLE,
}

internal object FlacPlaybackCapabilities {
    // Decoder enumeration is lazy and is called by the loader, not during YouTube playback or
    // service construction. No native extension is bundled in the current application.
    private val decoders by lazy {
        try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
                .mapNotNull { codec ->
                    codec.supportedTypes.firstOrNull { it.equals("audio/flac", ignoreCase = true) }?.let { mime ->
                        runCatching { codec.getCapabilitiesForType(mime).audioCapabilities }.getOrNull()
                    }
                }
        } catch (_: RuntimeException) {
            emptyList()
        }
    }

    fun inspect(info: FlacStreamInfo): FlacPlaybackCapability {
        if (Build.VERSION.SDK_INT < 27) return FlacPlaybackCapability.ANDROID_26_REQUIRES_DECODER
        // CD and standard studio rates, including native 24-bit masters. Higher-rate and
        // multichannel streams remain an explicit capability result, not a silent conversion.
        // This describes source/decoder support, not bit-perfect output through the existing DSP.
        if (info.sampleRate !in STANDARD_RATES || info.bitDepth !in STANDARD_DEPTHS || info.channels !in 1..2) {
            return FlacPlaybackCapability.PROFILE_NOT_SUPPORTED
        }
        return if (decoders.any { it.isSampleRateSupported(info.sampleRate) && it.maxInputChannelCount >= info.channels }) {
            FlacPlaybackCapability.SUPPORTED
        } else {
            FlacPlaybackCapability.PLATFORM_DECODER_UNAVAILABLE
        }
    }

    fun requireSupported(info: FlacStreamInfo) {
        val capability = inspect(info)
        if (capability != FlacPlaybackCapability.SUPPORTED) {
            throw DataSourceException("FLAC playback unavailable: $capability", null, PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED)
        }
    }

    private val STANDARD_RATES = setOf(44_100, 48_000)
    private val STANDARD_DEPTHS = setOf(16, 24)
}
