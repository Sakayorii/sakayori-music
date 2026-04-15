package com.sakayori.music.expect.io

expect suspend fun writeTextToUri(text: String, uri: String): Boolean

expect suspend fun readTextFromUri(uri: String): String?
