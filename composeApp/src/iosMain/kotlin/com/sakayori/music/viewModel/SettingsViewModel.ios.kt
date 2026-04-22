package com.sakayori.music.viewModel

import com.eygraber.uri.Uri
import com.sakayori.domain.repository.CacheRepository
import com.sakayori.domain.repository.CommonRepository
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual suspend fun calculateDataFraction(cacheRepository: CacheRepository): SettingsStorageSectionFraction? = null

actual suspend fun restoreNative(
    commonRepository: CommonRepository,
    uri: Uri,
    getData: () -> Unit,
) {}

actual suspend fun backupNative(
    commonRepository: CommonRepository,
    uri: Uri,
    backupDownloaded: Boolean,
) {}

actual fun getPackageName(): String =
    NSBundle.mainBundle.bundleIdentifier ?: "com.sakayori.music"

actual fun getFileDir(): String {
    val paths = NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, true)
    return (paths.firstOrNull() as? String) ?: ""
}

actual fun changeLanguageNative(code: String) {}
