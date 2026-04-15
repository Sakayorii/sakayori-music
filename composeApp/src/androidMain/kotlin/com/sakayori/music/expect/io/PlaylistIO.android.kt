package com.sakayori.music.expect.io

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.mp.KoinPlatform.getKoin

actual suspend fun writeTextToUri(text: String, uri: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val context: Context = getKoin().get()
            context.contentResolver.openOutputStream(Uri.parse(uri))?.use {
                it.bufferedWriter().use { writer ->
                    writer.write(text)
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

actual suspend fun readTextFromUri(uri: String): String? =
    withContext(Dispatchers.IO) {
        try {
            val context: Context = getKoin().get()
            context.contentResolver.openInputStream(Uri.parse(uri))?.use {
                it.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
    }
