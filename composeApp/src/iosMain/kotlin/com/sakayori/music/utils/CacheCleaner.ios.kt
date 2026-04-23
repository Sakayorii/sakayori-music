@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlin.time.ExperimentalTime::class)

package com.sakayori.music.utils

import kotlin.time.Clock
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDate
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileModificationDate
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURLCache
import platform.Foundation.NSUserDomainMask
import platform.Foundation.timeIntervalSince1970

actual object CacheCleaner {
    actual suspend fun cleanupOldFiles(maxAgeDays: Int) {
        NSURLCache.sharedURLCache.removeAllCachedResponses()
        val fileManager = NSFileManager.defaultManager
        val cachesPaths = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true)
        val cachesDir = cachesPaths.firstOrNull() as? String ?: return
        val cutoffSeconds = Clock.System.now().epochSeconds - maxAgeDays * 86_400L
        val contents = fileManager.contentsOfDirectoryAtPath(cachesDir, null) ?: return
        contents.forEach { name ->
            val fileName = name as? String ?: return@forEach
            val fullPath = "$cachesDir/$fileName"
            val attrs = fileManager.attributesOfItemAtPath(fullPath, null) ?: return@forEach
            val modDate = attrs[NSFileModificationDate] as? NSDate ?: return@forEach
            if (modDate.timeIntervalSince1970.toLong() < cutoffSeconds) {
                fileManager.removeItemAtPath(fullPath, null)
            }
        }
    }
}
