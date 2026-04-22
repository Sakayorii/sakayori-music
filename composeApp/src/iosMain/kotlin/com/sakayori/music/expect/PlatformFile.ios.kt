@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sakayori.music.expect

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile

actual fun writeBytesToFile(path: String, bytes: ByteArray) {
    val dirUrl = NSURL.fileURLWithPath(path).URLByDeletingLastPathComponent
    if (dirUrl != null) {
        NSFileManager.defaultManager.createDirectoryAtURL(
            url = dirUrl,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
    }
    bytes.usePinned { pinned ->
        val data = NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        data.writeToFile(path, atomically = true)
    }
}
