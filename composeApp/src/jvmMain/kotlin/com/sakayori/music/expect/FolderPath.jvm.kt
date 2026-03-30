package com.sakayori.music.expect

actual fun getDownloadFolderPath(): String = System.getProperty("user.home") + "/Downloads"
