package com.sakayori.music.utils

import java.io.File
import java.util.UUID
import kotlin.math.abs

actual object DeviceId {
    private var cached: String? = null

    actual fun stableId(): String {
        cached?.let { return it }
        val resolved = try {
            val dir = File(System.getProperty("user.home"), ".sakayori-music").apply { mkdirs() }
            val marker = File(dir, "device-id")
            if (marker.exists()) {
                marker.readText().trim().ifBlank { writeNew(marker) }
            } else {
                writeNew(marker)
            }
        } catch (_: Throwable) {
            UUID.randomUUID().toString()
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

    private fun writeNew(marker: File): String {
        val id = UUID.randomUUID().toString()
        marker.writeText(id)
        return id
    }
}
