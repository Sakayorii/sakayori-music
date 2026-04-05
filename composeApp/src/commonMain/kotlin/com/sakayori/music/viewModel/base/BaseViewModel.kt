package com.sakayori.music.viewModel.base

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakayori.domain.mediaservice.handler.MediaPlayerHandler
import com.sakayori.domain.mediaservice.handler.QueueData
import com.sakayori.logger.LogLevel
import com.sakayori.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import multiplatform.network.cmptoast.ToastDuration
import multiplatform.network.cmptoast.ToastGravity
import multiplatform.network.cmptoast.showToast
import org.jetbrains.compose.resources.StringResource
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.loading

abstract class BaseViewModel :
    ViewModel(),
    KoinComponent {
    protected val mediaPlayerHandler: MediaPlayerHandler by inject<MediaPlayerHandler>()
    private val _nowPlayingVideoId: MutableStateFlow<String> = MutableStateFlow("")

    val nowPlayingVideoId: StateFlow<String> get() = _nowPlayingVideoId

    protected val tag: String = this::class.simpleName ?: "BaseViewModel"

    protected fun log(
        message: String,
        logType: LogLevel = LogLevel.WARN,
    ) {
        when (logType) {
            LogLevel.DEBUG -> Logger.d(tag, message)
            LogLevel.INFO -> Logger.i(tag, message)
            LogLevel.WARN -> Logger.w(tag, message)
            LogLevel.ERROR -> Logger.e(tag, message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.cancel()
        log("ViewModel cleared", LogLevel.WARN)
    }

    init {
        getNowPlayingVideoId()
    }

    fun makeToast(message: String?) {
        showToast(
            message = message ?: "NO MESSAGE",
            duration = ToastDuration.Short,
            gravity = ToastGravity.Bottom,
        )
    }

    private val stringCache = mutableMapOf<StringResource, String>()

    protected fun getString(resId: StringResource): String =
        stringCache.getOrPut(resId) {
            runBlocking(Dispatchers.Default) {
                org.jetbrains.compose.resources
                    .getString(resId)
            }
        }

    private val loadingString: String by lazy { getString(Res.string.loading) }

    private val _showLoadingDialog: MutableStateFlow<Pair<Boolean, String>> = MutableStateFlow(false to "")
    val showLoadingDialog: StateFlow<Pair<Boolean, String>> get() = _showLoadingDialog

    fun showLoadingDialog(message: String? = null) {
        _showLoadingDialog.value = true to (message ?: loadingString)
    }

    fun hideLoadingDialog() {
        _showLoadingDialog.value = false to loadingString
    }

    private fun getNowPlayingVideoId() {
        viewModelScope.launch {
            combine(mediaPlayerHandler.nowPlayingState, mediaPlayerHandler.controlState) { nowPlayingState, controlState ->
                Pair(nowPlayingState, controlState)
            }.collect { (nowPlayingState, controlState) ->
                if (controlState.isPlaying) {
                    _nowPlayingVideoId.value = nowPlayingState.songEntity?.videoId ?: ""
                } else {
                    _nowPlayingVideoId.value = ""
                }
            }
        }
    }

    fun setQueueData(queueData: QueueData.Data) {
        mediaPlayerHandler.reset()
        mediaPlayerHandler.setQueueData(queueData)
    }

    fun <T> loadMediaItem(
        anyTrack: T,
        type: String,
        index: Int? = null,
    ) {
        viewModelScope.launch {
            mediaPlayerHandler.loadMediaItem(
                anyTrack = anyTrack,
                type = type,
                index = index,
            )
        }
    }

    fun shufflePlaylist(firstPlayIndex: Int = 0) {
        mediaPlayerHandler.shufflePlaylist(firstPlayIndex)
    }
}
