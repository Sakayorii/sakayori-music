package com.sakayori.logger

import co.touchlab.kermit.Logger

object Logger {
    private val logger = Logger

    fun d(
        tag: String,
        message: String,
    ) {
        logger.d(messageString = message, tag = tag)
    }

    fun i(
        tag: String,
        message: String,
    ) {
        logger.i(messageString = message, tag = tag)
    }

    fun w(
        tag: String,
        message: String,
    ) {
        logger.w(messageString = message, tag = tag)
    }

    fun e(
        tag: String,
        message: String,
        e: Throwable? = null,
    ) {
        logger.e(messageString = message, throwable = e, tag = tag)
    }
}

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR
}
