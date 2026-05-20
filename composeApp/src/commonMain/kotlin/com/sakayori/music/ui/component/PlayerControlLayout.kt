package com.sakayori.music.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sakayori.domain.mediaservice.handler.ControlState
import com.sakayori.domain.mediaservice.handler.RepeatState
import com.sakayori.music.ui.theme.seed
import com.sakayori.music.ui.theme.transparent
import com.sakayori.music.viewModel.UIEvent

@Composable
fun PlayerControlLayout(
    controllerState: ControlState,
    isSmallSize: Boolean = false,
    onUIEvent: (UIEvent) -> Unit,
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val withHaptic: (UIEvent) -> Unit = { event ->
        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
        onUIEvent(event)
    }
    val mainRowHeight = if (isSmallSize) 56.dp else 80.dp
    val sideIconBox = if (isSmallSize) 36.dp else 44.dp
    val sideIcon = if (isSmallSize) 22.dp else 26.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedPlaybackControls(
            isPlaying = controllerState.isPlaying,
            onPrevious = {
                if (controllerState.isPreviousAvailable) withHaptic(UIEvent.Previous)
            },
            onPlayPause = { withHaptic(UIEvent.PlayPause) },
            onNext = {
                if (controllerState.isNextAvailable) withHaptic(UIEvent.Next)
            },
            height = mainRowHeight,
            playPauseIconSize = if (isSmallSize) 28.dp else 36.dp,
            iconSize = if (isSmallSize) 26.dp else 32.dp,
            colorPlayPause = MaterialTheme.colorScheme.primary,
            tintPlayPauseIcon = MaterialTheme.colorScheme.onPrimary,
            colorSideButtons = MaterialTheme.colorScheme.secondaryContainer,
            tintSideIcons = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(sideIconBox),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(sideIconBox)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (controllerState.isShuffle) seed.copy(alpha = 0.18f) else transparent,
                    )
                    .clickable { withHaptic(UIEvent.Shuffle) },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = controllerState.isShuffle, label = "shuffleTint") { isShuffle ->
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        tint = if (isShuffle) seed else Color.White,
                        contentDescription = null,
                        modifier = Modifier.size(sideIcon),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(sideIconBox)
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (controllerState.repeatState !is RepeatState.None) seed.copy(alpha = 0.18f) else transparent,
                    )
                    .clickable { withHaptic(UIEvent.Repeat) },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = controllerState.repeatState, label = "repeatTint") { rs ->
                    when (rs) {
                        is RepeatState.None -> Icon(
                            imageVector = Icons.Rounded.Repeat,
                            tint = Color.White,
                            contentDescription = null,
                            modifier = Modifier.size(sideIcon),
                        )
                        RepeatState.All -> Icon(
                            imageVector = Icons.Rounded.Repeat,
                            tint = seed,
                            contentDescription = null,
                            modifier = Modifier.size(sideIcon),
                        )
                        RepeatState.One -> Icon(
                            imageVector = Icons.Rounded.RepeatOne,
                            tint = seed,
                            contentDescription = null,
                            modifier = Modifier.size(sideIcon),
                        )
                    }
                }
            }
        }
    }
}
