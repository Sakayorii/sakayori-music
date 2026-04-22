package com.sakayori.music.ui.screen.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect

@Composable
actual fun FullScreenRotationImmersive(
    onLaunch: () -> Unit,
    onDispose: () -> Unit,
) {
    DisposableEffect(Unit) {
        onLaunch()
        onDispose { onDispose() }
    }
}
