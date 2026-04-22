package com.sakayori.music.expect

actual fun platformRequestGc() {
    try {
        Runtime.getRuntime().gc()
    } catch (_: Throwable) {
    }
}
