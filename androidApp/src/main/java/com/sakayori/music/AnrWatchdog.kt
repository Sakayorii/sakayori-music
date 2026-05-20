package com.sakayori.music

import android.os.Handler
import android.os.Looper
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.sakayori.logger.Logger

class AnrWatchdog(
    private val timeoutMs: Long = 5000L,
    private val settleAfterForegroundMs: Long = 4000L,
) : Thread("ANR-Watchdog") {

    @Volatile
    private var lastResponseAt = 0L

    @Volatile
    private var pendingPostAt = 0L

    @Volatile
    private var isAppForeground = false

    @Volatile
    private var foregroundEnteredAt = 0L

    @Volatile
    private var reported = false

    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        isDaemon = true
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    ProcessLifecycleOwner.get().lifecycle.addObserver(
                        object : DefaultLifecycleObserver {
                            override fun onStart(owner: LifecycleOwner) {
                                isAppForeground = true
                                foregroundEnteredAt = System.currentTimeMillis()
                                lastResponseAt = System.currentTimeMillis()
                                pendingPostAt = 0L
                                reported = false
                            }

                            override fun onStop(owner: LifecycleOwner) {
                                isAppForeground = false
                            }
                        },
                    )
                } catch (_: Throwable) {
                }
            }
        } catch (_: Throwable) {
        }
    }

    override fun run() {
        lastResponseAt = System.currentTimeMillis()
        while (!isInterrupted) {
            try {
                sleep(1000L)
            } catch (_: InterruptedException) {
                return
            }

            if (!isAppForeground) {
                lastResponseAt = System.currentTimeMillis()
                pendingPostAt = 0L
                reported = false
                continue
            }

            val sinceForeground = System.currentTimeMillis() - foregroundEnteredAt
            if (sinceForeground < settleAfterForegroundMs) {
                lastResponseAt = System.currentTimeMillis()
                pendingPostAt = 0L
                reported = false
                continue
            }

            if (pendingPostAt == 0L) {
                pendingPostAt = System.currentTimeMillis()
                mainHandler.post {
                    lastResponseAt = System.currentTimeMillis()
                    pendingPostAt = 0L
                    reported = false
                }
                continue
            }

            val waited = System.currentTimeMillis() - pendingPostAt
            if (waited >= timeoutMs && !reported && isAppForeground) {
                reported = true
                val mainThread = Looper.getMainLooper().thread
                val stackTrace = mainThread.stackTrace
                val sb = StringBuilder("ANR detected. Main thread blocked for ${timeoutMs}ms\n")
                for (element in stackTrace) {
                    sb.append("    at $element\n")
                }
                Logger.e("ANR-Watchdog", sb.toString())
            }
        }
    }
}
