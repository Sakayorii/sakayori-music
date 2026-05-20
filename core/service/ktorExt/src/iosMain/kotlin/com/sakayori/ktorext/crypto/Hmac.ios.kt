package com.sakayori.ktorext.crypto

actual class Hmac actual constructor(algorithm: String, secretKey: String) {
    actual fun getMacTimestampPair(uri: String): Pair<String, String> {
        return Pair("", "")
    }

    actual fun generateHmac(data: String): String {
        return ""
    }

    actual fun validateHmac(data: String, hmac: String): Boolean {
        return false
    }

    actual fun isValidTimestamp(timestamp: String): Boolean {
        return false
    }
}
