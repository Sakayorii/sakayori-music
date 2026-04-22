package com.sakayori.music.update

import androidx.appcompat.app.AppCompatActivity
import com.sakayori.logger.Logger
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
import org.koin.mp.KoinPlatform.getKoin
import java.io.File
import java.io.FileOutputStream

actual class UpdateDownloadManager actual constructor(
    private val httpClient: HttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var currentJob: Job? = null

    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    actual val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    private fun downloadDir(): File {
        val context: AppCompatActivity = getKoin().get()
        return File(context.filesDir, "updates")
    }

    actual suspend fun cleanupStalePartials() = withContext(Dispatchers.IO) {
        val dir = downloadDir()
        if (!dir.exists()) return@withContext
        dir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".part")) {
                file.delete()
                Logger.w(TAG, "Deleted stale partial: ${file.name}")
            }
        }
    }

    actual fun cancel() {
        currentJob?.cancel()
        currentJob = null
        _state.value = UpdateDownloadState.Idle
    }

    actual fun start(
        url: String,
        fileName: String,
        expectedSize: Long,
        tag: String,
        onComplete: (String) -> Unit,
    ) {
        currentJob?.cancel()
        currentJob = scope.launch {
            try {
                val dir = downloadDir().apply { if (!exists()) mkdirs() }
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
                onComplete(finalFile.absolutePath)
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
