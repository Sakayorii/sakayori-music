package com.sakayori.music.expect

import java.util.Locale

actual object SetupPlatform {
    actual val needsNotificationPermission: Boolean = false
    actual val needsBatteryOptOut: Boolean = false

    actual fun isNotificationGranted(): Boolean = true
    actual fun isBatteryOptIgnored(): Boolean = true
    actual fun requestNotificationPermission(onResult: (Boolean) -> Unit) { onResult(true) }
    actual fun requestIgnoreBatteryOptimizations() {}

    actual fun systemLanguageTag(): String {
        return try {
            Locale.getDefault().toLanguageTag()
        } catch (_: Throwable) {
            "en-US"
        }
    }
}
