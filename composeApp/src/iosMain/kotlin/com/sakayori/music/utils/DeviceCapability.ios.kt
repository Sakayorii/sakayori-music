@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sakayori.music.utils

import platform.Foundation.NSProcessInfo

actual object DeviceCapability {
    actual fun isLowEndDevice(): Boolean {
        val ram = getRamGb()
        val cores = getCpuCores()
        return ram <= 2 || cores <= 2
    }

    actual fun getRamGb(): Int {
        val bytes = NSProcessInfo.processInfo.physicalMemory.toLong()
        if (bytes <= 0L) return 4
        val gib = (bytes / (1024L * 1024L * 1024L)).toInt()
        return gib.coerceIn(1, 64)
    }

    actual fun getCpuCores(): Int =
        NSProcessInfo.processInfo.processorCount.toInt().coerceAtLeast(1)
}
