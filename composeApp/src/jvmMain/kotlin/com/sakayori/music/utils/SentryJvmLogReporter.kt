package com.sakayori.music.utils

import com.sakayori.logger.LogLevel
import com.sakayori.logger.LogReporter
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel

class SentryJvmLogReporter : LogReporter {
    override fun onLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!DesktopCrashReporting.isEnabled()) return
        when (level) {
            LogLevel.ERROR -> {
                if (throwable != null) {
                    Sentry.captureException(throwable) { scope ->
                        scope.setTag("logger.tag", tag)
                        scope.setExtra("logger.message", message)
                    }
                } else {
                    Sentry.captureMessage(message, SentryLevel.ERROR) { scope ->
                        scope.setTag("logger.tag", tag)
                    }
                }
            }
            LogLevel.WARN -> {
                Sentry.addBreadcrumb(
                    Breadcrumb().apply {
                        category = tag
                        setMessage(message)
                        this.level = SentryLevel.WARNING
                    },
                )
            }
            LogLevel.INFO, LogLevel.DEBUG -> {
            }
        }
    }
}
