package com.sakayori.music

actual fun getPlatform(): Platform = Platform.Desktop

internal actual fun desktopOsName(): String = System.getProperty("os.name") ?: "jvm"
