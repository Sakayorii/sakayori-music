package com.sakayori.music.expect.audio

private object NoopEqualizerController : EqualizerController {
    override fun isAvailable(): Boolean = false
    override fun getBands(): List<EqualizerBand> = emptyList()
    override fun setBandLevel(bandIndex: Int, level: Float) {}
    override fun getPresets(): List<EqualizerPreset> = emptyList()
    override fun applyPreset(presetIndex: Int) {}
    override fun setEnabled(enabled: Boolean) {}
    override fun isEnabled(): Boolean = false
    override fun release() {}
}

actual fun createEqualizerController(audioSessionId: Int): EqualizerController = NoopEqualizerController
