package com.sakayori.music.extension

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

fun InputStream.zipInputStream(): ZipInputStream = ZipInputStream(this)

fun OutputStream.zipOutputStream(): ZipOutputStream = ZipOutputStream(this)

fun getSizeOfFile(dir: File): Long {
    var dirSize: Long = 0
    dir.listFiles()?.forEach { f ->
        dirSize += f.length()
        if (f.isDirectory) {
            dirSize += getSizeOfFile(f)
        }
    }
    return dirSize
}
