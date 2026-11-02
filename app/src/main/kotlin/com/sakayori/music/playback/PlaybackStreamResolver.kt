/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.content.Context
import android.net.ConnectivityManager
import android.os.SystemClock
import androidx.core.content.getSystemService
import com.sakayori.innertube.YouTube
import com.sakayori.innertube.strategy.ContentHints
import com.sakayori.music.constants.AudioQuality
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.utils.YTPlayerUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

internal data class ResolvedPlaybackStream(
    val rendition: AudioRendition,
    val playback: YTPlayerUtils.PlaybackData,
    val expiresAtMs: Long,
    val request: StreamLookupKey,
)

internal data class StreamLookupKey(
    val mediaId: String,
    val quality: AudioQuality,
    val metered: Boolean,
    val revision: Long,
    val disabledClients: Set<String>,
    val requiredKey: String?,
    val generation: Long,
)

/** Shared by playback and downloads. It resolves transport only and never changes player state. */
@Singleton
class PlaybackStreamResolver @Inject constructor(
    @ApplicationContext context: Context,
    private val database: MusicDatabase,
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private class Gate(val mutex: Mutex = Mutex(), var users: Int = 0)

    private val lock = Any()
    private val gates = mutableMapOf<StreamLookupKey, Gate>()
    private val generations = mutableMapOf<String, Long>()
    private var nextGeneration = 0L
    private val results = LinkedHashMap<StreamLookupKey, ResolvedPlaybackStream>(0, 0.75f, true)
    private val concurrency = StreamResolutionLimiter()

    internal fun profile(quality: AudioQuality): String = when (quality) {
        AudioQuality.AUTO -> if (connectivityManager.isActiveNetworkMetered) "AUTO_METERED" else "AUTO_UNMETERED"
        else -> quality.name
    }

    fun invalidate(mediaId: String) = synchronized(lock) {
        results.keys.removeAll { it.mediaId == mediaId }
        // Missing is invalid, not generation zero. Factories may still hold an old response after
        // its cache entry was evicted; it must not become valid again on a later request.
        generations.remove(mediaId)
    }

    fun clientName(mediaId: String): String? = synchronized(lock) {
        results.values.lastOrNull { it.rendition.mediaId == mediaId && isReusable(it) }?.playback?.streamClient
    }

    internal fun canReuse(stream: ResolvedPlaybackStream): Boolean = synchronized(lock) {
        isReusable(stream) && results[stream.request] === stream
    }

    /** A late failure from an older URL must not discard a newer successful lease. */
    internal fun invalidateRejected(stream: ResolvedPlaybackStream) = synchronized(lock) {
        results.entries.removeAll { (_, cached) ->
            cached.request.copy(requiredKey = stream.request.requiredKey) == stream.request &&
                cached.playback.streamUrl == stream.playback.streamUrl &&
                cached.playback.streamHeaders == stream.playback.streamHeaders
        }
        // canReuse also requires the exact response still to be in results. Removing only the
        // rejected lease invalidates captured factories without cancelling a newer pinned alias.
        forgetIdleGeneration(stream.rendition.mediaId)
    }

    internal suspend fun resolve(
        mediaId: String,
        quality: AudioQuality,
        required: AudioRendition? = null,
        priority: StateFlow<StreamRequestPriority> = foregroundStreamPriority,
    ): ResolvedPlaybackStream = withContext(Dispatchers.IO) {
        val metered = connectivityManager.isActiveNetworkMetered
        val revision = YouTube.requestContextRevision.value
        val clients = YTPlayerUtils.disabledStreamClients.toSet()
        val (key, gate) = synchronized(lock) {
            val key = StreamLookupKey(
                mediaId, quality, metered, revision, clients, required?.cacheKey,
                generations.getOrPut(mediaId) { ++nextGeneration },
            )
            key to gates.getOrPut(key) { Gate() }.also { it.users++ }
        }
        try {
            gate.mutex.withLock {
                currentCoroutineContext().ensureActive()
                checkCurrent(key)
                cached(key)?.let { return@withLock it }
                if (required != null && !required.canRenewYouTube) {
                    throw RenditionUnavailableException("The cached audio has no renewable resource revision")
                }
                concurrency.withPermit(priority) {
                    checkCurrent(key)
                    cached(key)?.let { return@withPermit it }
                    val startedAt = SystemClock.elapsedRealtime()
                    val song = database.songEntity(mediaId)
                    val playback = YTPlayerUtils.playerResponseForPlayback(
                        videoId = mediaId,
                        audioQuality = quality,
                        connectivityManager = connectivityManager,
                        contentHints = ContentHints(isExplicit = song?.explicit, isUploaded = song?.isUploaded),
                        requiredFormat = required?.requiredYouTubeFormat(),
                    ).getOrThrow()
                    currentCoroutineContext().ensureActive()
                    checkCurrent(key)
                    val rendition = AudioRendition.fromYouTube(mediaId, playback)
                    if (required != null && !required.requiredYouTubeFormat().matches(playback.format)) {
                        throw RenditionUnavailableException("The cached audio rendition is no longer available")
                    }
                    val response = ResolvedPlaybackStream(
                        rendition = required?.copy(
                            bitrate = playback.format.bitrate,
                            loudnessDb = playback.audioConfig?.loudnessDb ?: required.loudnessDb,
                            perceptualLoudnessDb = playback.audioConfig?.perceptualLoudnessDb ?: required.perceptualLoudnessDb,
                            clientName = playback.streamClient,
                        ) ?: rendition,
                        playback = playback,
                        // Count resolution time against the server's lease rather than extending it.
                        expiresAtMs = startedAt + playback.streamExpiresInSeconds.coerceAtLeast(0).toLong() * 1_000L,
                        request = key,
                    )
                    synchronized(lock) {
                        checkCurrent(key)
                        results[key] = response
                        while (results.size > MAX_ENTRIES) {
                            val removed = results.entries.iterator().let { iterator -> iterator.next().key.also { iterator.remove() } }
                            forgetIdleGeneration(removed.mediaId)
                        }
                    }
                    response
                }
            }
        } finally {
            synchronized(lock) {
                gate.users--
                if (gate.users == 0 && gates[key] === gate) gates.remove(key)
                forgetIdleGeneration(key.mediaId)
            }
        }
    }

    private fun cached(key: StreamLookupKey): ResolvedPlaybackStream? = synchronized(lock) {
        results[key]?.let { if (isReusable(it)) return@synchronized it }
        results.remove(key)
        // Recheck aliases after waiting on a gate/permit too: another selection may have
        // completed while this pinned request waited. Access through get to retain LRU order.
        val matched = if (key.requiredKey == null) null else results.entries.lastOrNull { (_, response) ->
            response.request.copy(requiredKey = key.requiredKey) == key &&
                response.rendition.cacheKey == key.requiredKey && isReusable(response)
        }?.key
        matched?.let { results[it] }
    }

    private fun isReusable(stream: ResolvedPlaybackStream): Boolean =
        stream.expiresAtMs > SystemClock.elapsedRealtime() && isCurrent(stream.request)

    private fun checkCurrent(key: StreamLookupKey) {
        if (!isCurrent(key)) throw CancellationException("Stream request superseded")
    }

    private fun isCurrent(key: StreamLookupKey): Boolean =
        YouTube.requestContextRevision.value == key.revision &&
            YTPlayerUtils.disabledStreamClients == key.disabledClients &&
            (key.quality != AudioQuality.AUTO || connectivityManager.isActiveNetworkMetered == key.metered) &&
            synchronized(lock) { generations[key.mediaId] == key.generation }

    private fun forgetIdleGeneration(mediaId: String) {
        if (gates.keys.none { it.mediaId == mediaId } && results.keys.none { it.mediaId == mediaId }) generations.remove(mediaId)
    }

    private companion object {
        const val MAX_ENTRIES = 500
    }
}
