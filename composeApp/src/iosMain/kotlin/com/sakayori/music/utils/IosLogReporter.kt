@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.time.ExperimentalTime::class)

package com.sakayori.music.utils

import com.sakayori.logger.LogLevel
import com.sakayori.logger.LogReporter
import kotlinx.cinterop.BetaInteropApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.writeToFile

class IosLogReporter : LogReporter {
    @OptIn(BetaInteropApi::class)
    override fun onLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level != LogLevel.ERROR && level != LogLevel.WARN) return
        try {
            val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
            val docs = paths.firstOrNull() as? String ?: return
            val logPath = "$docs/runtime.log"
            val entry = buildString {
                append(kotlin.time.Clock.System.now().toString())
                append(" [")
                append(level.name)
                append("] [")
                append(tag)
                append("] ")
                append(message)
                append('\n')
                if (throwable != null) {
                    append(throwable.stackTraceToString())
                    append('\n')
                }
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
