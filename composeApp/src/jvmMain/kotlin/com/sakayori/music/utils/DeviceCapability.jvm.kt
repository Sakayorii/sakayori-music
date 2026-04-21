package com.sakayori.music.utils

actual object DeviceCapability {
    actual fun isLowEndDevice(): Boolean {
        val ramGb = getRamGb()
        val cores = getCpuCores()
        return ramGb < 4 || cores < 4
    }

    actual fun getRamGb(): Int {
        val totalBytes = tryGetTotalPhysicalMemory() ?: return 8
        return (totalBytes / (1024L * 1024L * 1024L)).toInt().coerceAtLeast(1)
    }

    actual fun getCpuCores(): Int = Runtime.getRuntime().availableProcessors()

    private fun tryGetTotalPhysicalMemory(): Long? {
        val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        for (methodName in arrayOf("getTotalMemorySize", "getTotalPhysicalMemorySize")) {
            try {
                val method = osBean.javaClass.methods.firstOrNull { it.name == methodName } ?: continue
                val result = method.invoke(osBean) as? Long
                if (result != null && result > 0L) return result
            } catch (_: Throwable) {
            }
        }
        return null
    }
}
