package com.sakayori.music.utils

import com.sakayori.logger.Logger
import java.lang.management.ManagementFactory

object HeapPressureMonitor {
    private const val TAG = "HeapPressureMonitor"
    private const val CHECK_INTERVAL_MS = 10_000L
    private const val PRESSURE_THRESHOLD = 0.80
    private const val CRITICAL_THRESHOLD = 0.92
    private const val COOLDOWN_MS = 30_000L

    @Volatile
    private var started = false

    @Volatile
    private var lastTrimAt = 0L

    var onPressure: (() -> Unit)? = null

    fun start() {
        if (started) return
        started = true

        Thread {
            while (true) {
                try {
                    Thread.sleep(CHECK_INTERVAL_MS)
                    val runtime = Runtime.getRuntime()
                    val used = runtime.totalMemory() - runtime.freeMemory()
                    val max = runtime.maxMemory()
                    val ratio = used.toDouble() / max

                    if (ratio > CRITICAL_THRESHOLD) {
                        Logger.e(TAG, "CRITICAL heap pressure: ${(ratio * 100).toInt()}% (${used / 1024 / 1024} MB / ${max / 1024 / 1024} MB)")
                        performAggressiveCleanup()
                    } else if (ratio > PRESSURE_THRESHOLD) {
                        val now = System.currentTimeMillis()
                        if (now - lastTrimAt > COOLDOWN_MS) {
                            Logger.w(TAG, "Heap pressure: ${(ratio * 100).toInt()}% — triggering cleanup")
                            lastTrimAt = now
                            performStandardCleanup()
                        }
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Throwable) {
                }
            }
        }.apply {
            name = "HeapPressureMonitor"
            isDaemon = true
            priority = Thread.NORM_PRIORITY - 1
            start()
        }
    }

    private fun performStandardCleanup() {
        onPressure?.invoke()
        try {
            Runtime.getRuntime().gc()
        } catch (_: Throwable) {
        }
    }

    private fun performAggressiveCleanup() {
        onPressure?.invoke()
        try {
            System.gc()
            Runtime.getRuntime().gc()
        } catch (_: Throwable) {
        }
        try {
            val os = ManagementFactory.getOperatingSystemMXBean()
            Logger.w(TAG, "System load avg: ${os.systemLoadAverage}")
        } catch (_: Throwable) {
        }
    }

    fun currentUsagePercent(): Int {
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        val max = runtime.maxMemory()
        return ((used.toDouble() / max) * 100).toInt()
    }
}
