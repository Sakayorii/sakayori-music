@file:Suppress("unused")
@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sakayori.music.expect

import androidx.compose.runtime.Composable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIDeviceOrientation
import platform.UIKit.UIPasteboard

actual fun copyToClipboard(label: String, text: String) {
    UIPasteboard.generalPasteboard.setString(text)
}

actual fun networkStatusFlow(): Flow<Boolean> = flow {
    val pingUrl = NSURL.URLWithString("https://www.google.com/generate_204")
    while (true) {
        val connected = if (pingUrl == null) {
            false
        } else {
            kotlinx.coroutines.suspendCancellableCoroutine<Boolean> { cont ->
                val task = NSURLSession.sharedSession.dataTaskWithRequest(NSURLRequest(uRL = pingUrl)) { _, _, error ->
                    if (cont.isActive) cont.resumeWith(Result.success(error == null))
                }
                task.resume()
                cont.invokeOnCancellation { task.cancel() }
            }
        }
        emit(connected)
        delay(if (connected) 30_000L else 5_000L)
    }
}

actual fun openUrl(url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    UIApplication.sharedApplication.openURL(nsUrl, emptyMap<Any?, Any?>(), null)
}

actual fun shareUrl(title: String, url: String) {
    val nsUrl = NSURL.URLWithString(url) ?: return
    val activityVc = UIActivityViewController(
        activityItems = listOf(nsUrl),
        applicationActivities = null,
    )
    UIApplication.sharedApplication.keyWindow?.rootViewController?.presentViewController(
        activityVc, animated = true, completion = null,
    )
}

actual fun moveTaskToBack() { }

actual fun getDownloadFolderPath(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    return (paths.firstOrNull() as? String) ?: ""
}

actual fun isValidPendingUpdate(path: String): Boolean = false

actual fun deletePendingUpdate(path: String) { }

actual fun pickUpdateAssetName(versionTag: String): List<String> = emptyList()

actual fun installUpdateAsset(filePath: String) { }

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) { }

actual fun currentOrientation(): Orientation =
    when (UIDevice.currentDevice.orientation) {
        UIDeviceOrientation.UIDeviceOrientationLandscapeLeft,
        UIDeviceOrientation.UIDeviceOrientationLandscapeRight -> Orientation.LANDSCAPE
        UIDeviceOrientation.UIDeviceOrientationPortrait,
        UIDeviceOrientation.UIDeviceOrientationPortraitUpsideDown -> Orientation.PORTRAIT
        else -> Orientation.UNSPECIFIED
    }
