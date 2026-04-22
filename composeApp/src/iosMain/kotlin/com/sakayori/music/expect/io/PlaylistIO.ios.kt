@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sakayori.music.expect.io

import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.writeToFile

actual suspend fun writeTextToUri(text: String, uri: String): Boolean {
    val nsText = NSString.create(string = text)
    val data = nsText.dataUsingEncoding(NSUTF8StringEncoding) ?: return false
    return data.writeToFile(uri, atomically = true)
}

actual suspend fun readTextFromUri(uri: String): String? {
    val data = NSData.dataWithContentsOfFile(uri) ?: return null
    val nsString = NSString.create(data = data, encoding = NSUTF8StringEncoding) ?: return null
    return nsString.toString()
}
