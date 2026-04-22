package com.sakayori.music.utils

import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean
import java.util.concurrent.atomic.AtomicLong
import javax.swing.SwingUtilities

object FreezeWatchdog {
    @Volatile
    private var started = false

    private val lastEdtPing = AtomicLong(System.currentTimeMillis())

    private const val PING_INTERVAL_MS = 5_000L
    private const val FREEZE_THRESHOLD_MS = 45_000L
    private const val DEADLOCK_CHECK_INTERVAL_MS = 15_000L

    fun start() {
        if (started) return
        started = true

        Thread {
            while (true) {
                try {
                    Thread.sleep(PING_INTERVAL_MS)
                    val pingStart = System.currentTimeMillis()
                    SwingUtilities.invokeLater {
                        lastEdtPing.set(System.currentTimeMillis())
                    }
                    val now = System.currentTimeMillis()
                    val sinceLastEdt = now - lastEdtPing.get()
                    if (sinceLastEdt > FREEZE_THRESHOLD_MS) {
                        System.err.println(
                            "FreezeWatchdog: EDT unresponsive for ${sinceLastEdt}ms, dumping diagnostics",
                        )
                        dumpAllThreads()
                        if (sinceLastEdt > FREEZE_THRESHOLD_MS * 2) {
                            System.err.println(
                                "FreezeWatchdog: EDT dead >${FREEZE_THRESHOLD_MS * 2}ms, force-halting JVM (exit 137)",
                            )
                            Runtime.getRuntime().halt(137)
                        }
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Throwable) {
                }
            }
        }.apply {
            name = "FreezeWatchdog"
            isDaemon = true
            priority = Thread.MAX_PRIORITY
            start()
        }

        Thread {
            val threadMx: ThreadMXBean = ManagementFactory.getThreadMXBean()
            while (true) {
                try {
                    Thread.sleep(DEADLOCK_CHECK_INTERVAL_MS)
                    val deadlocked = threadMx.findDeadlockedThreads()
                    if (deadlocked != null && deadlocked.isNotEmpty()) {
                        System.err.println(
                            "FreezeWatchdog: ${deadlocked.size} deadlocked threads detected, force-halting",
                        )
                        for (id in deadlocked) {
                            val info = threadMx.getThreadInfo(id, Int.MAX_VALUE)
                            System.err.println("  Deadlocked: ${info?.threadName}")
                            info?.stackTrace?.take(15)?.forEach { System.err.println("    at $it") }
                        }
                        Runtime.getRuntime().halt(138)
                    }
                } catch (_: InterruptedException) {
                    return@Thread
                } catch (_: Throwable) {
                }
            }
        }.apply {
            name = "FreezeWatchdog-Deadlock"
            isDaemon = true
            start()
        }
    }

    private fun dumpAllThreads() {
        try {
            val threadMx = ManagementFactory.getThreadMXBean()
            val infos = threadMx.dumpAllThreads(true, true)
            val logDir = java.io.File(System.getProperty("user.home"), ".sakayori-music")
            logDir.mkdirs()
            val dumpFile = java.io.File(logDir, "thread-dump-${System.currentTimeMillis()}.txt")
            dumpFile.bufferedWriter().use { w ->
                w.write("Thread dump at ${java.time.LocalDateTime.now()}\n\n")
                for (info in infos) {
                    w.write("\"${info.threadName}\" state=${info.threadState}\n")
                    info.stackTrace.forEach { w.write("  at $it\n") }
                    w.write("\n")
                }
            }
            System.err.println("Thread dump written to ${dumpFile.absolutePath}")
        } catch (e: Throwable) {
            System.err.println("Failed to dump threads: ${e.message}")
        }
    }
}
