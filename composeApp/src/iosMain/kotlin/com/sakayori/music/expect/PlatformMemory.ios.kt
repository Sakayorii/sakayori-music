@file:OptIn(kotlin.native.runtime.NativeRuntimeApi::class)

package com.sakayori.music.expect

actual fun platformRequestGc() {
    kotlin.native.runtime.GC.collect()
}
