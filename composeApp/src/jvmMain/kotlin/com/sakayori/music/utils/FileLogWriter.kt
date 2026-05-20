package com.sakayori.music.utils

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity

class FileLogWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        when (severity) {
            Severity.Verbose, Severity.Debug -> FileLogger.d(tag, message)
            Severity.Info -> FileLogger.i(tag, message)
            Severity.Warn -> FileLogger.w(tag, message)
            Severity.Error, Severity.Assert -> FileLogger.e(tag, message, throwable)
        }
    }
}
