package com.sakayori.music.utils

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Structure

actual object DeviceCapability {
    actual fun isLowEndDevice(): Boolean {
        val ramGb = getRamGb()
        val cores = getCpuCores()
        return ramGb < 4 || cores < 4
    }

    actual fun getRamGb(): Int {
        if (isWindows) {
            tryWindowsRamGb()?.let { return it }
        }
        val totalBytes = tryJvmTotalPhysicalMemory() ?: return 8
        return (totalBytes / GB_BYTES).toInt().coerceAtLeast(1)
    }

    actual fun getCpuCores(): Int {
        if (isWindows) {
            tryWindowsCpuCores()?.let { return it }
        }
        return Runtime.getRuntime().availableProcessors()
    }

    private val isWindows: Boolean by lazy {
        System.getProperty("os.name").orEmpty().lowercase().contains("win")
    }

    private val kernel32: WinKernel32? by lazy {
        if (!isWindows) null
        else try {
            Native.load("kernel32", WinKernel32::class.java)
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryWindowsRamGb(): Int? {
        return try {
            val k = kernel32 ?: return null
            val status = MemoryStatusEx().apply { dwLength = size() }
            if (!k.GlobalMemoryStatusEx(status)) return null
            val totalBytes = status.ullTotalPhys
            if (totalBytes <= 0L) null
            else (totalBytes / GB_BYTES).toInt().coerceAtLeast(1)
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryWindowsCpuCores(): Int? {
        return try {
            val k = kernel32 ?: return null
            val count = k.GetActiveProcessorCount(ALL_PROCESSOR_GROUPS.toShort())
            if (count > 0) count else null
        } catch (_: Throwable) {
            null
        }
    }

    private fun tryJvmTotalPhysicalMemory(): Long? {
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

    private const val GB_BYTES: Long = 1024L * 1024L * 1024L
    private const val ALL_PROCESSOR_GROUPS: Int = 0xFFFF

    @Structure.FieldOrder(
        "dwLength",
        "dwMemoryLoad",
        "ullTotalPhys",
        "ullAvailPhys",
        "ullTotalPageFile",
        "ullAvailPageFile",
        "ullTotalVirtual",
        "ullAvailVirtual",
        "ullAvailExtendedVirtual",
    )
    class MemoryStatusEx : Structure() {
        @JvmField var dwLength: Int = 0
        @JvmField var dwMemoryLoad: Int = 0
        @JvmField var ullTotalPhys: Long = 0
        @JvmField var ullAvailPhys: Long = 0
        @JvmField var ullTotalPageFile: Long = 0
        @JvmField var ullAvailPageFile: Long = 0
        @JvmField var ullTotalVirtual: Long = 0
        @JvmField var ullAvailVirtual: Long = 0
        @JvmField var ullAvailExtendedVirtual: Long = 0
    }

    interface WinKernel32 : Library {
        fun GlobalMemoryStatusEx(lpBuffer: MemoryStatusEx): Boolean
        fun GetActiveProcessorCount(GroupNumber: Short): Int
    }
}
