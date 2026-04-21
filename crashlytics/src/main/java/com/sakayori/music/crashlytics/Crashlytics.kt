package com.sakayori.music.crashlytics

import android.content.Context
import android.util.Log
import com.sakayori.domain.data.player.PlayerError
import io.sentry.Sentry
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
    Sentry.withScope { scope ->
        Sentry.captureMessage("Player Error: ${error.message}, code: ${error.errorCode}, code name: ${error.errorCodeName}")
    }
}
