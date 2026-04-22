package com.sakayori.music.expect

import java.io.File
import java.io.FileOutputStream

actual fun writeBytesToFile(path: String, bytes: ByteArray) {
    val file = File(path)
    file.parentFile?.takeIf { !it.exists() }?.mkdirs()
    FileOutputStream(file).use { it.write(bytes) }
}
