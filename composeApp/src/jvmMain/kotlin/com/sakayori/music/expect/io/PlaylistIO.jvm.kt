package com.sakayori.music.expect.io

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

actual suspend fun writeTextToUri(text: String, uri: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            File(uri).writeText(text)
            true
        } catch (e: Exception) {
            false
        }
    }

actual suspend fun readTextFromUri(uri: String): String? =
    withContext(Dispatchers.IO) {
        try {
            File(uri).readText()
        } catch (e: Exception) {
            null
        }
    }
