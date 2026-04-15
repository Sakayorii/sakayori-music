package com.sakayori.music.expect.audio

actual fun createEqualizerController(audioSessionId: Int): EqualizerController =
    JvmEqualizerController()

class JvmEqualizerController : EqualizerController {
    private val presetData = listOf(
        EqualizerPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqualizerPreset("Bass Boost", listOf(5f, 4f, 3f, 1f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqualizerPreset("Treble Boost", listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 4f, 5f, 5f)),
        EqualizerPreset("Rock", listOf(4f, 3f, 1f, 0f, -1f, 0f, 2f, 3f, 4f, 4f)),
        EqualizerPreset("Pop", listOf(-1f, 1f, 3f, 4f, 3f, 1f, -1f, -1f, -1f, -1f)),
        EqualizerPreset("Jazz", listOf(3f, 2f, 0f, 1f, -1f, -1f, 0f, 1f, 2f, 3f)),
        EqualizerPreset("Classical", listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)),
        EqualizerPreset("Electronic", listOf(4f, 3f, 1f, 0f, -1f, 1f, 0f, 1f, 3f, 4f)),
    )

    private val defaultFrequencies = listOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)
    private val bandLevels = FloatArray(10) { 0f }
    private var enabled = false

    override fun isAvailable(): Boolean = true

    override fun getBands(): List<EqualizerBand> =
        defaultFrequencies.mapIndexed { i, freq ->
            EqualizerBand(
                index = i,
                centerFrequency = freq,
                level = bandLevels[i],
                minLevel = -20f,
                maxLevel = 20f,
            )
        }

    override fun setBandLevel(bandIndex: Int, level: Float) {
        if (bandIndex in bandLevels.indices) {
            bandLevels[bandIndex] = level
        }
    }

    override fun getPresets(): List<EqualizerPreset> = presetData

    override fun applyPreset(presetIndex: Int) {
        val preset = presetData.getOrNull(presetIndex) ?: return
        preset.levels.forEachIndexed { i, level ->
            if (i in bandLevels.indices) bandLevels[i] = level
        }
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun isEnabled(): Boolean = enabled

    override fun release() {
        bandLevels.fill(0f)
    }
}
