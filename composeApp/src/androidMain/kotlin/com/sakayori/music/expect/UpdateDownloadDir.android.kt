package com.sakayori.music.expect

import androidx.appcompat.app.AppCompatActivity
import org.koin.mp.KoinPlatform.getKoin
import java.io.File

actual fun updateDownloadDir(): File {
    val context: AppCompatActivity = getKoin().get()
    return File(context.filesDir, "updates")
}

actual fun isValidPendingUpdate(path: String): Boolean {
    if (path.isEmpty()) return false
    val f = File(path)
    return f.exists() && f.length() > 0
}

actual fun deletePendingUpdate(path: String) {
    if (path.isEmpty()) return
    try {
        File(path).delete()
    } catch (_: Throwable) {
    }
}
