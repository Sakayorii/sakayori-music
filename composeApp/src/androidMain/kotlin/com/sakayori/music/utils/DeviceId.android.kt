package com.sakayori.music.utils

import android.content.Context
import android.provider.Settings
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.math.abs

actual object DeviceId : KoinComponent {
    private var cached: String? = null

    actual fun stableId(): String {
        cached?.let { return it }
        val resolved = try {
            val context: Context = get()
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotBlank() && it != "9774d56d682e549c" }
                ?: fallbackUuid()
        } catch (_: Throwable) {
            fallbackUuid()
        }
        cached = resolved
        return resolved
    }

    actual fun anonymousDisplayId(): String {
        val raw = stableId()
        val hash = abs(raw.hashCode())
        val digits = (hash % 1_000_000).toString().padStart(6, '0')
        return "anonymous-$digits"
    }

    actual fun resolveUserDisplayId(name: String, conflictSuffix: String?): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return anonymousDisplayId()
        if (conflictSuffix.isNullOrBlank()) return trimmed
        return "$trimmed-${conflictSuffix.padStart(3, '0').takeLast(3)}"
    }

    private fun fallbackUuid(): String = java.util.UUID.randomUUID().toString()
}
