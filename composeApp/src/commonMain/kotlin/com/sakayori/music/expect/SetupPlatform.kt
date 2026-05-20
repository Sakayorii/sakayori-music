package com.sakayori.music.expect

expect object SetupPlatform {
    val needsNotificationPermission: Boolean
    val needsBatteryOptOut: Boolean

    fun isNotificationGranted(): Boolean
    fun isBatteryOptIgnored(): Boolean
    fun requestNotificationPermission(onResult: (Boolean) -> Unit)
    fun requestIgnoreBatteryOptimizations()
    fun systemLanguageTag(): String
}
