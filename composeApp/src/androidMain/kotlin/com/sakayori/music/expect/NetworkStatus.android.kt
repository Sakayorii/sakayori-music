package com.sakayori.music.expect

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.koin.mp.KoinPlatform.getKoin

actual fun networkStatusFlow(): Flow<Boolean> = callbackFlow {
    val context: Context = getKoin().get()
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (cm == null) {
        trySend(true)
        awaitClose {}
        return@callbackFlow
    }
    val current = cm.activeNetwork?.let { cm.getNetworkCapabilities(it) }
    trySend(current?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(true)
        }
        override fun onLost(network: Network) {
            trySend(false)
        }
    }
    try {
        cm.registerDefaultNetworkCallback(callback)
    } catch (_: Exception) {
        trySend(true)
    }
    awaitClose {
        try { cm.unregisterNetworkCallback(callback) } catch (_: Exception) {}
    }
}.distinctUntilChanged()
