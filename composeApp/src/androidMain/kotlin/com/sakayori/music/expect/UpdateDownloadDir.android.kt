package com.sakayori.music.expect

import java.io.File

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
