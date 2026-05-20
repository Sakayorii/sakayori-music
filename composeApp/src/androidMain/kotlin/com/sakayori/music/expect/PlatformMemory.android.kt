package com.sakayori.music.expect

import java.util.concurrent.atomic.AtomicLong

private val lastGcAt = AtomicLong(0L)
private const val MIN_GC_INTERVAL_MS = 60_000L

actual fun platformRequestGc() {
    val now = System.currentTimeMillis()
    val prev = lastGcAt.get()
    if (now - prev < MIN_GC_INTERVAL_MS) return
    if (!lastGcAt.compareAndSet(prev, now)) return
    Thread({
        try {
            Runtime.getRuntime().gc()
        } catch (_: Throwable) {
        }
    }, "sakayori-gc-hint").apply {
        isDaemon = true
        priority = Thread.MIN_PRIORITY
        start()
    }
}
