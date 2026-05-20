package com.sakayori.music.utils

expect object DeviceId {
    fun stableId(): String

    fun anonymousDisplayId(): String

    fun resolveUserDisplayId(name: String, conflictSuffix: String? = null): String
}
