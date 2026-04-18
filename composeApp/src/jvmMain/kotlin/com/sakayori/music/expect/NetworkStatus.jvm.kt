package com.sakayori.music.expect

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.InetAddress

actual fun networkStatusFlow(): Flow<Boolean> = flow {
    while (true) {
        val online = try {
            InetAddress.getByName("1.1.1.1").isReachable(2000)
        } catch (_: Exception) {
            false
        }
        emit(online)
        delay(10_000)
    }
}.distinctUntilChanged().flowOn(Dispatchers.IO)
