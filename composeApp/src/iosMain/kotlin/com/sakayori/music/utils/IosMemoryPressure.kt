@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sakayori.music.utils

import com.sakayori.logger.Logger
import com.sakayori.music.expect.platformRequestGc
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURLCache
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification

private const val TAG = "IosMemoryPressure"
private var installed = false

object IosMemoryPressure {
    var onPressure: (() -> Unit)? = null

    fun install() {
        if (installed) return
        installed = true
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidReceiveMemoryWarningNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            Logger.w(TAG, "UIApplication memory warning — trimming caches")
            try {
                NSURLCache.sharedURLCache.removeAllCachedResponses()
            } catch (_: Throwable) {
            }
            platformRequestGc()
            onPressure?.invoke()
        }
    }
}
