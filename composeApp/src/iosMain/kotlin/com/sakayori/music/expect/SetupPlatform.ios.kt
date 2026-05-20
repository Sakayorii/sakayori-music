package com.sakayori.music.expect

import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.languageCode
import platform.Foundation.countryCode

actual object SetupPlatform {
    actual val needsNotificationPermission: Boolean = false
    actual val needsBatteryOptOut: Boolean = false

    actual fun isNotificationGranted(): Boolean = true
    actual fun isBatteryOptIgnored(): Boolean = true
    actual fun requestNotificationPermission(onResult: (Boolean) -> Unit) { onResult(true) }
    actual fun requestIgnoreBatteryOptimizations() {}

    actual fun systemLanguageTag(): String {
        return try {
            val locale = NSLocale.currentLocale
            val lang = (locale.languageCode ?: "en").takeIf { it.isNotBlank() } ?: "en"
            val region = locale.countryCode
            if (region.isNullOrBlank()) lang else "$lang-$region"
        } catch (_: Throwable) {
            "en-US"
        }
    }
}
