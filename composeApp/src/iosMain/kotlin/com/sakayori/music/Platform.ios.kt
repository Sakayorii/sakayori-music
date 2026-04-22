package com.sakayori.music

actual fun getPlatform(): Platform = Platform.iOS

internal actual fun desktopOsName(): String = "iOS"
