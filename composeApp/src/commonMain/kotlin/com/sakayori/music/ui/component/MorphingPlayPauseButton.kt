package com.sakayori.music.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun MorphingPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerPlaying: Dp = 22.dp,
    cornerPaused: Dp = 14.dp,
    iconSize: Dp = 24.dp,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = MaterialTheme.colorScheme.onPrimary,
    haptic: Boolean = true,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val corner by animateDpAsState(
        targetValue = if (!isPlaying) cornerPlaying else cornerPaused,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "morphCorner",
    )
    val shape = remember(corner) { smoothCornerShape(corner = corner, smoothness = 60) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .clickable {
                if (haptic) hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = isPlaying,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
            label = "morphIcon",
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
