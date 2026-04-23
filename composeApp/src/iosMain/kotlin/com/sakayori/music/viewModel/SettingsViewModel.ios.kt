@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sakayori.music.viewModel

import com.eygraber.uri.Uri
import com.sakayori.domain.repository.CacheRepository
import com.sakayori.domain.repository.CommonRepository
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSystemFreeSize
import platform.Foundation.NSFileSystemSize
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDefaults
import platform.Foundation.NSUserDomainMask

private fun documentsDirPath(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    return (paths.firstOrNull() as? String) ?: ""
}

private fun directorySizeBytes(path: String): Long {
    val fileManager = NSFileManager.defaultManager
    val contents = fileManager.subpathsAtPath(path) ?: return 0L
    var total = 0L
    contents.forEach { entry ->
        val name = entry as? String ?: return@forEach
        val attrs = fileManager.attributesOfItemAtPath("$path/$name", null) ?: return@forEach
        val size = (attrs["NSFileSize"] as? Number)?.toLong() ?: 0L
        total += size
    }
    return total
}

actual suspend fun calculateDataFraction(cacheRepository: CacheRepository): SettingsStorageSectionFraction? {
    val fileManager = NSFileManager.defaultManager
    val docPath = documentsDirPath().ifEmpty { return null }
    val fsAttrs = fileManager.attributesOfFileSystemForPath(docPath, null) ?: return null
    val totalBytes = (fsAttrs[NSFileSystemSize] as? Number)?.toLong() ?: return null
    val freeBytes = (fsAttrs[NSFileSystemFreeSize] as? Number)?.toLong() ?: 0L
    if (totalBytes <= 0L) return null

    val appBytes = directorySizeBytes(docPath)
    val freeFrac = freeBytes.toFloat() / totalBytes.toFloat()
    val appFrac = appBytes.toFloat() / totalBytes.toFloat()
    val otherFrac = (1.0f - freeFrac - appFrac).coerceAtLeast(0f)

    return SettingsStorageSectionFraction(
        otherApp = otherFrac,
        downloadCache = 0f,
        playerCache = 0f,
        canvasCache = 0f,
        thumbCache = 0f,
        appDatabase = appFrac,
        freeSpace = freeFrac,
    )
}

actual suspend fun restoreNative(
    commonRepository: CommonRepository,
    uri: Uri,
    getData: () -> Unit,
) {
    getData()
}

actual suspend fun backupNative(
    commonRepository: CommonRepository,
    uri: Uri,
    backupDownloaded: Boolean,
) {
}

actual fun getPackageName(): String =
    NSBundle.mainBundle.bundleIdentifier ?: "com.sakayori.music"

actual fun getFileDir(): String = documentsDirPath()

actual fun changeLanguageNative(code: String) {
    NSUserDefaults.standardUserDefaults.setObject(listOf(code), forKey = "AppleLanguages")
    NSUserDefaults.standardUserDefaults.synchronize()
}
