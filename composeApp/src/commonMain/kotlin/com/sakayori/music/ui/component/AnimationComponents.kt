package com.sakayori.music.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sakayori.music.ui.theme.md_theme_dark_background
import kotlinx.coroutines.delay

@Composable
fun InfiniteBorderAnimationView(
    isAnimated: Boolean = false,
    brush: Brush = Brush.sweepGradient(listOf(Color.Gray, Color.White)),
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    contentPadding: Dp = 0.dp,
    borderWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    oneCircleDurationMillis: Int = 3000,
    content: @Composable () -> Unit,
) {
    val degrees = remember { Animatable(90f) }
    LaunchedEffect(isAnimated, oneCircleDurationMillis) {
        if (isAnimated) {
            degrees.animateTo(
                targetValue = 450f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = oneCircleDurationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        } else {
            degrees.stop()
        }
    }
    val scaleAnimationValue by animateFloatAsState(
        if (isAnimated) 1f else 0f,
        tween(800),
    )
    Surface(
        modifier =
            Modifier
                .clip(
                    shape,
                ).padding(borderWidth)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }.drawBehind {
                    if (scaleAnimationValue > 0f) {
                        scale(scale = scaleAnimationValue) {
                            rotate(degrees = degrees.value) {
                                drawCircle(
                                    brush = brush,
                                    radius = size.width,
                                    blendMode = BlendMode.SrcIn,
                                )
                            }
                        }
                    }
                }.animateContentSize(),
        color = backgroundColor,
        shape = shape,
    ) {
        Box(
            modifier =
                Modifier
                    .background(
                        color = if (isAnimated) md_theme_dark_background else backgroundColor,
                    ).padding(
                        contentPadding,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
fun LimitedBorderAnimationView(
    isAnimated: Boolean = false,
    brush: Brush = Brush.sweepGradient(listOf(Color.Gray, Color.White)),
    backgroundColor: Color = MaterialTheme.colorScheme.background,
    contentPadding: Dp = 0.dp,
    borderWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(12.dp),
    oneCircleDurationMillis: Int = 3000,
    interactionNumber: Int = 1,
    content: @Composable () -> Unit,
) {
    var shouldAnimate by rememberSaveable {
        mutableStateOf(false)
    }
    val scaleAnimationValue by animateFloatAsState(
        if (shouldAnimate) 1f else 0f,
        tween(800),
    )

    LaunchedEffect(true) {
        if (isAnimated) {
            shouldAnimate = true
            delay(interactionNumber * oneCircleDurationMillis.toLong())
            shouldAnimate = false
        }
    }

    val degrees = remember { Animatable(0f) }
    LaunchedEffect(shouldAnimate, oneCircleDurationMillis) {
        if (shouldAnimate) {
            degrees.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = oneCircleDurationMillis, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            )
        } else {
            degrees.stop()
        }
    }
    Surface(
        modifier =
            Modifier
                .clip(
                    shape,
                ).padding(borderWidth)
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                }.drawBehind {
                    if (shouldAnimate) {
                        scale(
                            scale = scaleAnimationValue,
                        ) {
                            rotate(degrees = degrees.value) {
                                drawCircle(
                                    brush = brush,
                                    radius = size.width,
                                    blendMode = BlendMode.SrcIn,
                                )
                            }
                        }
                    }
                },
        shape = shape,
    ) {
        Box(
            modifier =
                Modifier
                    .background(
                        color = backgroundColor,
                    ).padding(
                        contentPadding,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}
