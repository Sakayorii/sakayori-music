@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sakayori.data.mediaservice

import com.sakayori.domain.data.entities.NewFormatEntity
import com.sakayori.domain.data.model.browse.album.Track
import com.sakayori.domain.data.model.mediaService.SponsorSkipSegments
import com.sakayori.domain.data.player.GenericCommandButton
import com.sakayori.domain.data.player.GenericMediaItem
import com.sakayori.domain.data.player.GenericMediaMetadata
import com.sakayori.domain.data.player.GenericTracks
import com.sakayori.domain.data.player.PlayerError
import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.domain.mediaservice.handler.ControlState
import com.sakayori.domain.mediaservice.handler.MediaPlayerHandler
import com.sakayori.domain.mediaservice.handler.NowPlayingTrackState
import com.sakayori.domain.mediaservice.handler.PlayerEvent
import com.sakayori.domain.mediaservice.handler.QueueData
import com.sakayori.domain.mediaservice.handler.RepeatState
import com.sakayori.domain.mediaservice.handler.SimpleMediaState
import com.sakayori.domain.mediaservice.handler.SleepTimerState
import com.sakayori.domain.mediaservice.handler.ToastType
import com.sakayori.domain.mediaservice.player.MediaPlayerInterface
import com.sakayori.domain.mediaservice.player.MediaPlayerListener
import com.sakayori.domain.repository.StreamRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import platform.MediaPlayer.MPMediaItemPropertyArtist
import platform.MediaPlayer.MPMediaItemPropertyPlaybackDuration
import platform.MediaPlayer.MPMediaItemPropertyTitle
import platform.MediaPlayer.MPNowPlayingInfoCenter
import platform.MediaPlayer.MPNowPlayingInfoPropertyElapsedPlaybackTime
import platform.MediaPlayer.MPNowPlayingInfoPropertyPlaybackRate
import platform.MediaPlayer.MPRemoteCommandCenter
import platform.MediaPlayer.MPRemoteCommandHandlerStatusSuccess

