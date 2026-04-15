package com.sakayori.music.expect.audio

data class EqualizerBand(
    val index: Int,
    val centerFrequency: Int,
    val level: Float,
    val minLevel: Float,
    val maxLevel: Float,
)

data class EqualizerPreset(
    val name: String,
    val levels: List<Float>,
)

interface EqualizerController {
    fun isAvailable(): Boolean
    fun getBands(): List<EqualizerBand>
    fun setBandLevel(bandIndex: Int, level: Float)
    fun getPresets(): List<EqualizerPreset>
    fun applyPreset(presetIndex: Int)
    fun setEnabled(enabled: Boolean)
    fun isEnabled(): Boolean
    fun release()
}

expect fun createEqualizerController(audioSessionId: Int): EqualizerController
