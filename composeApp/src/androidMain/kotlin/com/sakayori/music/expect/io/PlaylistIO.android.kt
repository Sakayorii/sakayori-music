package com.sakayori.music.expect.io

import android.content.Context
import com.eygraber.uri.toAndroidUri
import com.eygraber.uri.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext.getKoin

actual suspend fun writeTextToUri(text: String, uri: String): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val context: Context = getKoin().get()
            context.contentResolver.openOutputStream(uri.toUri().toAndroidUri())?.use {
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
            context.contentResolver.openInputStream(uri.toUri().toAndroidUri())?.use {
                it.bufferedReader().readText()
            }
        } catch (e: Exception) {
            null
        }
    }