internal class IosMediaPlayerHandler(
    private val dataStoreManager: DataStoreManager,
    private val streamRepository: StreamRepository,
) : MediaPlayerHandler {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val iosPlayer = AVPlayerMediaPlayerInterface()
    override val player: MediaPlayerInterface = iosPlayer

    private val _simpleMediaState = MutableStateFlow<SimpleMediaState>(SimpleMediaState.Initial)
    override val simpleMediaState: StateFlow<SimpleMediaState> = _simpleMediaState.asStateFlow()

    private val _nowPlaying = MutableStateFlow<GenericMediaItem?>(null)
    override val nowPlaying: StateFlow<GenericMediaItem?> = _nowPlaying.asStateFlow()

    private val _queueData = MutableStateFlow<QueueData?>(null)
    override val queueData: StateFlow<QueueData?> = _queueData.asStateFlow()

    private val _controlState = MutableStateFlow(
        ControlState(
            isPlaying = false,
            isShuffle = false,
            repeatState = RepeatState.None,
            isLiked = false,
            isNextAvailable = false,
            isPreviousAvailable = false,
            isCrossfading = false,
            volume = 1.0f,
        ),
    )
    override val controlState: StateFlow<ControlState> = _controlState.asStateFlow()

    private val _nowPlayingState = MutableStateFlow(NowPlayingTrackState.initial())
    override val nowPlayingState: StateFlow<NowPlayingTrackState> = _nowPlayingState.asStateFlow()

    private val _sleepTimerState = MutableStateFlow(SleepTimerState(isDone = true, timeRemaining = 0))
    override val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    private val _skipSegments = MutableStateFlow<List<SponsorSkipSegments>?>(null)
    override val skipSegments: StateFlow<List<SponsorSkipSegments>?> = _skipSegments.asStateFlow()

    private val _format = MutableStateFlow<NewFormatEntity?>(null)
    override val format: StateFlow<NewFormatEntity?> = _format.asStateFlow()

    private val _currentSongIndex = MutableStateFlow(0)
    override val currentSongIndex: StateFlow<Int> = _currentSongIndex.asStateFlow()

    override var onUpdateNotification: (List<GenericCommandButton>) -> Unit = {}
    override var pushPlayerError: (PlayerError) -> Unit = {}
    override var showToast: (ToastType) -> Unit = {}

    private var progressJob: Job? = null
    private var sleepJob: Job? = null
    private var sleepRemainingSec: Int = 0

    private val playerListener = object : MediaPlayerListener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _controlState.value = _controlState.value.copy(isPlaying = isPlaying)
            updateNowPlayingInfoRate(isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: GenericMediaItem?, reason: Int) {
            _nowPlaying.value = mediaItem
            _currentSongIndex.value = player.currentMediaItemIndex
            _controlState.value = _controlState.value.copy(
                isNextAvailable = player.hasNextMediaItem(),
                isPreviousAvailable = player.hasPreviousMediaItem(),
            )
            updateNowPlayingInfoMetadata(mediaItem)
        }

        override fun onTimelineChanged(list: List<GenericMediaItem>, reason: String) {
            _controlState.value = _controlState.value.copy(
                isNextAvailable = player.hasNextMediaItem(),
                isPreviousAvailable = player.hasPreviousMediaItem(),
            )
        }

        override fun onTracksChanged(tracks: GenericTracks) {}

        override fun onPlayerError(error: PlayerError) {
            pushPlayerError(error)
            showToast(ToastType.PlayerError(error.message ?: "Playback error"))
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _simpleMediaState.value = when (playbackState) {
                PLAYBACK_STATE_READY -> SimpleMediaState.Ready(player.duration)
                PLAYBACK_STATE_BUFFERING -> SimpleMediaState.Buffering(player.currentPosition)
                PLAYBACK_STATE_ENDED -> SimpleMediaState.Ended
                else -> SimpleMediaState.Initial
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean, list: List<GenericMediaItem>) {
            _controlState.value = _controlState.value.copy(isShuffle = shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _controlState.value = _controlState.value.copy(
                repeatState = when (repeatMode) {
                    1 -> RepeatState.One
                    2 -> RepeatState.All
                    else -> RepeatState.None
                },
            )
        }

        override fun onVolumeChanged(volume: Float) {
            _controlState.value = _controlState.value.copy(volume = volume)
        }
    }

    init {
        player.addListener(playerListener)
        setupRemoteCommandCenter()
    }

    private fun setupRemoteCommandCenter() {
        val center = MPRemoteCommandCenter.sharedCommandCenter()
        center.playCommand.setEnabled(true)
        center.pauseCommand.setEnabled(true)
        center.nextTrackCommand.setEnabled(true)
        center.previousTrackCommand.setEnabled(true)
        center.togglePlayPauseCommand.setEnabled(true)

        center.playCommand.addTargetWithHandler { _ ->
            player.play()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.pauseCommand.addTargetWithHandler { _ ->
            player.pause()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.togglePlayPauseCommand.addTargetWithHandler { _ ->
            if (player.isPlaying) player.pause() else player.play()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.nextTrackCommand.addTargetWithHandler { _ ->
            if (player.hasNextMediaItem()) player.seekToNext()
            MPRemoteCommandHandlerStatusSuccess
        }
        center.previousTrackCommand.addTargetWithHandler { _ ->
            if (player.hasPreviousMediaItem()) player.seekToPrevious()
            MPRemoteCommandHandlerStatusSuccess
        }
    }

    private fun updateNowPlayingInfoMetadata(mediaItem: GenericMediaItem?) {
        if (mediaItem == null) {
            MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
            return
        }
        val info = mutableMapOf<Any?, Any?>(
            MPMediaItemPropertyTitle to (mediaItem.metadata.title ?: ""),
            MPMediaItemPropertyArtist to (mediaItem.metadata.artist ?: ""),
            MPMediaItemPropertyPlaybackDuration to (player.duration / 1000.0),
            MPNowPlayingInfoPropertyElapsedPlaybackTime to (player.currentPosition / 1000.0),
            MPNowPlayingInfoPropertyPlaybackRate to if (player.isPlaying) 1.0 else 0.0,
        )
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = info.toMap()
    }

    private fun updateNowPlayingInfoRate(isPlaying: Boolean) {
        val center = MPNowPlayingInfoCenter.defaultCenter()
        val info = (center.nowPlayingInfo ?: return).toMutableMap()
        info[MPNowPlayingInfoPropertyPlaybackRate] = if (isPlaying) 1.0 else 0.0
        info[MPNowPlayingInfoPropertyElapsedPlaybackTime] = player.currentPosition / 1000.0
        center.nowPlayingInfo = info.toMap()
    }

    override suspend fun onPlayerEvent(playerEvent: PlayerEvent) {
        when (playerEvent) {
            PlayerEvent.PlayPause -> if (player.isPlaying) player.pause() else player.play()
            PlayerEvent.Backward -> player.seekBack()
            PlayerEvent.Forward -> player.seekForward()
            PlayerEvent.Stop -> player.stop()
            PlayerEvent.Next -> if (player.hasNextMediaItem()) player.seekToNext()
            PlayerEvent.Previous -> if (player.hasPreviousMediaItem()) player.seekToPrevious()
            PlayerEvent.Shuffle -> {
                player.shuffleModeEnabled = !player.shuffleModeEnabled
            }
            PlayerEvent.Repeat -> {
                player.repeatMode = (player.repeatMode + 1) % 3
            }
            is PlayerEvent.UpdateProgress -> {
                val target = (player.duration * playerEvent.newProgress / 100f).toLong()
                player.seekTo(target)
            }
            is PlayerEvent.UpdateVolume -> {
                player.volume = playerEvent.newVolume
            }
            PlayerEvent.ToggleLike -> toggleLike()
        }
    }

    override fun toggleRadio() {}

    override fun toggleLike() {
        _controlState.value = _controlState.value.copy(isLiked = !_controlState.value.isLiked)
    }

    override fun like(liked: Boolean) {
        _controlState.value = _controlState.value.copy(isLiked = liked)
    }

    override fun resetSongAndQueue() {
        player.clearMediaItems()
        _nowPlaying.value = null
        _nowPlayingState.value = NowPlayingTrackState.initial()
        _queueData.value = null
    }

    override fun sleepStart(minutes: Int) {
        sleepJob?.cancel()
        sleepRemainingSec = minutes * 60
        _sleepTimerState.value = SleepTimerState(isDone = false, timeRemaining = sleepRemainingSec)
        sleepJob = scope.launch {
            while (sleepRemainingSec > 0) {
                delay(1_000L)
                sleepRemainingSec -= 1
                _sleepTimerState.value = SleepTimerState(isDone = false, timeRemaining = sleepRemainingSec)
            }
            player.pause()
            _sleepTimerState.value = SleepTimerState(isDone = true, timeRemaining = 0)
        }
    }

    override fun sleepStop() {
        sleepJob?.cancel()
        sleepJob = null
        sleepRemainingSec = 0
        _sleepTimerState.value = SleepTimerState(isDone = true, timeRemaining = 0)
    }

    override fun removeMediaItem(position: Int) {
        player.removeMediaItem(position)
    }

    override fun addMediaItem(mediaItem: GenericMediaItem, playWhenReady: Boolean) {
        player.addMediaItem(mediaItem)
        if (playWhenReady) {
            player.prepare()
            player.play()
        }
    }

    override fun clearMediaItems() {
        player.clearMediaItems()
    }

    override fun addMediaItemList(mediaItemList: List<GenericMediaItem>) {
        mediaItemList.forEach { player.addMediaItem(it) }
    }

    override fun playMediaItemInMediaSource(index: Int) {
        player.seekTo(index, 0L)
        player.prepare()
        player.play()
    }

    override fun currentSongIndex(): Int = player.currentMediaItemIndex

    override fun currentOrderIndex(): Int = player.currentMediaItemIndex

    override suspend fun swap(from: Int, to: Int) {
        player.moveMediaItem(from, to)
    }

    override fun resetCrossfade() {}

    override fun shufflePlaylist(randomTrackIndex: Int) {
        player.shuffleModeEnabled = true
    }

    override fun loadMore() {}

    override fun getRelated(videoId: String) {}

    override fun setQueueData(queueData: QueueData.Data) {
        _queueData.value = QueueData(data = queueData)
    }

    override fun getCurrentMediaItem(): GenericMediaItem? = player.currentMediaItem

    override suspend fun moveItemUp(position: Int) {
        if (position > 0) player.moveMediaItem(position, position - 1)
    }

    override suspend fun moveItemDown(position: Int) {
        if (position < player.mediaItemCount - 1) player.moveMediaItem(position, position + 1)
    }

    override fun addFirstMediaItemToIndex(mediaItem: GenericMediaItem?, index: Int) {
        if (mediaItem != null) player.addMediaItem(index, mediaItem)
    }

    override fun reset() {
        resetSongAndQueue()
    }

    private fun Track.toGenericMediaItem(streamUrl: String): GenericMediaItem =
        GenericMediaItem(
            mediaId = videoId,
            uri = streamUrl,
            metadata = GenericMediaMetadata(
                title = title,
                artist = artists?.joinToString(", ") { it.name },
                albumTitle = album?.name,
                artworkUri = thumbnails?.lastOrNull()?.url,
            ),
        )

    private suspend fun resolveStreamUrl(videoId: String, isVideo: Boolean = false): String? =
        try {
            streamRepository.getStream(
                dataStoreManager = dataStoreManager,
                videoId = videoId,
                isDownloading = false,
                isVideo = isVideo,
                muxed = false,
            ).firstOrNull()
        } catch (_: Throwable) {
            null
        }

    override suspend fun load(downloaded: Int, index: Int?) {
        val tracks = _queueData.value?.data?.listTracks ?: return
        if (tracks.isEmpty()) return
        player.clearMediaItems()
        tracks.forEach { track ->
            val streamUrl = resolveStreamUrl(track.videoId) ?: return@forEach
            player.addMediaItem(track.toGenericMediaItem(streamUrl))
        }
        val startIndex = (index ?: 0).coerceIn(0, (player.mediaItemCount - 1).coerceAtLeast(0))
        if (player.mediaItemCount > 0) {
            player.seekTo(startIndex, 0L)
            player.prepare()
            player.play()
        }
    }

    override suspend fun loadMoreCatalog(listTrack: ArrayList<Track>, isAddToQueue: Boolean) {
        listTrack.forEach { track ->
            val streamUrl = resolveStreamUrl(track.videoId) ?: return@forEach
            player.addMediaItem(track.toGenericMediaItem(streamUrl))
        }
    }

    override suspend fun updateCatalog(downloaded: Int, index: Int?): Boolean = false

    override fun addQueueToPlayer() {
        val tracks = _queueData.value?.data?.listTracks ?: return
        scope.launch {
            tracks.forEach { track ->
                val streamUrl = resolveStreamUrl(track.videoId) ?: return@forEach
                player.addMediaItem(track.toGenericMediaItem(streamUrl))
            }
        }
    }

    override fun loadPlaylistOrAlbum(index: Int?) {
        scope.launch { load(downloaded = 0, index = index) }
    }

    override fun setCurrentSongIndex(index: Int) {
        _currentSongIndex.value = index
    }

    override suspend fun playNext(track: Track) {
        val streamUrl = resolveStreamUrl(track.videoId) ?: return
        val item = track.toGenericMediaItem(streamUrl)
        val insertAt = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(insertAt, item)
    }

    override suspend fun <T> loadMediaItem(anyTrack: T, type: String, index: Int?) {
        val track = anyTrack as? Track ?: return
        val streamUrl = resolveStreamUrl(track.videoId) ?: run {
            showToast(ToastType.PlayerError("Could not resolve stream URL for ${track.title}"))
            return
        }
        val mediaItem = track.toGenericMediaItem(streamUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
    }

    override fun getPlayerDuration(): Long = player.duration

    override fun getProgress(): Long = player.currentPosition

    override fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (true) {
                val pos = player.currentPosition
                _simpleMediaState.value = SimpleMediaState.Progress(pos)
                delay(500L)
            }
        }
    }

    override fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }

    override fun startBufferedUpdate() {}

    override fun stopBufferedUpdate() {}

    override fun mayBeNormalizeVolume() {}

    override fun mayBeSaveRecentSong(runBlocking: Boolean) {}

    override fun mayBeSavePlaybackState() {}

    override fun mayBeRestoreQueue() {}

    override fun shouldReleaseOnTaskRemoved(): Boolean = false

    override fun release() {
        progressJob?.cancel()
        sleepJob?.cancel()
        player.removeListener(playerListener)
        player.release()
        MPNowPlayingInfoCenter.defaultCenter().nowPlayingInfo = null
    }

    companion object {
        private const val PLAYBACK_STATE_BUFFERING = 2
        private const val PLAYBACK_STATE_READY = 3
        private const val PLAYBACK_STATE_ENDED = 4
    }
}
