/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import com.sakayori.innertube.YouTube
import okhttp3.OkHttpClient
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton

/** Reuse connections across cache misses and downloads; rebuild only when proxy settings change. */
@Singleton
class PlaybackHttpClient @Inject constructor() {
    fun get(): OkHttpClient = shared()

    companion object {
        private data class Settings(val proxy: Proxy?, val credentials: String?)
        private var settings: Settings? = null
        private var client: OkHttpClient? = null

        @Synchronized
        internal fun shared(): OkHttpClient {
            val next = Settings(YouTube.proxy, YouTube.proxyAuth)
            if (settings == next) client?.let { return it }
            return OkHttpClient.Builder()
                .proxy(next.proxy)
                .addInterceptor { chain -> chain.proceed(chain.request()).withBoundedErrorBody() }
                .proxyAuthenticator { _, response ->
                    val credentials = next.credentials ?: return@proxyAuthenticator null
                    if (response.request.header("Proxy-Authorization") == credentials) null
                    else response.request.newBuilder().header("Proxy-Authorization", credentials).build()
                }
                .build()
                .also { settings = next; client = it }
        }
    }
}
