package com.sakayori.music.crashlytics

import android.content.Context
import android.util.Log
import com.sakayori.domain.data.player.PlayerError
import com.sakayori.logger.LogLevel
import com.sakayori.logger.LogReporter
import com.sakayori.logger.Logger
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import java.util.concurrent.atomic.AtomicBoolean

private val reportingEnabled = AtomicBoolean(false)

fun setCrashReportingEnabled(enabled: Boolean) {
    reportingEnabled.set(enabled)
}

fun reportCrash(throwable: Throwable) {
    if (reportingEnabled.get()) Sentry.captureException(throwable)
}

fun configCrashlytics(applicationContext: Context, dsn: String) {
    SentryAndroid.init(applicationContext) { options ->
        Log.d("Sentry", "dsn: $dsn")
        options.dsn = dsn
        options.isEnableAutoSessionTracking = true
        options.isAnrEnabled = true
        options.anrTimeoutIntervalMillis = 5000L
        options.beforeSend = io.sentry.SentryOptions.BeforeSendCallback { event, _ ->
            if (reportingEnabled.get()) event else null
        }
    }
}

fun pushPlayerError(error: PlayerError) {
    if (!reportingEnabled.get()) return
    Sentry.withScope { _ ->
        Sentry.captureMessage("Player Error: ${error.message}, code: ${error.errorCode}, code name: ${error.errorCodeName}")
    }
}

fun installLogReporter() {
    Logger.installReporter(SentryAndroidLogReporter())
}

private class SentryAndroidLogReporter : LogReporter {
    override fun onLog(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (!reportingEnabled.get()) return
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
