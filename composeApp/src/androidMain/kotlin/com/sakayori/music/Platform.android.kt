package com.sakayori.music

actual fun getPlatform(): Platform = Platform.Android

internal actual fun desktopOsName(): String = "android"
