@file:OptIn(kotlin.experimental.ExperimentalNativeApi::class, kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.time.ExperimentalTime::class)

package com.sakayori.music.utils

import com.sakayori.logger.Logger
import kotlin.native.setUnhandledExceptionHook
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile

private const val TAG = "IosCrashLogger"
private var installed = false

object IosCrashLogger {
    fun install() {
        if (installed) return
        installed = true
        setUnhandledExceptionHook { throwable: Throwable ->
            writeCrashLog(throwable)
            Logger.e(TAG, "Unhandled Kotlin exception: ${throwable.message}", throwable)
        }
    }

    @OptIn(BetaInteropApi::class)
    private fun writeCrashLog(t: Throwable) {
        try {
            val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            val docs = paths.firstOrNull() as? String ?: return
            val logPath = "$docs/crash.log"
            val entry = buildString {
                append(kotlin.time.Clock.System.now().toString())
                append(" | ")
                append(t::class.simpleName ?: "Unknown")
                append(": ")
                append(t.message ?: "no message")
                append('\n')
                append(t.stackTraceToString())
                append('\n')
            }
            val existing = NSFileManager.defaultManager.contentsAtPath(logPath)
            val existingText = if (existing != null) {
                NSString.create(data = existing, encoding = NSUTF8StringEncoding)?.toString() ?: ""
            } else {
                ""
            }
            val combined = existingText + entry
            val nsString = NSString.create(string = combined)
            val data = nsString.dataUsingEncoding(NSUTF8StringEncoding) ?: return
            data.writeToFile(logPath, atomically = true)
        } catch (_: Throwable) {
        }
    }
}
