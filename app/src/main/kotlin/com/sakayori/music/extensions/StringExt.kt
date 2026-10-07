/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.extensions

import androidx.sqlite.db.SimpleSQLiteQuery
import java.net.InetSocketAddress
import java.net.InetSocketAddress.createUnresolved
import java.text.Normalizer

private val combiningDiacriticalMarksRegex = "\\p{Mn}+".toRegex()

inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T =
    if (this == null) {
        defaultValue
    } else {
        try {
            enumValueOf(this)
        } catch (e: IllegalArgumentException) {
            defaultValue
        }
    }

fun String.toSQLiteQuery(): SimpleSQLiteQuery = SimpleSQLiteQuery(this)

fun String.normalizeForSearch(): String =
    Normalizer
        .normalize(this.trim(), Normalizer.Form.NFD)
        .replace(combiningDiacriticalMarksRegex, "")
        .lowercase()

fun matchesNormalizedQuery(normalizedQuery: String, vararg values: String?): Boolean {
    val q = normalizedQuery.trim()
    if (q.isBlank()) return true
    return values.any { value ->
        value?.normalizeForSearch()?.contains(q) == true
    }
}

fun String.toInetSocketAddress(): InetSocketAddress {
    val input = trim()
    // Bracketed IPv6: [2001:db8::1]:8080
    val bracketed = Regex("^\\[(.+)]:(\\d+)$").matchEntire(input)
    if (bracketed != null) {
        return createUnresolved(bracketed.groupValues[1], bracketed.groupValues[2].toInt())
    }
    // host:port — split on the last colon so unbracketed IPv6 (2001:db8::1:8080) parses too.
    // The old split(":") destructuring threw on any address containing more than one colon.
    val lastColon = input.lastIndexOf(':')
    require(lastColon > 0 && lastColon < input.length - 1) { "Invalid proxy address: $input" }
    val host = input.substring(0, lastColon)
    val port = input.substring(lastColon + 1).toInt()
    return createUnresolved(host, port)
}
