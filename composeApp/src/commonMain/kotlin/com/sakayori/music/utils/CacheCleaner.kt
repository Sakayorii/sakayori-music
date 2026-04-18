package com.sakayori.music.utils

expect object CacheCleaner {
    suspend fun cleanupOldFiles(maxAgeDays: Int = 30)
}
