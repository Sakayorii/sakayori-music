package com.sakayori.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun WaveformProgressBar(
    progress: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 40,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.2f),
    height: Dp = 16.dp,
) {
    val wavePhase = remember { Animatable(0f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            wavePhase.animateTo(
                targetValue = wavePhase.value + 1000f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 20000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        } else {
            wavePhase.stop()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val barWidth = canvasWidth / (barCount * 2f)
        val gap = barWidth
        val progressX = canvasWidth * progress.coerceIn(0f, 1f)

        for (i in 0 until barCount) {
            val x = i * (barWidth + gap)
            val phase = wavePhase.value * 0.1f
            val waveHeight = if (isPlaying) {
                val wave1 = sin((i * 0.4f + phase) * 1.2f)
                val wave2 = sin((i * 0.7f + phase * 0.8f) * 0.9f)
                val combined = (wave1 * 0.6f + wave2 * 0.4f + 1f) / 2f
                (combined * 0.7f + 0.3f) * canvasHeight
            } else {
                canvasHeight * 0.3f
            }

            val barColor = if (x <= progressX) activeColor else inactiveColor
            val barY = (canvasHeight - waveHeight) / 2f

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, barY),
                size = Size(barWidth, waveHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
            )
        }
    }
}
