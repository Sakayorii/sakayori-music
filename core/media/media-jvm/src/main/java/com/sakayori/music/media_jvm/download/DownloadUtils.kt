package com.sakayori.music.media_jvm.download

import com.sakayori.common.MERGING_DATA_TYPE
import com.sakayori.domain.data.entities.DownloadState
import com.sakayori.domain.data.entities.SongEntity
import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.domain.mediaservice.handler.DownloadHandler
import com.sakayori.domain.repository.SongRepository
import com.sakayori.domain.repository.StreamRepository
import com.sakayori.domain.utils.toTrack
import com.sakayori.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

internal class DownloadUtils(
    private val dataStoreManager: DataStoreManager,
    private val streamRepository: StreamRepository,
    private val songRepository: SongRepository,
) : DownloadHandler {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var _downloads = MutableStateFlow<Map<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>>(emptyMap())

    override val downloads: StateFlow<Map<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>>
        get() = _downloads
    private val _downloadTask = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val downloadTask: StateFlow<Map<String, Int>> get() = _downloadTask

    val downloadingVideoIds = MutableStateFlow<Set<String>>(emptySet())

    init {
    }

    override suspend fun downloadTrack(
        videoId: String,
        title: String,
        thumbnail: String,
    ) {
        Logger.w("Download", "downloadTrack called videoId=$videoId title=$title")
        var song = songRepository.getSongById(videoId).lastOrNull()
        if (song == null) {
            Logger.w("Download", "Song not in DB — inserting minimal entity from UI params")
            val placeholder = SongEntity(
                videoId = videoId,
                title = title,
                thumbnails = thumbnail.takeIf { it.isNotEmpty() },
                duration = "",
                durationSeconds = 0,
                isAvailable = true,
                isExplicit = false,
                likeStatus = "INDIFFERENT",
                videoType = "MUSIC_VIDEO_TYPE_ATV",
                category = null,
                resultType = null,
            )
            songRepository.insertSong(placeholder).firstOrNull()
            song = songRepository.getSongById(videoId).lastOrNull()
            if (song == null) {
                Logger.e("Download", "Failed to insert placeholder song — abort download")
                return
            }
        }
        songRepository.updateDownloadState(
            videoId,
            DownloadState.STATE_DOWNLOADING,
        )
        if (!File(getDownloadPath()).exists()) {
            File(getDownloadPath()).mkdirs()
        }
        Logger.w("Download", "Starting downloadToFile for $videoId → ${getDownloadPath()}")
        songRepository
            .downloadToFile(
                song.toTrack(),
                path = getDownloadPath() + File.separator + videoId,
                videoId = videoId,
                isVideo = false,
            ).collect { state ->
                if (state.isError) {
                    Logger.e("Download", "Download error for $videoId: ${state.errorMessage}")
                    songRepository.updateDownloadState(
                        videoId,
                        DownloadState.STATE_NOT_DOWNLOADED,
                    )
                } else if (state.isDone) {
                    Logger.w("Download", "Download done for $videoId")
                    songRepository.updateDownloadState(
                        videoId,
                        DownloadState.STATE_DOWNLOADED,
                    )
                }
            }
    }

    override fun removeDownload(videoId: String) {
        File(getDownloadPath())
            .listFiles()
            ?.filter {
                it.name.contains(videoId)
            }?.forEach {
                try {
                    it.delete()
                } catch (_: Throwable) {
                }
                coroutineScope.launch {
                    songRepository.updateDownloadState(
                        videoId,
                        DownloadState.STATE_NOT_DOWNLOADED,
                    )
                }
            }
    }

    override fun removeAllDownloads() {
        File(getDownloadPath()).listFiles()?.forEach {
            try {
                it.delete()
            } catch (_: Throwable) {
            }
            coroutineScope.launch {
                songRepository.updateDownloadState(
                    it.name.split(".").first().removePrefix(
                        MERGING_DATA_TYPE.VIDEO,
                    ),
                    DownloadState.STATE_NOT_DOWNLOADED,
                )
            }
        }
    }
}

fun getDownloadPath(): String {
    val path = System.getProperty("user.home") + File.separator + ".SakayoriMusic" + File.separator + "downloads"
    try {
        val dir = File(path)
        if (!dir.exists()) dir.mkdirs()
    } catch (_: Throwable) {
    }
    return path
}
