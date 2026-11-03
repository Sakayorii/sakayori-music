/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.os.SystemClock
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.di.ApplicationScope
import com.sakayori.music.extensions.SilentHandler
import com.sakayori.music.models.MediaMetadata
import com.sakayori.music.models.toMediaMetadata
import com.sakayori.innertube.YouTube
import com.sakayori.music.playback.flac.LosslessSourceRegistry
import com.sakayori.music.playback.flac.FlacPlaybackCapabilities
import com.sakayori.music.playback.flac.FlacPlaybackCapability
import com.sakayori.music.playback.flac.FlacResource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

internal enum class LosslessLookupState { PENDING, AVAILABLE, UNAVAILABLE, TIMED_OUT, SUPERSEDED, AT_CAPACITY, FAILED }

internal data class LosslessLookup(
    val deadlineMs: Long,
    val result: Deferred<FlacResource?>,
    val state: StateFlow<LosslessLookupState>,
)

/** Optional lookup, bounded independently from the normal playback resolver. */
@Singleton
class LosslessResolver @Inject constructor(
    private val database: MusicDatabase,
    private val store: AudioRenditionStore,
    internal val source: LosslessSourceRegistry,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private data class Entry(val mediaId: String, val resource: FlacResource?, val expiresAt: Long)
    private data class Pending(
        val mediaId: String,
        val lookup: LosslessLookup,
        val job: Job,
        val priority: MutableStateFlow<StreamRequestPriority>,
        var forDownload: Boolean,
    )
    private val lock = Any()
    private val metadata = LinkedHashMap<String, MediaMetadata>(0, 0.75f, true)
    private val completed = LinkedHashMap<String, Entry>(0, 0.75f, true)
    private val pending = mutableMapOf<String, Pending>()
    private val blocked = LinkedHashMap<String, Long>()
    private val failureTokens = ConcurrentHashMap<String, Long>()
    private val failureSequence = AtomicLong()
    private val permits = StreamResolutionLimiter(maxConcurrent = 2, maxBackground = 1)

    init {
        applicationScope.launch(Dispatchers.IO + SilentHandler) {
            // The first settings load is initialization, not a user changing sources. Lookups
            // already waiting for it must be allowed to use that initial configured endpoint.
            source.awaitConfiguration()
            source.configuration.drop(1).collectLatest {
                synchronized(lock) {
                    completed.clear()
                    blocked.clear()
                    failureTokens.clear()
                    pending.values.filter { !it.forDownload }.map { it.job }.forEach { it.cancel() }
                }
            }
        }
    }

    internal fun remember(item: MediaMetadata) = synchronized(lock) {
        metadata[item.id] = item
        trim(metadata, 512)
    }

    internal fun isBlocked(mediaId: String): Boolean = synchronized(lock) {
        val until = blocked[mediaId] ?: return@synchronized false
        if (until > SystemClock.elapsedRealtime()) true else { blocked.remove(mediaId); false }
    }

    internal fun cached(mediaId: String): FlacResource? {
        if (isBlocked(mediaId)) return null
        val rendition = store.playback(mediaId, AudioRendition.DIRECT_FLAC_PROFILE) ?: return null
        return restore(rendition)?.takeIf(source::isCurrentResource)
    }

    internal fun restore(rendition: AudioRendition): FlacResource? = source.restore(rendition)?.takeIf {
        FlacPlaybackCapabilities.inspect(it.streamInfo) == FlacPlaybackCapability.SUPPORTED
    }

    /** No disk access: the active player can use an already validated in-memory result. */
    internal fun ready(mediaId: String): FlacResource? = synchronized(lock) {
        if (isBlocked(mediaId)) return@synchronized null
        val item = metadata[mediaId]
        completed[lookupKey(mediaId, item, source.configuration.value)]
            ?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() }?.resource?.takeIf(source::isCurrentResource)
    }

    internal fun lookup(mediaId: String, forDownload: Boolean = false): Deferred<FlacResource?> =
        request(mediaId, forDownload, if (forDownload) StreamRequestPriority.BACKGROUND else StreamRequestPriority.FOREGROUND).result

    internal fun request(
        mediaId: String,
        forDownload: Boolean = false,
        priority: StreamRequestPriority = StreamRequestPriority.FOREGROUND,
    ): LosslessLookup {
        // Start the single absolute window before dispatch, configuration, or permit waits.
        val deadline = SystemClock.elapsedRealtime() + LOOKUP_BUDGET_MS
        fun finished(value: FlacResource?, state: LosslessLookupState) = LosslessLookup(
            deadline, CompletableDeferred(value), MutableStateFlow(state).asStateFlow(),
        )
        if (!forDownload && isBlocked(mediaId)) return finished(null, LosslessLookupState.UNAVAILABLE)
        val item = synchronized(lock) { metadata[mediaId] }
        if (item != null && !eligible(item)) {
            return finished(null, LosslessLookupState.UNAVAILABLE)
        }
        val contextRevision = YouTube.requestContextRevision.value
        val sourceConfiguration = if (source.isConfigurationReady) source.configuration.value else null
        val scopedKey = lookupKey(mediaId, item, sourceConfiguration, contextRevision)
        return synchronized(lock) {
            completed[scopedKey]?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() }?.let {
                return@synchronized finished(it.resource, if (it.resource != null) LosslessLookupState.AVAILABLE else LosslessLookupState.UNAVAILABLE)
            }
            (pending[scopedKey] ?: if (sourceConfiguration != null) pending[lookupKey(mediaId, item, null, contextRevision)] else null)?.let {
                it.forDownload = it.forDownload || forDownload
                if (priority == StreamRequestPriority.FOREGROUND) it.priority.value = priority
                return@synchronized it.lookup
            }
            // Two background owners cannot consume the active playback reservation. Download
            // owners remain independent; capacity refusal is not cached as a missing recording.
            if (pending.size >= 3 || (priority == StreamRequestPriority.BACKGROUND && pending.size >= 2)) {
                return@synchronized finished(null, LosslessLookupState.AT_CAPACITY)
            }
            val requestPriority = MutableStateFlow(priority)
            val state = MutableStateFlow(LosslessLookupState.PENDING)
            val answer = CompletableDeferred<FlacResource?>()
            val task = applicationScope.launch(Dispatchers.IO, start = CoroutineStart.LAZY) {
                var lookupCompleted = false
                var resolvedConfiguration = sourceConfiguration
                fun blockedForOwner() = isBlocked(mediaId) && synchronized(lock) { pending[scopedKey]?.forDownload != true }
                fun publish(resource: FlacResource): Boolean = synchronized(lock) {
                    val validatedAt = SystemClock.elapsedRealtime()
                    if (validatedAt > deadline || blockedForOwner() || source.configuration.value != resolvedConfiguration ||
                        YouTube.requestContextRevision.value != contextRevision || answer.isCompleted
                    ) return@synchronized false
                    completed[lookupKey(mediaId, item, resolvedConfiguration, contextRevision)] =
                        Entry(mediaId, resource, validatedAt + RESULT_TTL_MS)
                    trim(completed, 128)
                    state.value = LosslessLookupState.AVAILABLE
                    answer.complete(resource)
                    true
                }
                val resource = try {
                    withTimeoutOrNull((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L)) {
                        source.awaitConfiguration()
                        val configured = sourceConfiguration ?: source.configuration.value
                        resolvedConfiguration = configured
                        if (source.configuration.value != configured || YouTube.requestContextRevision.value != contextRevision) {
                            state.value = LosslessLookupState.SUPERSEDED
                            return@withTimeoutOrNull null
                        }
                        permits.withPermit(requestPriority) {
                            if (source.configuration.value != configured || YouTube.requestContextRevision.value != contextRevision) {
                                state.value = LosslessLookupState.SUPERSEDED
                                return@withPermit null
                            }
                            val target = item ?: database.getSongById(mediaId)?.toMediaMetadata() ?: run {
                                lookupCompleted = true
                                return@withPermit null
                            }
                            if (!eligible(target)) {
                                lookupCompleted = true
                                return@withPermit null
                            }
                            val song = database.songEntity(mediaId)
                            if (song?.isUploaded == true || song?.isLocal == true || song?.isEpisode == true || song?.isVideo == true) {
                                lookupCompleted = true
                                null
                            } else {
                                val saved = cached(mediaId)
                                val usable = saved != null && (store.completeInEither(saved.rendition()) || try {
                                    source.validate(saved)
                                } catch (cancelled: CancellationException) { throw cancelled }
                                catch (_: Exception) { false })
                                val result = if (usable) saved?.also { publish(it) }
                                    else source.find(target, configured, requestPriority, onValidated = { publish(it) })
                                lookupCompleted = true
                                result
                            }
                        }
                    }
                } catch (cancelled: CancellationException) {
                    if (!answer.isCompleted) {
                        state.value = LosslessLookupState.SUPERSEDED
                        answer.cancel(cancelled)
                    }
                    throw cancelled
                } catch (_: Exception) {
                    state.value = LosslessLookupState.FAILED
                    null
                }
                currentCoroutineContext().ensureActive()
                if (answer.isCompleted) return@launch
                val current = !blockedForOwner() && source.configuration.value == resolvedConfiguration &&
                    YouTube.requestContextRevision.value == contextRevision
                val insideWindow = SystemClock.elapsedRealtime() <= deadline
                val accepted = resource?.takeIf { current && insideWindow }
                if (accepted != null && publish(accepted)) return@launch
                if (!current) state.value = LosslessLookupState.SUPERSEDED
                else if (state.value == LosslessLookupState.PENDING) {
                    state.value = if (!insideWindow || !lookupCompleted) LosslessLookupState.TIMED_OUT else LosslessLookupState.UNAVAILABLE
                }
                // Result delivery does not wait for SQLite or metadata publication. The actual
                // selected reader binds its physical identity before writing its first bytes.
                synchronized(lock) {
                    if (current && insideWindow && state.value in setOf(LosslessLookupState.AVAILABLE, LosslessLookupState.UNAVAILABLE)) {
                        val ttlMs = if (accepted == null) 60_000L else RESULT_TTL_MS
                        completed[lookupKey(mediaId, item, resolvedConfiguration, contextRevision)] = Entry(mediaId, accepted, SystemClock.elapsedRealtime() + ttlMs)
                        trim(completed, 128)
                    }
                }
                answer.complete(null)
            }
            val lookup = LosslessLookup(deadline, answer, state.asStateFlow())
            pending[scopedKey] = Pending(mediaId, lookup, task, requestPriority, forDownload)
            // Delivery has its own deadline signal. Even a non-cooperative cache/database read
            // cannot keep the public result pending after its lookup window has closed.
            val expiry = applicationScope.launch {
                delay((deadline - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
                synchronized(lock) {
                    if (!answer.isCompleted) {
                        state.value = LosslessLookupState.TIMED_OUT
                        answer.complete(null)
                        task.cancel()
                    }
                }
            }
            answer.invokeOnCompletion {
                expiry.cancel()
                if (answer.isCancelled) task.cancel()
            }
            task.invokeOnCompletion {
                synchronized(lock) {
                    if (!answer.isCompleted) {
                        state.value = LosslessLookupState.SUPERSEDED
                        answer.complete(null)
                    }
                    if (pending[scopedKey]?.lookup === lookup) pending.remove(scopedKey)
                }
            }
            task.start()
            lookup
        }
    }

    private fun lookupKey(mediaId: String, item: MediaMetadata?, configuration: String?, revision: Long = YouTube.requestContextRevision.value) =
        identityDigest(mediaId, item?.title, item?.artists?.joinToString { it.name }, item?.album?.title,
            item?.duration?.toString(), configuration, revision.toString())

    internal fun beginPlayback(mediaId: String) = synchronized(lock) {
        blocked.remove(mediaId)
        failureTokens.remove(mediaId)
        Unit
    }

    /** A failed optional stream gets one normal-quality handoff rather than a repeated upgrade loop. */
    internal fun failed(rendition: AudioRendition, forDownload: Boolean = false) {
        if (forDownload) synchronized(lock) {
            completed.entries.removeAll { it.value.resource?.rendition()?.cacheKey == rendition.cacheKey }
        } else failed(rendition.mediaId, rendition)
    }

    internal fun failed(mediaId: String, rendition: AudioRendition? = null) {
        val token = failureSequence.incrementAndGet()
        val configuration = source.configuration.value
        synchronized(lock) {
            failureTokens[mediaId] = token
            blocked[mediaId] = SystemClock.elapsedRealtime() + RESULT_TTL_MS
            trim(blocked, 128)
            failureTokens.keys.retainAll(blocked.keys)
            completed.entries.removeAll { it.value.mediaId == mediaId }
            pending.values.filter { it.mediaId == mediaId && !it.forDownload }.map { it.job }.forEach { it.cancel() }
        }
        applicationScope.launch(Dispatchers.IO + SilentHandler) {
            val saved = rendition ?: store.playback(mediaId, AudioRendition.DIRECT_FLAC_PROFILE)
            saved?.let {
                store.forgetPlayback(mediaId, AudioRendition.DIRECT_FLAC_PROFILE, it.cacheKey) {
                    failureTokens[mediaId] == token && source.configuration.value == configuration
                }
            }
        }
    }

    internal fun cancelPending() = synchronized(lock) {
        pending.values.filter { !it.forDownload }.map { it.job }.forEach { it.cancel() }
    }

    internal fun retainPlaybackLookups(mediaIds: Set<String>) = synchronized(lock) {
        pending.values.filter { !it.forDownload && it.mediaId !in mediaIds }.map { it.job }.forEach { it.cancel() }
    }

    private fun <K, V> trim(map: LinkedHashMap<K, V>, limit: Int) {
        while (map.size > limit) map.entries.iterator().apply { next(); remove() }
    }

    private fun eligible(item: MediaMetadata): Boolean = !item.isEpisode && !item.isVideoSong &&
        item.uploadEntityId == null && item.title.isNotBlank() && item.artists.isNotEmpty() && item.duration > 0

    private companion object {
        const val LOOKUP_BUDGET_MS = 4_000L
        const val RESULT_TTL_MS = 15 * 60_000L
    }
}
