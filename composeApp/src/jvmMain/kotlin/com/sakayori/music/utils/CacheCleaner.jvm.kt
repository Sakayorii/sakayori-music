package com.sakayori.music.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual object CacheCleaner {
    actual suspend fun cleanupOldFiles(maxAgeDays: Int) {
        withContext(Dispatchers.IO) {
            try {
                val cutoff = System.currentTimeMillis() - (maxAgeDays * 24L * 60L * 60L * 1000L)
                val userHome = System.getProperty("user.home") ?: return@withContext
                val cacheDirs = listOf(
                    File(userHome, ".sakayori-music/coil3_disk_cache"),
                    File(userHome, ".sakayori-music/http_cache"),
                )
                cacheDirs.forEach { cleanDir(it, cutoff) }
            } catch (_: Exception) {
            }
        }
    }

    private fun cleanDir(dir: File?, cutoff: Long) {
        if (dir == null || !dir.exists() || !dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                cleanDir(file, cutoff)
            } else if (file.lastModified() < cutoff) {
                try { file.delete() } catch (_: Exception) {}
            }
        }
    }
}
