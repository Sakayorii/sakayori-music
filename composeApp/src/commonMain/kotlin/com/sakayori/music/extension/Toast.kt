package com.sakayori.music.extension

import androidx.compose.ui.graphics.Color
import multiplatform.network.cmptoast.ToastDuration
import multiplatform.network.cmptoast.ToastGravity
import multiplatform.network.cmptoast.showToast

fun makeDarkToast(
    message: String,
    gravity: ToastGravity = ToastGravity.Bottom,
    duration: ToastDuration = ToastDuration.Short,
) {
    showToast(
        message = message,
        gravity = gravity,
        duration = duration,
        backgroundColor = Color(0xFF1A1A1A),
        textColor = Color.White,
        cornerRadius = 8,
        bottomPadding = 80,
    )
}
