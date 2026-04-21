package com.sakayori.music.utils

import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.SentryOptions
import java.util.concurrent.atomic.AtomicBoolean

object DesktopCrashReporting {
    private val enabled = AtomicBoolean(false)

    fun setEnabled(value: Boolean) {
        enabled.set(value)
    }

    fun isEnabled(): Boolean = enabled.get()

    fun init(dsn: String, version: String) {
        if (dsn.isEmpty()) return
        Sentry.init { options ->
            options.dsn = dsn
            options.release = "sakayorimusic-desktop@$version"
            options.setDiagnosticLevel(SentryLevel.ERROR)
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                if (enabled.get()) event else null
            }
        }
    }
}
