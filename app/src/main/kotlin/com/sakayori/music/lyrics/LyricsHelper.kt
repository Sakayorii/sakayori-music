/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.lyrics

import android.content.Context
import android.util.LruCache
import androidx.datastore.preferences.core.Preferences
import com.sakayori.music.constants.LyricsProviderOrderKey
import com.sakayori.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.sakayori.music.models.MediaMetadata
import com.sakayori.music.utils.NetworkConnectivityObserver
import com.sakayori.music.utils.dataStore
import com.sakayori.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

private const val MAX_LYRICS_FETCH_MS = 25000L
private const val PER_PROVIDER_TIMEOUT_MS = 8000L
private const val PROVIDER_NONE = ""

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    val preferred =
        context.dataStore.data
            .map { preferences ->
                resolveLyricsProviders(preferences)
            }.distinctUntilChanged()

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null
    private class FetchGate {
        val mutex = Mutex()
        var callers = 0
        var result: LyricsWithProvider? = null
    }
    private val fetchGates = mutableMapOf<String, FetchGate>()
    private val fetchGatesLock = Any()

    // Single key derivation for the lyrics LRU: getLyrics() and getAllLyrics()
    // must use the same key or cache reads never hit (previously getLyrics read
    // by mediaMetadata.id while puts used the artists-title key).
    private fun lyricsCacheKey(mediaId: String, songTitle: String, songArtists: String) =
        "$mediaId-$songArtists-$songTitle".replace(" ", "")

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        currentLyricsJob?.cancel()

        val cacheKey = lyricsCacheKey(mediaMetadata.id, mediaMetadata.title, mediaMetadata.artists.joinToString { it.name })
        val cached = cache.get(cacheKey)?.firstOrNull()
        if (cached != null) {
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        val gate = synchronized(fetchGatesLock) {
            fetchGates.getOrPut(cacheKey) { FetchGate() }.also { it.callers++ }
        }
        try {
            return gate.mutex.withLock {
                gate.result?.let { return@withLock it }
                val shared = cache.get(cacheKey)?.firstOrNull()
                val result = if (shared != null) LyricsWithProvider(shared.lyrics, shared.providerName)
                    else fetchLyrics(mediaMetadata, cacheKey)
                gate.result = result
                result
            }
        } finally {
            synchronized(fetchGatesLock) {
                gate.callers--
                if (gate.callers == 0 && fetchGates[cacheKey] === gate) fetchGates.remove(cacheKey)
            }
        }
    }

    private suspend fun fetchLyrics(mediaMetadata: MediaMetadata, cacheKey: String): LyricsWithProvider {
        val preferences = context.dataStore.data.first()
        val orderedProviders = resolveLyricsProviders(preferences)

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) {
            return LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        val result = withTimeoutOrNull(MAX_LYRICS_FETCH_MS) {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(mediaMetadata.title)
            val enabledProviders = orderedProviders.filter { isEnabled(it, preferences) }

            Timber.tag("LyricsHelper").d("Starting sequential fetch for: $cleanedTitle by ${mediaMetadata.artists.joinToString { it.name }}")
            Timber.tag("LyricsHelper").d("Enabled providers in order: ${enabledProviders.joinToString { it.name }}")

            for (provider in enabledProviders) {
                Timber.tag("LyricsHelper").d("Trying provider: ${provider.name}")
                val providerResult = try {
                    withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                        provider.getLyrics(
                            context,
                            mediaMetadata.id,
                            cleanedTitle,
                            mediaMetadata.artists.joinToString { it.name },
                            mediaMetadata.duration,
                            mediaMetadata.album?.title,
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag("LyricsHelper").w("${provider.name} threw: ${e.message}")
                    null
                }

                if (providerResult != null && providerResult.isSuccess) {
                    Timber.tag("LyricsHelper").i("Got lyrics from ${provider.name}")
                    val filtered = LyricsUtils.filterLyricsCreditLines(providerResult.getOrNull()!!)
                    cache.put(cacheKey, listOf(LyricsResult(provider.name, filtered)))
                    return@withTimeoutOrNull LyricsWithProvider(filtered, provider.name)
                } else {
                    val errorMsg = providerResult?.exceptionOrNull()?.message ?: "timeout or exception"
                    Timber.tag("LyricsHelper").w("${provider.name} failed: $errorMsg")
                }
            }

            Timber.tag("LyricsHelper").w("No lyrics found after checking all providers")
            LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
        }

        return result ?: LyricsWithProvider(LYRICS_NOT_FOUND, PROVIDER_NONE)
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        currentLyricsJob?.cancel()

        val cacheKey = lyricsCacheKey(mediaId, songTitle, songArtists)
        cache.get(cacheKey)?.let { results ->
            results.forEach { callback(it) }
            return
        }

        val isNetworkAvailable = try {
            networkConnectivity.isCurrentlyConnected()
        } catch (e: Exception) {
            true
        }

        if (!isNetworkAvailable) return

        val allResult = mutableListOf<LyricsResult>()
        currentLyricsJob = CoroutineScope(SupervisorJob(coroutineContext[Job])).launch {
            val cleanedTitle = LyricsUtils.cleanTitleForSearch(songTitle)
            val preferences = context.dataStore.data.first()
            val allProviders = resolveLyricsProviders(preferences)
            val enabledProviders = allProviders.filter { isEnabled(it, preferences) }

            val otherProviders = enabledProviders.filter { it.name != "LyricsPlus" }
            val lyricsPlusProvider = enabledProviders.find { it.name == "LyricsPlus" }

            val callbackMutex = Any()

            val otherJobs = otherProviders.map { provider ->
                launch {
                    try {
                        withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                            provider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                                val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                                val result = LyricsResult(provider.name, filteredLyrics)
                                synchronized(callbackMutex) {
                                    allResult += result
                                    callback(result)
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            otherJobs.forEach { it.join() }

            val otherLyricsCount = allResult.count { it.providerName != "LyricsPlus" }
            if (lyricsPlusProvider != null && otherLyricsCount <= 2) {
                launch {
                    try {
                        withTimeoutOrNull(PER_PROVIDER_TIMEOUT_MS) {
                            lyricsPlusProvider.getAllLyrics(context, mediaId, cleanedTitle, songArtists, duration, album) { lyrics ->
                                val filteredLyrics = LyricsUtils.filterLyricsCreditLines(lyrics)
                                val result = LyricsResult(lyricsPlusProvider.name, filteredLyrics)
                                synchronized(callbackMutex) {
                                    allResult += result
                                    callback(result)
                                }
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }.join()
            }

            if (allResult.isNotEmpty()) {
                cache.put(cacheKey, allResult)
            }
        }

        currentLyricsJob?.join()
    }

    private suspend fun isEnabled(provider: LyricsProvider, preferences: Preferences): Boolean =
        LyricsProviderRegistry.enabledFromSnapshot(provider, preferences)
            ?: withContext(Dispatchers.IO) { provider.isEnabled(context) }

    private fun resolveLyricsProviders(preferences: Preferences): List<LyricsProvider> {
        val providerOrder = preferences[LyricsProviderOrderKey].orEmpty()
        if (providerOrder.isNotBlank()) {
            return LyricsProviderRegistry.getOrderedProviders(providerOrder)
        }

        return LyricsProviderRegistry.getDefaultProviderOrder()
            .mapNotNull { LyricsProviderRegistry.getProviderByName(it) }
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
