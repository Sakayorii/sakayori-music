package com.sakayori.music.expect

import java.io.File

actual fun updateDownloadDir(): File =
    File(System.getProperty("user.home"), ".sakayori-music/updates")

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
