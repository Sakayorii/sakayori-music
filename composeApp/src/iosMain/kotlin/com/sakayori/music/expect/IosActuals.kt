@file:Suppress("unused")

package com.sakayori.music.expect

import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.NSURL
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIPasteboard

actual fun copyToClipboard(label: String, text: String) {
    UIPasteboard.generalPasteboard.setString(text)
}

actual fun networkStatusFlow(): Flow<Boolean> = flow { emit(true) }

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

actual fun currentOrientation(): Orientation = Orientation.PORTRAIT
