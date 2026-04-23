package com.sakayori.music.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.dp

actual class PlatformBackdrop

actual fun Modifier.layerBackdrop(
    backdrop: PlatformBackdrop,
): Modifier = this.blur(24.dp)

actual fun Modifier.drawBackdropCustomShape(
    backdrop: PlatformBackdrop,
    layer: GraphicsLayer,
    luminanceAnimation: Float,
    shape: Shape,
): Modifier = this.clip(shape).blur(16.dp)

@Composable
actual fun rememberBackdrop(): PlatformBackdrop = remember { PlatformBackdrop() }
