package com.sakayori.music

import android.os.Handler
import android.os.Looper
import com.sakayori.logger.Logger

class AnrWatchdog(
    private val timeoutMs: Long = 5000L
) : Thread("ANR-Watchdog") {

    @Volatile
    private var tick = 0L

    @Volatile
    private var reported = false

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        isDaemon = true
    }

    override fun run() {
        while (!isInterrupted) {
            tick = 0L
            reported = false
            mainHandler.post { tick = System.currentTimeMillis() }

            try {
                sleep(timeoutMs)
            } catch (_: InterruptedException) {
                return
            }

            if (tick == 0L && !reported) {
                reported = true
                val mainThread = Looper.getMainLooper().thread
                val stackTrace = mainThread.stackTrace
                val sb = StringBuilder("ANR detected! Main thread blocked for ${timeoutMs}ms\n")
                for (element in stackTrace) {
                    sb.append("    at $element\n")
                }
                Logger.e("ANR-Watchdog", sb.toString())
            }
        }
    }
}
