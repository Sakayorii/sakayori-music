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
import com.sakayori.music.playback.flac.ArchiveFlacSource
import com.sakayori.music.playback.flac.FlacPlaybackCapabilities
import com.sakayori.music.playback.flac.FlacPlaybackCapability
import com.sakayori.music.playback.flac.FlacResource
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Optional lookup, bounded independently from the normal playback resolver. */
@Singleton
class LosslessResolver @Inject constructor(
    private val database: MusicDatabase,
    private val store: AudioRenditionStore,
    internal val source: ArchiveFlacSource,
    @ApplicationScope private val applicationScope: CoroutineScope,
) {
    private data class Entry(val mediaId: String, val resource: FlacResource?, val expiresAt: Long)
    private data class Pending(val mediaId: String, val result: Deferred<FlacResource?>, var forDownload: Boolean)
    private val lock = Any()
    private val metadata = LinkedHashMap<String, MediaMetadata>(0, 0.75f, true)
    private val completed = LinkedHashMap<String, Entry>(0, 0.75f, true)
    private val pending = mutableMapOf<String, Pending>()
    private val blocked = LinkedHashMap<String, Long>()
    private val permits = Semaphore(1)

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
        return restore(rendition)
    }

    internal fun restore(rendition: AudioRendition): FlacResource? = source.restore(rendition)?.takeIf {
        !isBlocked(rendition.mediaId) && FlacPlaybackCapabilities.inspect(it.streamInfo) == FlacPlaybackCapability.SUPPORTED
    }

    internal fun lookup(mediaId: String, forDownload: Boolean = false): Deferred<FlacResource?> {
        if (isBlocked(mediaId)) return CompletableDeferred(value = null)
        val item = synchronized(lock) { metadata[mediaId] }
        if (item != null && !eligible(item)) {
            return CompletableDeferred(value = null)
        }
        val key = identityDigest(mediaId, item?.title, item?.artists?.joinToString { it.name }, item?.album?.title, item?.duration?.toString())
        return synchronized(lock) {
            completed[key]?.takeIf { it.expiresAt > SystemClock.elapsedRealtime() }?.let {
                return@synchronized CompletableDeferred(it.resource)
            }
            pending[key]?.let {
                it.forDownload = it.forDownload || forDownload
                return@synchronized it.result
            }
            if (pending.size >= 2) return@synchronized CompletableDeferred(value = null)
            val task = applicationScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                val resource = try {
                    withTimeoutOrNull(LOOKUP_BUDGET_MS) {
                        permits.withPermit {
                            val target = item ?: database.getSongById(mediaId)?.toMediaMetadata() ?: return@withPermit null
                            if (!eligible(target)) return@withPermit null
                            val song = database.songEntity(mediaId)
                            if (song?.isUploaded == true || song?.isLocal == true || song?.isEpisode == true || song?.isVideo == true) null
                            else source.find(target)
                        }
                    }
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { null }
                currentCoroutineContext().ensureActive()
                if (resource != null && !isBlocked(mediaId)) {
                    store.bindPlayback(mediaId, AudioRendition.DIRECT_FLAC_PROFILE, resource.rendition())
                }
                synchronized(lock) {
                    if (!isBlocked(mediaId)) {
                        completed[key] = Entry(mediaId, resource, SystemClock.elapsedRealtime() + RESULT_TTL_MS)
                        trim(completed, 128)
                    }
                }
                resource
            }
            pending[key] = Pending(mediaId, task, forDownload)
            task.invokeOnCompletion { synchronized(lock) { if (pending[key]?.result === task) pending.remove(key) } }
            task.start()
            task
        }
    }

    /** A failed optional stream gets one normal-quality handoff rather than a repeated upgrade loop. */
    internal fun failed(rendition: AudioRendition) = failed(rendition.mediaId, rendition)

    internal fun failed(mediaId: String, rendition: AudioRendition? = null) {
        synchronized(lock) {
            blocked[mediaId] = SystemClock.elapsedRealtime() + RESULT_TTL_MS
            trim(blocked, 128)
            completed.entries.removeAll { it.value.mediaId == mediaId }
            pending.values.filter { it.mediaId == mediaId }.map { it.result }.forEach { it.cancel() }
        }
        applicationScope.launch(Dispatchers.IO + SilentHandler) {
            val saved = rendition ?: store.playback(mediaId, AudioRendition.DIRECT_FLAC_PROFILE)
            saved?.let { store.forgetPlayback(mediaId, AudioRendition.DIRECT_FLAC_PROFILE, it.cacheKey) }
        }
    }

    internal fun cancelPending() = synchronized(lock) {
        pending.values.map { it.result }.forEach { it.cancel() }
    }

    internal fun retainPlaybackLookups(mediaIds: Set<String>) = synchronized(lock) {
        pending.values.filter { !it.forDownload && it.mediaId !in mediaIds }.map { it.result }.forEach { it.cancel() }
    }

    private fun <K, V> trim(map: LinkedHashMap<K, V>, limit: Int) {
        while (map.size > limit) map.entries.iterator().apply { next(); remove() }
    }

    private fun eligible(item: MediaMetadata): Boolean = !item.isEpisode && !item.isVideoSong &&
        item.uploadEntityId == null && !item.album?.title.isNullOrBlank() && item.duration > 0

    private companion object {
        const val LOOKUP_BUDGET_MS = 5_000L
        const val RESULT_TTL_MS = 15 * 60_000L
    }
}
