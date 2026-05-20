package com.sakayori.music.utils

import platform.UIKit.UIDevice
import platform.Foundation.NSUUID
import kotlin.math.abs

actual object DeviceId {
    private var cached: String? = null

    actual fun stableId(): String {
        cached?.let { return it }
        val resolved = try {
            UIDevice.currentDevice.identifierForVendor?.UUIDString ?: NSUUID().UUIDString
        } catch (_: Throwable) {
            NSUUID().UUIDString
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
}
