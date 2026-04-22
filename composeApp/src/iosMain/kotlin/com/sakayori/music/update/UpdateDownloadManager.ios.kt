package com.sakayori.music.update

import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

actual class UpdateDownloadManager actual constructor(
    @Suppress("unused") private val httpClient: HttpClient,
) {
    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    actual val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    actual suspend fun cleanupStalePartials() { }

    actual fun cancel() {
        _state.value = UpdateDownloadState.Idle
    }

    actual fun start(
        url: String,
        fileName: String,
        expectedSize: Long,
        tag: String,
        onComplete: (String) -> Unit,
    ) {
        _state.value = UpdateDownloadState.Failed("In-app updates not supported on iOS. Use TestFlight or App Store.")
    }
}
