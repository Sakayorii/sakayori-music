package com.sakayori.music.ui.component

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp

actual fun smoothCornerShape(
    topStart: Dp,
    topEnd: Dp,
    bottomStart: Dp,
    bottomEnd: Dp,
    smoothness: Int,
): Shape = RoundedCornerShape(
    topStart = topStart,
    topEnd = topEnd,
    bottomStart = bottomStart,
    bottomEnd = bottomEnd,
)
