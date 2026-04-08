package com.sakayori.music.utils

import android.app.ActivityManager
import android.content.Context
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

actual object DeviceCapability : KoinComponent {
    actual fun isLowEndDevice(): Boolean {
        val ramGb = getRamGb()
        val cores = getCpuCores()
        return ramGb < 4 || cores < 4 || isSystemLowRam()
    }

    actual fun getRamGb(): Int {
        val context = try { get<Context>() } catch (_: Throwable) { return 8 }
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 8
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return (memoryInfo.totalMem / (1024L * 1024L * 1024L)).toInt()
    }

    actual fun getCpuCores(): Int = Runtime.getRuntime().availableProcessors()

    private fun isSystemLowRam(): Boolean {
        val context = try { get<Context>() } catch (_: Throwable) { return false }
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return activityManager.isLowRamDevice
    }
}
