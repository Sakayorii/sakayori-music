package com.sakayori.music.update

import com.sakayori.logger.Logger
import com.sakayori.music.expect.updateDownloadDir
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data class Downloading(val fileName: String, val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()
    data class Ready(val filePath: String, val fileName: String, val tag: String) : UpdateDownloadState()
    data class Failed(val reason: String) : UpdateDownloadState()
}

class UpdateDownloadManager(
    private val httpClient: HttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    suspend fun cleanupStalePartials() = withContext(Dispatchers.IO) {
        val dir = updateDownloadDir()
        if (!dir.exists()) return@withContext
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".part")) {
                file.delete()
                Logger.w(TAG, "Deleted stale partial: ${file.name}")
            }
        }
    }

    fun existingReadyFile(fileName: String, tag: String): File? {
        val target = File(updateDownloadDir(), fileName)
        return if (target.exists() && target.length() > 0) target else null
    }

    fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _state.value = UpdateDownloadState.Idle
    }

    fun start(
        url: String,
        fileName: String,
        expectedSize: Long,
        tag: String,
        onComplete: (File) -> Unit = {},
    ) {
        currentJob?.cancel()
        currentJob = scope.launch {
            try {
                val dir = updateDownloadDir().apply { if (!exists()) mkdirs() }
                val partFile = File(dir, "$fileName.part")
                val finalFile = File(dir, fileName)

                if (finalFile.exists()) finalFile.delete()
                if (partFile.exists()) partFile.delete()

                _state.value = UpdateDownloadState.Downloading(fileName, 0L, expectedSize)

                httpClient.prepareGet(url).execute { response ->
                    val total = response.contentLength() ?: expectedSize
                    val channel: ByteReadChannel = response.bodyAsChannel()
                    FileOutputStream(partFile).use { out ->
                        val buffer = ByteArray(64 * 1024)
                        var downloaded = 0L
                        var lastReport = 0L
                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read > 0) {
                                out.write(buffer, 0, read)
                                downloaded += read
                                if (downloaded - lastReport >= 256 * 1024 || downloaded == total) {
                                    _state.value = UpdateDownloadState.Downloading(fileName, downloaded, total)
                                    lastReport = downloaded
                                }
                            } else if (read < 0) {
                                break
                            }
                        }
                        out.flush()
                    }
                }

                if (!partFile.renameTo(finalFile)) {
                    partFile.copyTo(finalFile, overwrite = true)
                    partFile.delete()
                }
                _state.value = UpdateDownloadState.Ready(finalFile.absolutePath, fileName, tag)
                onComplete(finalFile)
            } catch (t: Throwable) {
                Logger.e(TAG, "Update download failed: ${t.message}", t)
                _state.value = UpdateDownloadState.Failed(t.message ?: "Download failed")
            }
        }
    }

    companion object {
        private const val TAG = "UpdateDownloadManager"
    }
}
