package com.sakayori.logger

import co.touchlab.kermit.Logger as KermitLogger

interface LogReporter {
    fun onLog(level: LogLevel, tag: String, message: String, throwable: Throwable?)
}

object Logger {
    @kotlin.concurrent.Volatile
    private var reporter: LogReporter? = null

    fun installReporter(r: LogReporter) {
        reporter = r
    }

    fun d(
        tag: String,
        message: String,
    ) {
        KermitLogger.d(messageString = message, tag = tag)
    }

    fun i(
        tag: String,
        message: String,
    ) {
        KermitLogger.i(messageString = message, tag = tag)
        try {
            reporter?.onLog(LogLevel.INFO, tag, message, null)
        } catch (_: Throwable) {
        }
    }

    fun w(
        tag: String,
        message: String,
    ) {
        KermitLogger.w(messageString = message, tag = tag)
        try {
            reporter?.onLog(LogLevel.WARN, tag, message, null)
        } catch (_: Throwable) {
        }
    }

    fun e(
        tag: String,
        message: String,
        e: Throwable? = null,
    ) {
        KermitLogger.e(messageString = message, throwable = e, tag = tag)
        try {
            reporter?.onLog(LogLevel.ERROR, tag, message, e)
        } catch (_: Throwable) {
        }
    }
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}
