package com.sakayori.music.ui.component

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

expect fun smoothCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    smoothness: Int = 60,
): Shape

fun smoothCornerShape(corner: Dp, smoothness: Int = 60): Shape =
    smoothCornerShape(
        topStart = corner,
        topEnd = corner,
        bottomStart = corner,
        bottomEnd = corner,
        smoothness = smoothness,
    )
