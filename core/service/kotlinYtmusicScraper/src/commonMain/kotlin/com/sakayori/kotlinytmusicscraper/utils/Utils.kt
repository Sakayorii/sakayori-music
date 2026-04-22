package com.sakayori.kotlinytmusicscraper.utils

import com.sakayori.kotlinytmusicscraper.models.response.AudioData
import com.sakayori.logger.Logger
import kotlinx.serialization.json.Json
import okio.ByteString.Companion.encodeUtf8
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> (eachByte.toInt() and 0xff).toString(16).padStart(2, '0') }

fun sha1(str: String): String = str.encodeUtf8().sha1().hex()

fun parseCookieString(cookie: String): Map<String, String> =
    cookie
        .split("; ")
        .filter { it.isNotEmpty() }
        .associate {
            val parts = it.split("=", limit = 2)
            val key = parts.getOrNull(0) ?: ""
            val value = parts.getOrNull(1) ?: ""
            key to value
        }

fun String.parseTime(): Int? {
    try {
        val parts =
            if (this.contains(":")) split(":").map { it.toInt() } else split(".").map { it.toInt() }
        if (parts.size == 2) {
            return parts[0] * 60 + parts[1]
        }
        if (parts.size == 3) {
            return parts[0] * 3600 + parts[1] * 60 + parts[2]
        }
    } catch (e: Exception) {
        return null
    }
    return null
}

@OptIn(ExperimentalTime::class)
fun generateNetscapeCookies(
    cookies: Map<String, String>,
    domain: String = ".example.com",
    path: String = "/",
    secure: Boolean = false,
    httpOnly: Boolean = false,
    expirationTimeSeconds: Long = Clock.System.now().epochSeconds + 86400 * 365,
): String {
    val header =
        "# Netscape HTTP Cookie File\n" +
            "# This is a generated file! Do not edit.\n\n"

    val cookieLines =
        cookies
            .map { (name, value) ->
                buildString {
                    append(domain)
                    append("\t")
                    append("TRUE")
                    append("\t")
                    append(path)
                    append("\t")
                    append(if (secure) "TRUE" else "FALSE")
                    append("\t")
                    append(expirationTimeSeconds)
                    append("\t")
                    append(name)
                    append("\t")
                    append(value)
                }
            }.joinToString("\n")

    return header + cookieLines
}

fun String.decodeTidalManifest(): AudioData? {
    val decodedBytes = Base64.decode(this)
    val jsonString = decodedBytes.decodeToString()
    val json =
        Json {
            explicitNulls = false
            ignoreUnknownKeys = true
        }
    return try {
        json.decodeFromString<AudioData?>(jsonString)
    } catch (e: Exception) {
        Logger.e("Utils", "Failed to decode Tidal manifest: ${e.message}")
        null
    }
}

fun String.decodeBase64(): String {
    val decodedBytes = Base64.decode(this)
    return decodedBytes.decodeToString()
}
