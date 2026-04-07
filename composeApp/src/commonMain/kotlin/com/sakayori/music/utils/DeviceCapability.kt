package com.sakayori.music.utils

expect object DeviceCapability {
    fun isLowEndDevice(): Boolean

    fun getRamGb(): Int

    fun getCpuCores(): Int
}
