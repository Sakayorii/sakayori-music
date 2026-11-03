/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import com.sakayori.music.constants.AudioQuality
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.io.InterruptedIOException

/** Keeps DownloadManager's logical request ID while binding its actual writer to one rendition. */
internal class RenditionDownloader(
    private val request: DownloadRequest,
    private val quality: AudioQuality,
    private val store: AudioRenditionStore,
    private val resolver: PlaybackStreamResolver,
    private val sources: RenditionDataSources,
    private val lossless: LosslessResolver,
    private val preferLossless: Boolean,
    private val onSelected: (AudioRendition, ResolvedPlaybackStream?) -> Unit,
) : Downloader {
    @Volatile private var cancelled = false
    @Volatile private var delegate: Downloader? = null
    private var boundRendition: AudioRendition? = null
    private var needsFreshSelection = false
    private var skipLossless = false

    override fun download(progressListener: Downloader.ProgressListener?) {
        if (cancelled) throw InterruptedIOException("Download cancelled")
        var protection: AutoCloseable? = null
        try {
            if (delegate == null) {
                var resolved: ResolvedPlaybackStream? = null
                val rendition = runBlocking {
                    val saved = if (needsFreshSelection) null else store.downloaded(request.id)?.takeIf {
                        (!it.legacy || store.completeInDownloads(it)) &&
                            (it.provider == AudioRendition.YOUTUBE || lossless.source.restore(it) != null)
                    }
                    val preferred = if (saved == null && preferLossless && !needsFreshSelection && !skipLossless) {
                        try {
                            lossless.cached(request.id) ?: lossless.lookup(request.id, forDownload = true).await()
                        } catch (cancelled: CancellationException) {
                            currentCoroutineContext().ensureActive()
                            null
                        } catch (_: Exception) {
                            null
                        }
                    } else null
                    val legacy = if (saved != null || needsFreshSelection) null else store.legacyComplete(request.id, includePlayerCache = false)
                    val cached = if (saved != null || legacy != null || (needsFreshSelection && !skipLossless)) null else {
                        store.playback(request.id, resolver.profile(quality))?.takeIf {
                            it.provider == AudioRendition.YOUTUBE && !it.legacy && store.hasBytes(it)
                        }
                    }
                    saved ?: preferred?.rendition() ?: legacy ?: cached
                        ?: resolver.resolve(request.id, quality, priority = backgroundStreamPriority).also { resolved = it }.rendition
                }
                if (cancelled) throw InterruptedIOException("Download cancelled")
                store.bindDownload(request.id, rendition)
                boundRendition = rendition
                needsFreshSelection = false
                onSelected(rendition, resolved)
                delegate = if (rendition.provider == AudioRendition.DIRECT_FLAC) {
                    val resource = lossless.source.restore(rendition) ?: throw IOException("Optional download source unavailable")
                    sources.flacDownloader(request, resource, resolveLocation = { lossless.source.location(resource) })
                } else {
                    ProgressiveDownloader(
                        MediaItem.Builder().setUri(request.uri).setCustomCacheKey(rendition.cacheKey).build(),
                        sources.downloadCacheFactory(rendition, quality, resolved),
                    )
                }
            }
            val downloader = delegate ?: throw IOException("Download source unavailable")
            protection = boundRendition?.let(store::protect)
            if (cancelled) {
                downloader.cancel()
                throw InterruptedIOException("Download cancelled")
            }
            downloader.download(progressListener)
            boundRendition?.let {
                if (!store.completeInDownloads(it)) throw IOException("Downloaded audio does not match its expected length")
            }
        } catch (cancelled: CancellationException) {
            throw InterruptedIOException("Download cancelled").apply { initCause(cancelled) }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw interrupted
        } catch (error: IOException) {
            val failed = boundRendition
            if (failed?.provider == AudioRendition.DIRECT_FLAC && !skipLossless && !cancelled && !Thread.currentThread().isInterrupted) {
                lossless.failed(failed, forDownload = true)
                delegate?.cancel()
                delegate = null
                skipLossless = true
                needsFreshSelection = true
                download(progressListener)
                return
            }
            // A cleared/evicted legacy source can restart into a new physical key on retry.
            // An unversioned expired lease can only restart under a NEW key. Versioned
            // downloads stay pinned; failure must never append a replacement file to them.
            if (boundRendition?.legacy == true ||
                (boundRendition?.canRenewYouTube == false && error is RenditionUnavailableException)
            ) {
                delegate?.cancel()
                delegate = null
                needsFreshSelection = true
            }
            throw error
        } catch (error: Exception) {
            throw IOException("Unable to resolve download", error)
        } finally {
            protection?.close()
        }
    }

    override fun cancel() {
        cancelled = true
        delegate?.cancel()
    }

    override fun remove() {
        store.removeDownload(request.id)
    }
}
