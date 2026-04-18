package com.sakayori.music.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin
import java.io.File

actual object CacheCleaner {
    actual suspend fun cleanupOldFiles(maxAgeDays: Int) {
        withContext(Dispatchers.IO) {
            try {
                val context: Context = getKoin().get()
                val cutoff = System.currentTimeMillis() - (maxAgeDays * 24L * 60L * 60L * 1000L)
                val cacheDir = context.cacheDir
                cleanDir(cacheDir, cutoff)
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
