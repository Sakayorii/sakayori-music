package com.sakayori.music.utils

actual object DeviceCapability {
    actual fun isLowEndDevice(): Boolean {
        val ramGb = getRamGb()
        val cores = getCpuCores()
        return ramGb < 4 || cores < 4
    }

    actual fun getRamGb(): Int {
        val maxMem = Runtime.getRuntime().maxMemory()
        val totalMem = try {
            val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
            val method = osBean.javaClass.getMethod("getTotalMemorySize")
            method.isAccessible = true
            method.invoke(osBean) as Long
        } catch (_: Throwable) {
            try {
                val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
                val method = osBean.javaClass.getMethod("getTotalPhysicalMemorySize")
                method.isAccessible = true
                method.invoke(osBean) as Long
            } catch (_: Throwable) {
                maxMem
            }
        }
        return (totalMem / (1024L * 1024L * 1024L)).toInt()
    }

    actual fun getCpuCores(): Int = Runtime.getRuntime().availableProcessors()
}
