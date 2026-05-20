package com.sakayori.music.ui.component

import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

actual fun smoothCornerShape(
    topStart: Dp,
    topEnd: Dp,
    bottomStart: Dp,
    bottomEnd: Dp,
    smoothness: Int,
): Shape = AbsoluteSmoothCornerShape(
    cornerRadiusTL = topStart,
    smoothnessAsPercentTL = smoothness,
    cornerRadiusTR = topEnd,
    smoothnessAsPercentTR = smoothness,
    cornerRadiusBL = bottomStart,
    smoothnessAsPercentBL = smoothness,
    cornerRadiusBR = bottomEnd,
    smoothnessAsPercentBR = smoothness,
)
