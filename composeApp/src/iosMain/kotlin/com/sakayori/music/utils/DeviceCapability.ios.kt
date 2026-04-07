package com.sakayori.music.utils

actual object DeviceCapability {
    actual fun isLowEndDevice(): Boolean = false

    actual fun getRamGb(): Int = 8

    actual fun getCpuCores(): Int = 4
}
