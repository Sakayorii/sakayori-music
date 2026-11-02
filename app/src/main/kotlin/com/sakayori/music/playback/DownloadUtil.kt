/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.content.Context
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.DownloaderFactory
import com.sakayori.music.constants.AudioQuality
import com.sakayori.music.constants.AudioQualityKey
import com.sakayori.music.constants.PreferLosslessKey
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.db.entities.SongEntity
import com.sakayori.music.di.DownloadCache
import com.sakayori.music.di.PlayerCache
import com.sakayori.music.utils.enumPreference
import com.sakayori.music.utils.dataStore
import com.sakayori.music.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadUtil
@Inject
constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    val databaseProvider: DatabaseProvider,
    @DownloadCache val downloadCache: Cache,
    @PlayerCache val playerCache: Cache,
    private val renditionStore: AudioRenditionStore,
    private val streamResolver: PlaybackStreamResolver,
    private val renditionSources: RenditionDataSources,
    private val losslessResolver: LosslessResolver,
) {
    private val TAG = "DownloadUtil"
    private val audioQuality by enumPreference(context, AudioQualityKey, AudioQuality.AUTO)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val downloads = MutableStateFlow<Map<String, Download>>(emptyMap())
    private val initialSnapshotLock = Any()
    private var initialSnapshotPending = true
    private val changedDuringInitialSnapshot = mutableSetOf<String>()
    private data class DownloadEvent(val download: Download, val removed: Boolean = false)
    private val downloadEvents = Channel<DownloadEvent>(Channel.UNLIMITED)

    // Media3 delivers listener callbacks on the application thread. Persist in order on IO;
    // a late STOPPED update must not overwrite a subsequent COMPLETED update.
    private val downloadEventJob = scope.launch {
        for ((download, removed) in downloadEvents) {
            try {
                val id = download.request.id
                if (removed) {
                    database.updateDownloadedInfo(id, false, null)
                    synchronized(initialSnapshotLock) {
                        downloads.update { map ->
                            if (map[id] === download || map[id]?.state == Download.STATE_REMOVING) map - id else map
                        }
                    }
                } else when (download.state) {
                    Download.STATE_COMPLETED -> database.updateDownloadedInfo(id, true, LocalDateTime.now())
                    Download.STATE_FAILED, Download.STATE_STOPPED, Download.STATE_REMOVING ->
                        database.updateDownloadedInfo(id, false, null)
                    else -> Unit
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag(TAG).e(error, "Failed to persist download state for ${download.request.id}")
            }
        }
    }

    private val downloaderFactory = DownloaderFactory { request ->
        RenditionDownloader(request, audioQuality, renditionStore, streamResolver, renditionSources,
            losslessResolver, context.dataStore.get(PreferLosslessKey, false)) { rendition, resolved ->
            // The downloader calls this on its worker. Finish insertion before completion can
            // be reported, and never overwrite a concurrently saved library song/active format.
            rendition.toFormatEntity()?.let(database::insertFormatIfMissing)
            val details = resolved?.playback?.videoDetails
            database.insert(SongEntity(
                id = request.id,
                title = details?.title ?: request.data.toString(Charsets.UTF_8).ifBlank { "Unknown" },
                duration = details?.lengthSeconds?.toIntOrNull() ?: 0,
                thumbnailUrl = details?.thumbnail?.thumbnails?.lastOrNull()?.url,
                dateDownload = null,
                isDownloaded = false,
            ))
        }
    }

    val downloadNotificationHelper =
        DownloadNotificationHelper(context, ExoDownloadService.CHANNEL_ID)

    val downloadManager: DownloadManager =
        DownloadManager(
            context,
            DefaultDownloadIndex(databaseProvider),
            downloaderFactory,
        ).apply {
            maxParallelDownloads = 3
            addListener(
                object : DownloadManager.Listener {
                    override fun onDownloadChanged(
                        downloadManager: DownloadManager,
                        download: Download,
                        finalException: Exception?,
                    ) {
                        synchronized(initialSnapshotLock) {
                            if (initialSnapshotPending) changedDuringInitialSnapshot += download.request.id
                            downloads.update { it + (download.request.id to download) }
                        }
                        downloadEvents.trySend(DownloadEvent(download))
                    }

                    override fun onDownloadRemoved(
                        downloadManager: DownloadManager,
                        download: Download,
                    ) {
                        val downloadId = download.request.id
                        streamResolver.invalidate(downloadId)

                        synchronized(initialSnapshotLock) {
                            if (initialSnapshotPending) changedDuringInitialSnapshot += downloadId
                        }
                        downloadEvents.trySend(DownloadEvent(download, removed = true))
                    }
                }
            )
        }

    init {
        scope.launch {
            try {
                val result = mutableMapOf<String, Download>()
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    while (cursor.moveToNext()) result[cursor.download.request.id] = cursor.download
                }
                synchronized(initialSnapshotLock) {
                    downloads.update { current -> result.filterKeys { it !in changedDuringInitialSnapshot } + current }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Timber.tag(TAG).e(error, "Failed to read saved downloads")
            } finally {
                synchronized(initialSnapshotLock) {
                    initialSnapshotPending = false
                    changedDuringInitialSnapshot.clear()
                }
            }
        }
    }

    fun getDownload(songId: String): Flow<Download?> = downloads.map { it[songId] }

    fun release() {
        downloadEvents.close()
        downloadEventJob.cancel()
        scope.cancel()
    }
}
