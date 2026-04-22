package com.sakayori.music.utils

actual object CacheCleaner {
    actual suspend fun cleanupOldFiles(maxAgeDays: Int) {}
}
