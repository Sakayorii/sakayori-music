@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sakayori.music.extension

import androidx.compose.runtime.Composable
import com.sakayori.domain.data.model.ui.ScreenSizeInfo
import kotlinx.cinterop.useContents
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen

@Composable
actual fun getScreenSizeInfo(): ScreenSizeInfo {
    val scale = UIScreen.mainScreen.scale
    val (wDP, hDP) = UIScreen.mainScreen.bounds.useContents {
        size.width.toInt() to size.height.toInt()
    }
    val wPX = (wDP * scale).toInt()
    val hPX = (hDP * scale).toInt()
    return ScreenSizeInfo(hDP = hDP, wDP = wDP, hPX = hPX, wPX = wPX)
}

@Composable
actual fun KeepScreenOn() {
    UIApplication.sharedApplication.idleTimerDisabled = true
}

@Composable
actual fun rememberIsInPipMode(): Boolean = false
