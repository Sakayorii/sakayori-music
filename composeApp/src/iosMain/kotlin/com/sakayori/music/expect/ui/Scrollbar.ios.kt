package com.sakayori.music.expect.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
actual fun HorizontalScrollBar(
    modifier: Modifier,
    scrollState: LazyListState,
) {
    val progress by remember(scrollState) {
        derivedStateOf {
            val totalItems = scrollState.layoutInfo.totalItemsCount
            if (totalItems == 0) return@derivedStateOf 0f
            val first = scrollState.firstVisibleItemIndex
            val offset = scrollState.firstVisibleItemScrollOffset
            val visibleItems = scrollState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            val progress = (first + offset / 1000f) / (totalItems - visibleItems).coerceAtLeast(1).toFloat()
            progress.coerceIn(0f, 1f)
        }
    }
    val visibleFraction by remember(scrollState) {
        derivedStateOf {
            val totalItems = scrollState.layoutInfo.totalItemsCount.coerceAtLeast(1)
            val visible = scrollState.layoutInfo.visibleItemsInfo.size.coerceAtLeast(1)
            (visible.toFloat() / totalItems.toFloat()).coerceIn(0.05f, 1f)
        }
    }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp),
    ) {
        val trackWidth = size.width
        val thumbWidth = trackWidth * visibleFraction
        val thumbX = (trackWidth - thumbWidth) * progress
        drawRect(
            color = Color.Gray.copy(alpha = 0.15f),
            topLeft = Offset(0f, 0f),
            size = Size(trackWidth, size.height),
        )
        drawRect(
            color = Color.Gray.copy(alpha = 0.6f),
            topLeft = Offset(thumbX, 0f),
            size = Size(thumbWidth, size.height),
        )
    }
}
