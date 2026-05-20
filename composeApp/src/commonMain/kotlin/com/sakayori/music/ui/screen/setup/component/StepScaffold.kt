package com.sakayori.music.ui.screen.setup.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakayori.music.ui.component.smoothCornerShape

@Composable
fun StepScaffold(
    stepOrder: Int,
    totalSteps: Int,
    title: String,
    subtitle: String?,
    canGoBack: Boolean,
    primaryButtonText: String,
    onPrimary: () -> Unit,
    onBack: () -> Unit,
    isPrimaryEnabled: Boolean = true,
    secondaryButtonText: String? = null,
    onSecondary: (() -> Unit)? = null,
    backButtonText: String = "Back",
    content: @Composable () -> Unit,
) {
    val progress = remember(stepOrder, totalSteps) {
        ((stepOrder + 1).toFloat() / (totalSteps + 1).toFloat()).coerceIn(0f, 1f)
    }
    val isLastStep = stepOrder >= totalSteps - 1

    val morphSpec = tween<Float>(durationMillis = 600, easing = FastOutSlowInEasing)
    val rotationSpec = tween<Float>(durationMillis = 900, easing = FastOutSlowInEasing)

    val targetShapeValues = when (stepOrder % 3) {
        0 -> listOf(50f, 50f, 50f, 50f)
        1 -> listOf(26f, 26f, 26f, 26f)
        else -> listOf(18f, 50f, 18f, 50f)
    }
    val animatedTopStart by animateFloatAsState(targetShapeValues[0], morphSpec, label = "fabTL")
    val animatedTopEnd by animateFloatAsState(targetShapeValues[1], morphSpec, label = "fabTR")
    val animatedBottomStart by animateFloatAsState(targetShapeValues[2], morphSpec, label = "fabBL")
    val animatedBottomEnd by animateFloatAsState(targetShapeValues[3], morphSpec, label = "fabBR")
    val animatedRotation by animateFloatAsState(
        targetValue = stepOrder * 360f,
        animationSpec = rotationSpec,
        label = "fabRotation",
    )

    val fabShape = smoothCornerShape(
        topStart = animatedTopStart.toInt().dp,
        topEnd = animatedTopEnd.toInt().dp,
        bottomStart = animatedBottomStart.toInt().dp,
        bottomEnd = animatedBottomEnd.toInt().dp,
        smoothness = 60,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth(),
        ) {
            content()
        }
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (canGoBack) {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            backButtonText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                AnimatedContent(
                    targetState = stepOrder,
                    transitionSpec = {
                        if (targetState > initialState) {
                            (slideInVertically { h -> h } + fadeIn())
                                .togetherWith(slideOutVertically { h -> -h } + fadeOut())
                        } else {
                            (slideInVertically { h -> -h } + fadeIn())
                                .togetherWith(slideOutVertically { h -> h } + fadeOut())
                        }.using(SizeTransform(clip = false))
                    },
                    label = "stepCounter",
                ) { step ->
                    Text(
                        text = "${step + 1}/$totalSteps",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (secondaryButtonText != null && onSecondary != null) {
                    OutlinedButton(
                        onClick = onSecondary,
                        shape = RoundedCornerShape(20.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text(
                            secondaryButtonText,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                MorphingStepFab(
                    enabled = isPrimaryEnabled,
                    isLastStep = isLastStep,
                    shape = fabShape,
                    rotation = animatedRotation,
                    primaryText = primaryButtonText,
                    onClick = onPrimary,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun MorphingStepFab(
    enabled: Boolean,
    isLastStep: Boolean,
    shape: androidx.compose.ui.graphics.Shape,
    rotation: Float,
    primaryText: String,
    onClick: () -> Unit,
) {
    val containerColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    }
    Surface(
        modifier = Modifier
            .height(56.dp)
            .rotate(rotation),
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clickable(enabled = enabled, onClick = onClick),
        ) {
            Box(modifier = Modifier.rotate(-rotation)) {
                AnimatedContent(
                    targetState = isLastStep,
                    transitionSpec = {
                        ContentTransform(
                            targetContentEnter = fadeIn(animationSpec = tween(220, delayMillis = 90))
                                + scaleIn(initialScale = 0.9f, animationSpec = tween(220, delayMillis = 90)),
                            initialContentExit = fadeOut(animationSpec = tween(90))
                                + scaleOut(targetScale = 0.9f, animationSpec = tween(90)),
                        )
                    },
                    label = "fabIcon",
                ) { last ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = primaryText,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = contentColor,
                        )
                        Spacer(Modifier.size(8.dp))
                        Icon(
                            imageVector = if (last) Icons.Rounded.Check else Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}
