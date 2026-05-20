package com.sakayori.music.utils

import com.sakayori.music.BuildKonfig

object VersionManager {
    private var versionName: String? = null

    fun initialize() {
        if (versionName == null) {
            versionName =
                try {
                    BuildKonfig.versionName
                } catch (_: Exception) {
                    String()
                }
        }
    }

    fun getVersionName(): String = removeDevSuffix(versionName ?: String())

    fun isRemoteNewerThan(remoteTag: String, currentVersion: String): Boolean {
        val remote = parseSemver(remoteTag) ?: return false
        val current = parseSemver(currentVersion) ?: return true
        for (i in 0 until maxOf(remote.size, current.size)) {
            val r = remote.getOrElse(i) { 0 }
            val c = current.getOrElse(i) { 0 }
            if (r != c) return r > c
        }
        return false
    }

    private fun parseSemver(raw: String): List<Int>? {
        val cleaned = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (cleaned.isBlank()) return null
        val parts = cleaned.split('.')
        if (parts.isEmpty()) return null
        return parts.mapNotNull { it.toIntOrNull() }.takeIf { it.size == parts.size }
    }

    private fun removeDevSuffix(versionName: String): String {
        return if (versionName.endsWith("-dev")) {
            versionName.replace("-dev", "")
        } else {
            versionName
        }
    }
}
