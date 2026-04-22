package com.sakayori.music.update

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.StateFlow

sealed class UpdateDownloadState {
    data object Idle : UpdateDownloadState()
    data class Downloading(val fileName: String, val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()
    data class Ready(val filePath: String, val fileName: String, val tag: String) : UpdateDownloadState()
    data class Failed(val reason: String) : UpdateDownloadState()
}

expect class UpdateDownloadManager(httpClient: HttpClient) {
    val state: StateFlow<UpdateDownloadState>

    suspend fun cleanupStalePartials()

    fun cancel()

    fun start(
        url: String,
        fileName: String,
        expectedSize: Long,
        tag: String,
        onComplete: (String) -> Unit = {},
    )
}
