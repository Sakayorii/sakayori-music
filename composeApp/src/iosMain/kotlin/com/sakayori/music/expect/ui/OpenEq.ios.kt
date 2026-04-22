package com.sakayori.music.expect.ui

import androidx.compose.runtime.Composable

private object NoopOpenEqLauncher : OpenEqLauncher {
    override fun launch() {}
}

@Composable
actual fun openEqResult(audioSessionId: Int): OpenEqLauncher = NoopOpenEqLauncher
