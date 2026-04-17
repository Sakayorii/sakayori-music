package com.sakayori.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakayori.music.ui.theme.typo
import org.jetbrains.compose.resources.stringResource
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.hours
import com.sakayori.music.generated.resources.minutes

data class BarChartEntry(
    val label: String,
    val value: Long,
)

@Composable
fun BarChart(
    entries: List<BarChartEntry>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF00BCD4),
    gridColor: Color = Color.White.copy(alpha = 0.1f),
    labelColor: Color = Color.White.copy(alpha = 0.6f),
    valueColor: Color = Color.White.copy(alpha = 0.8f),
    animationDuration: Int = 800,
) {
    if (entries.isEmpty()) return

    val maxValue = (entries.maxOfOrNull { it.value } ?: 1L).coerceAtLeast(1L)
    val animationProgress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(entries) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(animationDuration))
    }

    val labelStyle = TextStyle(color = labelColor, fontSize = 10.sp)
    val valueStyle = TextStyle(color = valueColor, fontSize = 9.sp)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp),
    ) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val bottomPadding = 28f
        val topPadding = 16f
        val leftPadding = 8f
        val rightPadding = 8f
        val chartHeight = canvasHeight - bottomPadding - topPadding
        val chartWidth = canvasWidth - leftPadding - rightPadding

        for (i in 0..4) {
            val y = topPadding + chartHeight * (1f - i / 4f)
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, y),
                end = Offset(canvasWidth - rightPadding, y),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f)),
            )
        }

        val barCount = entries.size
        val totalBarSpace = chartWidth
        val barWidth = (totalBarSpace / barCount) * 0.6f
        val barGap = (totalBarSpace / barCount) * 0.4f
        val sectionWidth = totalBarSpace / barCount

        entries.forEachIndexed { index, entry ->
            val barHeight = (entry.value.toFloat() / maxValue) * chartHeight * animationProgress.value
            val x = leftPadding + sectionWidth * index + (sectionWidth - barWidth) / 2f
            val y = topPadding + chartHeight - barHeight

            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(4f, 4f),
            )

            val labelResult = textMeasurer.measure(entry.label, labelStyle)
            drawText(
                textLayoutResult = labelResult,
                topLeft = Offset(
                    x + barWidth / 2f - labelResult.size.width / 2f,
                    canvasHeight - bottomPadding + 6f,
                ),
            )

            if (entry.value > 0 && animationProgress.value > 0.5f) {
                val valueText = formatChartValue(entry.value)
                val valueResult = textMeasurer.measure(valueText, valueStyle)
                drawText(
                    textLayoutResult = valueResult,
                    topLeft = Offset(
                        x + barWidth / 2f - valueResult.size.width / 2f,
                        (y - valueResult.size.height - 2f).coerceAtLeast(topPadding),
                    ),
                )
            }
        }
    }
}

@Composable
fun HorizontalBarChart(
    entries: List<BarChartEntry>,
    modifier: Modifier = Modifier,
    barColor: Color = Color(0xFF00BCD4),
    labelColor: Color = Color.White.copy(alpha = 0.7f),
) {
    if (entries.isEmpty()) return

    val maxValue = (entries.maxOfOrNull { it.value } ?: 1L).coerceAtLeast(1L)
    val animationProgress = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()

    LaunchedEffect(entries) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(1f, animationSpec = tween(600))
    }

    val labelStyle = TextStyle(color = labelColor, fontSize = 11.sp)
    val valueStyle = TextStyle(color = Color.White.copy(alpha = 0.9f), fontSize = 10.sp)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height((entries.size * 36 + 8).dp),
    ) {
        val canvasWidth = size.width
        val leftPadding = 80f
        val rightPadding = 8f
        val barAreaWidth = canvasWidth - leftPadding - rightPadding
        val barHeight = 20f
        val rowHeight = 36f * density

        entries.forEachIndexed { index, entry ->
            val y = index * rowHeight
            val barWidth = (entry.value.toFloat() / maxValue) * barAreaWidth * animationProgress.value

            val labelResult = textMeasurer.measure(entry.label, labelStyle)
            drawText(
                textLayoutResult = labelResult,
                topLeft = Offset(
                    leftPadding - labelResult.size.width - 8f,
                    y + (rowHeight - labelResult.size.height) / 2f,
                ),
            )

            drawRoundRect(
                color = barColor,
                topLeft = Offset(leftPadding, y + (rowHeight - barHeight) / 2f),
                size = Size(barWidth.coerceAtLeast(2f), barHeight),
                cornerRadius = CornerRadius(4f, 4f),
            )

            if (entry.value > 0 && animationProgress.value > 0.3f) {
                val valueText = formatChartValue(entry.value)
                val valueResult = textMeasurer.measure(valueText, valueStyle)
                drawText(
                    textLayoutResult = valueResult,
                    topLeft = Offset(
                        leftPadding + barWidth + 6f,
                        y + (rowHeight - valueResult.size.height) / 2f,
                    ),
                )
            }
        }
    }
}

@Composable
fun ListeningTimeChart(
    totalSeconds: Long,
    modifier: Modifier = Modifier,
) {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column {
            Text(
                text = "$hours",
                style = typo().headlineLarge,
                color = Color(0xFF00BCD4),
            )
            Text(
                text = stringResource(Res.string.hours),
                style = typo().bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
        Column {
            Text(
                text = "$minutes",
                style = typo().headlineLarge,
                color = Color(0xFF00BCD4),
            )
            Text(
                text = stringResource(Res.string.minutes),
                style = typo().bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )
        }
    }
}

private fun formatChartValue(value: Long): String =
    when {
        value >= 1_000_000 -> "${value / 1_000_000}M"
        value >= 1_000 -> "${value / 1_000}K"
        else -> "$value"
    }
