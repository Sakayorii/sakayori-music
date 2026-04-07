package com.sakayori.music.utils

import android.app.ActivityManager
import android.content.Context
import multiplatform.network.cmptoast.AppContext

actual object DeviceCapability {
    actual fun isLowEndDevice(): Boolean {
        val ramGb = getRamGb()
        val cores = getCpuCores()
        return ramGb < 4 || cores < 4 || isSystemLowRam()
    }

    actual fun getRamGb(): Int {
        val context = AppContext.get() as? Context ?: return 8
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 8
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        return (memoryInfo.totalMem / (1024L * 1024L * 1024L)).toInt()
    }

    actual fun getCpuCores(): Int = Runtime.getRuntime().availableProcessors()

    private fun isSystemLowRam(): Boolean {
        val context = AppContext.get() as? Context ?: return false
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return activityManager.isLowRamDevice
    }
}
