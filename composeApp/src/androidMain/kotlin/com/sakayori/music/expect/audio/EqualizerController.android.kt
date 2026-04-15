package com.sakayori.music.expect.audio

import android.media.audiofx.Equalizer

actual fun createEqualizerController(audioSessionId: Int): EqualizerController =
    AndroidEqualizerController(audioSessionId)

class AndroidEqualizerController(audioSessionId: Int) : EqualizerController {
    private var equalizer: Equalizer? = try {
        Equalizer(0, audioSessionId)
    } catch (e: Exception) {
        null
    }

    override fun isAvailable(): Boolean = equalizer != null

    override fun getBands(): List<EqualizerBand> {
        val eq = equalizer ?: return emptyList()
        val numberOfBands = eq.numberOfBands.toInt()
        val range = eq.bandLevelRange
        val minLevel = range[0] / 100f
        val maxLevel = range[1] / 100f
        return (0 until numberOfBands).map { i ->
            EqualizerBand(
                index = i,
                centerFrequency = eq.getCenterFreq(i.toShort()) / 1000,
                level = eq.getBandLevel(i.toShort()) / 100f,
                minLevel = minLevel,
                maxLevel = maxLevel,
            )
        }
    }

    override fun setBandLevel(bandIndex: Int, level: Float) {
        try {
            equalizer?.setBandLevel(bandIndex.toShort(), (level * 100).toInt().toShort())
        } catch (_: Exception) {}
    }

    override fun getPresets(): List<EqualizerPreset> {
        val eq = equalizer ?: return emptyList()
        val numberOfPresets = eq.numberOfPresets.toInt()
        val numberOfBands = eq.numberOfBands.toInt()
        return (0 until numberOfPresets).map { i ->
            eq.usePreset(i.toShort())
            val levels = (0 until numberOfBands).map { b ->
                eq.getBandLevel(b.toShort()) / 100f
            }
            EqualizerPreset(
                name = eq.getPresetName(i.toShort()),
                levels = levels,
            )
        }
    }

    override fun applyPreset(presetIndex: Int) {
        try {
            equalizer?.usePreset(presetIndex.toShort())
        } catch (_: Exception) {}
    }

    override fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
        } catch (_: Exception) {}
    }

    override fun isEnabled(): Boolean = try {
        equalizer?.enabled ?: false
    } catch (_: Exception) {
        false
    }

    override fun release() {
        try {
            equalizer?.release()
            equalizer = null
        } catch (_: Exception) {}
    }
}
