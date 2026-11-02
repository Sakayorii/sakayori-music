/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.net.Uri
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.Downloader
import androidx.media3.exoplayer.offline.ProgressiveDownloader
import com.sakayori.music.constants.AudioQuality
import com.sakayori.music.di.DownloadCache
import com.sakayori.music.di.PlayerCache
import com.sakayori.innertube.YouTube
import com.sakayori.music.playback.flac.FlacHttpDataSource
import com.sakayori.music.playback.flac.FlacLocation
import com.sakayori.music.playback.flac.FlacPlaybackCapabilities
import com.sakayori.music.playback.flac.FlacResource
import com.sakayori.music.playback.flac.LosslessStreamException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.StateFlow
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.io.InterruptedIOException
import javax.inject.Inject
import javax.inject.Singleton

/** Cache factories always receive a bound rendition before they can read or write any bytes. */
@Singleton
class RenditionDataSources @Inject constructor(
    @DownloadCache private val downloadCache: Cache,
    @PlayerCache private val playerCache: Cache,
    private val store: AudioRenditionStore,
    private val resolver: PlaybackStreamResolver,
    private val httpClient: PlaybackHttpClient,
    private val lossless: dagger.Lazy<LosslessResolver>,
) {
    internal fun playbackFactory(
        quality: () -> AudioQuality,
        selectionVersion: () -> Long,
        usePlayerCache: () -> Boolean,
        freshSelectionToken: (String) -> Long?,
        beforeSelection: suspend (String) -> Unit,
        priority: (String) -> StateFlow<StreamRequestPriority> = { foregroundStreamPriority },
        preferLossless: () -> Boolean = { false },
        onSelected: (SelectedAudio) -> Unit,
    ): DataSource.Factory = DataSource.Factory {
        // A progressive period reopens this same datasource for retries and seeks. Pin its
        // encoding once; a cache eviction must not swap formats underneath an existing extractor.
        var selection: SelectedAudio? = null
        SelectingDataSource { original ->
            val mediaId = original.key ?: throw IOException("Missing media ID")
            val selected = selection ?: runBlocking {
                beforeSelection(mediaId)
                val selectedQuality = quality()
                val version = selectionVersion()
                val token = freshSelectionToken(mediaId)
                val profile = resolver.profile(selectedQuality)
                val revision = YouTube.requestContextRevision.value
                val useCache = usePlayerCache()
                val wantsLossless = preferLossless()
                fun selected(rendition: AudioRendition, stream: ResolvedPlaybackStream? = null) =
                    SelectedAudio(rendition, stream, selectedQuality, version, revision, token,
                        if (rendition.provider == AudioRendition.DIRECT_FLAC) AudioRendition.DIRECT_FLAC_PROFILE else profile)
                suspend fun normal(): SelectedAudio {
                    val saved = if (token != null) null else store.downloaded(mediaId)?.takeIf { rendition ->
                        store.completeInDownloads(rendition) &&
                            (rendition.provider == AudioRendition.YOUTUBE || lossless.get().restore(rendition) != null)
                    }
                    val known = if (token != null) null else store.playback(mediaId, profile)?.takeIf {
                        it.provider == AudioRendition.YOUTUBE && store.hasBytes(it, useCache)
                    }
                    val cached = saved ?: known ?: (if (token != null) null else store.legacyComplete(mediaId, useCache))
                    if (cached != null) return selected(cached)
                    val resolved = resolver.resolve(mediaId, selectedQuality, priority = priority(mediaId))
                    return selected(resolved.rendition, resolved)
                }
                val cachedLossless = if (wantsLossless) lossless.get().cached(mediaId) else null
                val choice = if (cachedLossless != null) selected(cachedLossless.rendition()) else {
                    selectOptionalAudio(
                        optional = if (wantsLossless) lossless.get().lookup(mediaId) else null,
                        convert = { resource: FlacResource -> selected(resource.rendition()) },
                        normal = { normal() },
                    )
                }
                if (version != selectionVersion() || selectedQuality != quality() ||
                    profile != resolver.profile(selectedQuality) || token != freshSelectionToken(mediaId) ||
                    revision != YouTube.requestContextRevision.value || wantsLossless != preferLossless()
                ) {
                    throw InterruptedIOException("Audio selection superseded")
                }
                store.bindPlayback(mediaId, if (choice.rendition.provider == AudioRendition.DIRECT_FLAC) AudioRendition.DIRECT_FLAC_PROFILE else profile, choice.rendition)
                choice.also { selection = it }
            }
            if (selected.rendition.mediaId != mediaId) throw IOException("Audio period changed identity")
            onSelected(selected)
            val source = playbackCacheFactory(selected.rendition, selected.quality, usePlayerCache(), selected.stream, priority(mediaId)) { stream ->
                val updated = selected.copy(rendition = stream.rendition, stream = stream, contextRevision = stream.request.revision)
                if (updated.rendition != selected.rendition && selected.selectionVersion == selectionVersion()) {
                    store.refreshPlaybackMetadata(selected.profile, updated.rendition)
                }
                selection = updated
                onSelected(updated)
            }.createDataSource()
            source to original.buildUpon().setKey(selected.rendition.cacheKey).build()
        }
    }

    private fun legacyFactory(rendition: AudioRendition): CacheDataSource.Factory {
        val cache = store.legacyCache(rendition)
            ?: throw RenditionUnavailableException("Legacy cached audio has no bound cache")
        if (!store.completeInEither(rendition)) {
            throw RenditionUnavailableException("Legacy cached audio is incomplete")
        }
        // Older cache entries sometimes have a Room length but no cache length. Record the
        // verified complete length so an unbounded read ends here rather than opening upstream.
        val length = rendition.contentLength
        if (length != null && ContentMetadata.getContentLength(cache.getContentMetadata(rendition.cacheKey)) < 0) {
            val mutations = ContentMetadataMutations()
            ContentMetadataMutations.setContentLength(mutations, length)
            cache.applyContentMetadataMutations(rendition.cacheKey, mutations)
        }
        return CacheDataSource.Factory()
            .setCache(cache)
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(DataSource.Factory {
                SelectingDataSource {
                    throw RenditionUnavailableException("Legacy cached audio is incomplete")
                }
            })
    }

    internal fun downloadCacheFactory(
        rendition: AudioRendition,
        quality: AudioQuality,
        initial: ResolvedPlaybackStream? = null,
    ): CacheDataSource.Factory {
        if (rendition.legacy) {
            if (rendition.legacyCache != LegacyAudioCache.DOWNLOAD) {
                throw RenditionUnavailableException("Legacy playback fragments cannot be appended to a download")
            }
            return legacyFactory(rendition)
        }
        return CacheDataSource.Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(playerCacheFactory(rendition, quality, initial, forDownload = true))
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    /** Source-adapter entry point. The original resource and exact recording match must exist
     * before this is used; an ordinary YouTube URL is not a [FlacResource]. */
    internal fun flacPlaybackFactory(
        resource: FlacResource,
        resolveLocation: suspend () -> FlacLocation,
        usePlayerCache: () -> Boolean,
        onSelected: (AudioRendition) -> Unit = {},
    ): DataSource.Factory = DataSource.Factory {
        val rendition = resource.rendition()
        SelectingDataSource { original ->
            if (original.key != resource.mediaId) throw IOException("FLAC selection changed logical identity")
            FlacPlaybackCapabilities.requireSupported(resource.streamInfo)
            store.bindPlayback(resource.mediaId, AudioRendition.DIRECT_FLAC_PROFILE, rendition)
            onSelected(rendition)
            val upstream = DataSource.Factory { FlacHttpDataSource(resource, httpClient, resolveLocation) }
            cacheFirstPlaybackFactory(upstream, usePlayerCache()).createDataSource() to
                original.buildUpon().setKey(rendition.cacheKey).build()
        }
    }

    /** Keeps the same DownloadManager ID while the source adapter supplies a pinned FLAC file. */
    internal fun flacDownloader(
        request: DownloadRequest,
        resource: FlacResource,
        resolveLocation: suspend () -> FlacLocation,
        onSelected: (AudioRendition) -> Unit = {},
    ): Downloader {
        require(request.id == resource.mediaId)
        return object : Downloader {
            @Volatile private var cancelled = false
            @Volatile private var delegate: Downloader? = null

            override fun download(progressListener: Downloader.ProgressListener?) {
                if (cancelled) throw InterruptedIOException("FLAC download cancelled")
                if (delegate == null) {
                    val rendition = resource.rendition()
                    val saved = store.downloaded(resource.mediaId)
                    if (saved != null && saved.cacheKey != rendition.cacheKey) {
                        throw RenditionUnavailableException("The download is bound to a different resource; renew the persisted rendition")
                    }
                    store.bindDownload(resource.mediaId, rendition)
                    onSelected(rendition)
                    val upstream = DataSource.Factory { FlacHttpDataSource(resource, httpClient, resolveLocation) }
                    val cache = CacheDataSource.Factory().setCache(downloadCache)
                        .setUpstreamDataSourceFactory(CacheDataSource.Factory().setCache(playerCache).setUpstreamDataSourceFactory(upstream))
                        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
                    delegate = ProgressiveDownloader(
                        MediaItem.Builder().setUri(request.uri).setCustomCacheKey(rendition.cacheKey).build(), cache,
                    )
                }
                val downloader = checkNotNull(delegate)
                if (cancelled) {
                    downloader.cancel()
                    throw InterruptedIOException("FLAC download cancelled")
                }
                downloader.download(progressListener)
                if (!store.completeInDownloads(resource.rendition())) throw IOException("FLAC download is incomplete")
            }

            override fun cancel() {
                cancelled = true
                delegate?.cancel()
            }

            override fun remove() = store.removeDownload(resource.mediaId)
        }
    }

    private fun playbackCacheFactory(
        rendition: AudioRendition,
        quality: AudioQuality,
        usePlayerCache: Boolean,
        initial: ResolvedPlaybackStream?,
        priority: StateFlow<StreamRequestPriority>,
        onResolved: (ResolvedPlaybackStream) -> Unit,
    ): CacheDataSource.Factory = when {
        rendition.legacy -> legacyFactory(rendition)
        rendition.provider == AudioRendition.DIRECT_FLAC -> {
            val resource = lossless.get().restore(rendition)
                ?: throw LosslessStreamException(IOException("Optional lossless source unavailable"))
            cacheFirstPlaybackFactory(
                DataSource.Factory { FlacHttpDataSource(resource, httpClient) { lossless.get().source.location(resource) } }, usePlayerCache,
            )
        }
        else -> cacheFirstPlaybackFactory(upstream(rendition, quality, initial, priority = priority, onResolved = onResolved), usePlayerCache)
    }

    private fun cacheFirstPlaybackFactory(upstream: DataSource.Factory, usePlayerCache: Boolean): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(downloadCache)
        .setCacheWriteDataSinkFactory(null)
        .setUpstreamDataSourceFactory(
            if (usePlayerCache) CacheDataSource.Factory().setCache(playerCache)
                .setUpstreamDataSourceFactory(upstream).setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            else upstream,
        )
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private fun playerCacheFactory(
        rendition: AudioRendition,
        quality: AudioQuality,
        initial: ResolvedPlaybackStream?,
        forDownload: Boolean = false,
        priority: StateFlow<StreamRequestPriority> = backgroundStreamPriority,
        onResolved: (ResolvedPlaybackStream) -> Unit = {},
    ): CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(playerCache)
        .setUpstreamDataSourceFactory(upstream(rendition, quality, initial, forDownload, priority, onResolved))
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    private fun upstream(
        rendition: AudioRendition,
        quality: AudioQuality,
        initial: ResolvedPlaybackStream?,
        forDownload: Boolean = false,
        priority: StateFlow<StreamRequestPriority> = backgroundStreamPriority,
        onResolved: (ResolvedPlaybackStream) -> Unit = {},
    ): DataSource.Factory = DataSource.Factory {
        var currentStream: ResolvedPlaybackStream? = initial
        val source = ResolvingDataSource.Factory(
            DataSource.Factory {
                OkHttpDataSource.Factory(httpClient.get())
                    .setContentTypePredicate(HttpDataSource.REJECT_PAYWALL_TYPES).createDataSource()
            },
        ) { requested ->
            if (rendition.legacy) throw RenditionUnavailableException("Legacy cached audio is incomplete")
            val stream = currentStream?.takeIf(resolver::canReuse)
                ?: runBlocking { resolver.resolve(rendition.mediaId, quality, rendition, priority) }
            currentStream = stream
            onResolved(stream)
            val url = if (forDownload && rendition.contentLength != null) {
                // This query convention belongs to YouTube downloads, never generic FLAC.
                stream.playback.streamUrl.toHttpUrl().newBuilder()
                    .setQueryParameter("range", "0-${rendition.contentLength}").build().toString()
            } else stream.playback.streamUrl
            requested.withUri(url.toUri())
                .withRequestHeaders(requested.httpRequestHeaders + stream.playback.streamHeaders)
        }.createDataSource()
        RejectedStreamDataSource(source) { currentStream?.let(resolver::invalidateRejected) }
    }
}

internal data class SelectedAudio(
    val rendition: AudioRendition,
    val stream: ResolvedPlaybackStream?,
    val quality: AudioQuality,
    val selectionVersion: Long,
    val contextRevision: Long,
    val freshSelectionToken: Long?,
    val profile: String,
)

/** Invalidates rejected URLs before Media3's next loader retry, not only after final failure. */
private class RejectedStreamDataSource(
    private val delegate: DataSource,
    private val onRejected: () -> Unit,
) : DataSource by delegate {
    override fun open(dataSpec: DataSpec): Long = invalidateOnRejection { delegate.open(dataSpec) }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        invalidateOnRejection { delegate.read(buffer, offset, length) }

    private inline fun <T> invalidateOnRejection(block: () -> T): T = try {
        block()
    } catch (error: HttpDataSource.InvalidResponseCodeException) {
        if (error.responseCode == 403 || error.responseCode == 410 || error.responseCode == 416) onRejected()
        throw error
    }
}

/** Defers all disk/network selection to open(), which Media3 calls from a loader thread. */
private class SelectingDataSource(
    private val select: (DataSpec) -> Pair<DataSource, DataSpec>,
) : DataSource {
    private var delegate: DataSource? = null
    private val listeners = mutableListOf<TransferListener>()

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long = sourceCall {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Audio source selection must run on a loader thread" }
        val (source, selectedSpec) = select(dataSpec)
        delegate = source
        listeners.forEach(source::addTransferListener)
        source.open(selectedSpec)
    }

    private inline fun <T> sourceCall(block: () -> T): T {
        try {
            return block()
        } catch (cancelled: CancellationException) {
            throw InterruptedIOException("Audio request cancelled").apply { initCause(cancelled) }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Audio request interrupted").apply { initCause(interrupted) }
        } catch (error: IOException) {
            throw when (error) {
                is java.net.ConnectException, is java.net.UnknownHostException ->
                    DataSourceException(error.message, error, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
                is java.net.SocketTimeoutException ->
                    DataSourceException(error.message, error, PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT)
                else -> error
            }
        } catch (error: PlaybackException) {
            throw DataSourceException(error.message, error, error.errorCode)
        } catch (error: Exception) {
            throw IOException("Unable to read audio source", error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = sourceCall {
        delegate?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT
    }

    override fun getUri(): Uri? = delegate?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()

    override fun close() {
        val source = delegate
        delegate = null
        source?.close()
    }
}
