package com.sakayori.spotify.auth

actual fun generateTotp(secret: String, timestamp: Long): String {
    // Stub implementation for iOS until proper TOTP is available
    return ""
}
