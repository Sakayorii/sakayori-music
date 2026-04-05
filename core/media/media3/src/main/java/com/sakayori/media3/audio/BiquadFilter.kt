package com.sakayori.media3.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class BiquadFilter {

    enum class FilterType {
        LOW_PASS,
        HIGH_PASS,
    }

    @Volatile private var b0_1 = 1.0
    @Volatile private var b1_1 = 0.0
    @Volatile private var b2_1 = 0.0
    @Volatile private var a1_1 = 0.0
    @Volatile private var a2_1 = 0.0

    @Volatile private var b0_2 = 1.0
    @Volatile private var b1_2 = 0.0
    @Volatile private var b2_2 = 0.0
    @Volatile private var a1_2 = 0.0
    @Volatile private var a2_2 = 0.0

    private var s1_x1L = 0.0; private var s1_x2L = 0.0
    private var s1_y1L = 0.0; private var s1_y2L = 0.0
    private var s1_x1R = 0.0; private var s1_x2R = 0.0
    private var s1_y1R = 0.0; private var s1_y2R = 0.0

    private var s2_x1L = 0.0; private var s2_x2L = 0.0
    private var s2_y1L = 0.0; private var s2_y2L = 0.0
    private var s2_x1R = 0.0; private var s2_x2R = 0.0
    private var s2_y1R = 0.0; private var s2_y2R = 0.0

    fun updateCoefficients(
        cutoffHz: Float,
        sampleRate: Int,
        q: Float = BUTTERWORTH_Q,
        type: FilterType,
    ) {
        val clampedCutoff = cutoffHz.coerceIn(20f, (sampleRate / 2f) - 1f)
        val omega = 2.0 * PI * clampedCutoff / sampleRate
        val sinOmega = sin(omega)
        val cosOmega = cos(omega)
        val alpha = sinOmega / (2.0 * q)

        val rawA0: Double
        when (type) {
            FilterType.LOW_PASS -> {
                val v = (1.0 - cosOmega) / 2.0
                b0_1 = v; b0_2 = v
                b1_1 = 1.0 - cosOmega; b1_2 = b1_1
                b2_1 = v; b2_2 = v
                rawA0 = 1.0 + alpha
                a1_1 = -2.0 * cosOmega; a1_2 = a1_1
                a2_1 = 1.0 - alpha; a2_2 = a2_1
            }

            FilterType.HIGH_PASS -> {
                val v = (1.0 + cosOmega) / 2.0
                b0_1 = v; b0_2 = v
                b1_1 = -(1.0 + cosOmega); b1_2 = b1_1
                b2_1 = v; b2_2 = v
                rawA0 = 1.0 + alpha
                a1_1 = -2.0 * cosOmega; a1_2 = a1_1
                a2_1 = 1.0 - alpha; a2_2 = a2_1
            }
        }

        b0_1 /= rawA0; b1_1 /= rawA0; b2_1 /= rawA0; a1_1 /= rawA0; a2_1 /= rawA0
        b0_2 /= rawA0; b1_2 /= rawA0; b2_2 /= rawA0; a1_2 /= rawA0; a2_2 /= rawA0
    }

    fun processSampleMono(input: Double): Double {
        val mid = b0_1 * input + b1_1 * s1_x1L + b2_1 * s1_x2L - a1_1 * s1_y1L - a2_1 * s1_y2L
        s1_x2L = s1_x1L; s1_x1L = input
        s1_y2L = s1_y1L; s1_y1L = mid

        val output = b0_2 * mid + b1_2 * s2_x1L + b2_2 * s2_x2L - a1_2 * s2_y1L - a2_2 * s2_y2L
        s2_x2L = s2_x1L; s2_x1L = mid
        s2_y2L = s2_y1L; s2_y1L = output

        return output
    }

    fun processStereo(inputLeft: Double, inputRight: Double): Pair<Double, Double> {
        val midL = b0_1 * inputLeft + b1_1 * s1_x1L + b2_1 * s1_x2L - a1_1 * s1_y1L - a2_1 * s1_y2L
        s1_x2L = s1_x1L; s1_x1L = inputLeft
        s1_y2L = s1_y1L; s1_y1L = midL
        val outL = b0_2 * midL + b1_2 * s2_x1L + b2_2 * s2_x2L - a1_2 * s2_y1L - a2_2 * s2_y2L
        s2_x2L = s2_x1L; s2_x1L = midL
        s2_y2L = s2_y1L; s2_y1L = outL

        val midR = b0_1 * inputRight + b1_1 * s1_x1R + b2_1 * s1_x2R - a1_1 * s1_y1R - a2_1 * s1_y2R
        s1_x2R = s1_x1R; s1_x1R = inputRight
        s1_y2R = s1_y1R; s1_y1R = midR
        val outR = b0_2 * midR + b1_2 * s2_x1R + b2_2 * s2_x2R - a1_2 * s2_y1R - a2_2 * s2_y2R
        s2_x2R = s2_x1R; s2_x1R = midR
        s2_y2R = s2_y1R; s2_y1R = outR

        return outL to outR
    }

    fun reset() {
        s1_x1L = 0.0; s1_x2L = 0.0; s1_y1L = 0.0; s1_y2L = 0.0
        s1_x1R = 0.0; s1_x2R = 0.0; s1_y1R = 0.0; s1_y2R = 0.0
        s2_x1L = 0.0; s2_x2L = 0.0; s2_y1L = 0.0; s2_y2L = 0.0
        s2_x1R = 0.0; s2_x2R = 0.0; s2_y1R = 0.0; s2_y2R = 0.0
    }

    companion object {
        const val BUTTERWORTH_Q = 0.707f
    }
}

