package com.sakayori.music.expect.audio

import uk.co.caprica.vlcj.player.base.Equalizer

actual fun createEqualizerController(audioSessionId: Int): EqualizerController =
    VlcEqualizerController()

class VlcEqualizerController : EqualizerController {
    private var equalizer: Equalizer? = try {
        Equalizer(Equalizer.bandCount())
    } catch (e: Exception) {
        null
    }

    private var enabled = false

    private val defaultFrequencies = listOf(60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000)

    override fun isAvailable(): Boolean = equalizer != null

    override fun getBands(): List<EqualizerBand> {
        val eq = equalizer ?: return emptyList()
        val bandCount = try { Equalizer.bandCount() } catch (_: Exception) { return emptyList() }
        return (0 until bandCount).map { i ->
            val freq = try {
                Equalizer.bandFrequency(i).toInt()
            } catch (_: Exception) {
                defaultFrequencies.getOrElse(i) { 1000 }
            }
            EqualizerBand(
                index = i,
                centerFrequency = freq,
                level = eq.amp(i),
                minLevel = -20f,
                maxLevel = 20f,
            )
        }
    }

    override fun setBandLevel(bandIndex: Int, level: Float) {
        try {
            equalizer?.setAmp(bandIndex, level)
        } catch (_: Exception) {}
    }

    override fun getPresets(): List<EqualizerPreset> {
        val presetCount = try { Equalizer.presetCount() } catch (_: Exception) { return emptyList() }
        val bandCount = try { Equalizer.bandCount() } catch (_: Exception) { return emptyList() }
        return (0 until presetCount).map { i ->
            val presetEq = try { Equalizer(i) } catch (_: Exception) { null }
            val levels = (0 until bandCount).map { b ->
                try { presetEq?.amp(b) ?: 0f } catch (_: Exception) { 0f }
            }
            EqualizerPreset(
                name = try { Equalizer.presetName(i) } catch (_: Exception) { "Preset $i" },
                levels = levels,
            )
        }
    }

    override fun applyPreset(presetIndex: Int) {
        try {
            equalizer = Equalizer(presetIndex)
        } catch (_: Exception) {}
    }

    override fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun isEnabled(): Boolean = enabled

    fun getEqualizer(): Equalizer? = if (enabled) equalizer else null

    override fun release() {
        equalizer = null
    }
}
