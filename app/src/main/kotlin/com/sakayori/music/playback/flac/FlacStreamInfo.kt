/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback.flac

import java.io.IOException

/** Facts from native FLAC's mandatory STREAMINFO block, not from a filename or decoded PCM. */
internal data class FlacStreamInfo(
    val sampleRate: Int,
    val channels: Int,
    val bitDepth: Int,
    val totalSamples: Long,
) {
    init {
        require(sampleRate in 1..655_350 && channels in 1..8 && bitDepth in 4..32 && totalSamples in 0..0xfffffffffL)
    }

    val durationUs: Long?
        get() = totalSamples.takeIf { it > 0 }?.let { it * 1_000_000L / sampleRate }

    companion object {
        const val HEADER_LENGTH = 42 // fLaC + metadata-block header + 34-byte STREAMINFO.

        fun parse(header: ByteArray): FlacStreamInfo {
            if (header.size < HEADER_LENGTH) throw IOException("Truncated FLAC STREAMINFO")
            if (header[0] != 'f'.code.toByte() || header[1] != 'L'.code.toByte() ||
                header[2] != 'a'.code.toByte() || header[3] != 'C'.code.toByte()
            ) throw IOException("Expected native FLAC audio")
            if ((header[4].toInt() and 0x7f) != 0 || unsigned(header, 5, 3) != 34L) {
                throw IOException("FLAC must begin with a 34-byte STREAMINFO block")
            }
            val minBlock = unsigned(header, 8, 2)
            val maxBlock = unsigned(header, 10, 2)
            val minFrame = unsigned(header, 12, 3)
            val maxFrame = unsigned(header, 15, 3)
            val packed = unsigned(header, 18, 8)
            val sampleRate = (packed ushr 44).toInt()
            val channels = ((packed ushr 41) and 7).toInt() + 1
            val bitDepth = ((packed ushr 36) and 31).toInt() + 1
            val samples = packed and 0xfffffffffL
            if (minBlock < 16 || maxBlock < minBlock || sampleRate !in 1..655_350 || bitDepth !in 4..32 ||
                (minFrame != 0L && maxFrame != 0L && minFrame > maxFrame)
            ) throw IOException("Invalid FLAC STREAMINFO values")
            return FlacStreamInfo(sampleRate, channels, bitDepth, samples)
        }

        private fun unsigned(bytes: ByteArray, offset: Int, length: Int): Long {
            var value = 0L
            for (index in offset until offset + length) value = (value shl 8) or (bytes[index].toLong() and 0xff)
            return value
        }
    }
}
