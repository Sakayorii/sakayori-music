@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sakayori.data.mediaservice

import com.sakayori.domain.data.player.GenericMediaItem
import com.sakayori.domain.data.player.GenericPlaybackParameters
import com.sakayori.domain.mediaservice.player.MediaPlayerInterface
import com.sakayori.domain.mediaservice.player.MediaPlayerListener
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.AVFoundation.AVPlayerTimeControlStatusPaused
import platform.AVFoundation.AVPlayerTimeControlStatusPlaying
import platform.AVFoundation.AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.AVFoundation.replaceCurrentItemWithPlayerItem
import platform.AVFoundation.seekToTime
import platform.AVFoundation.timeControlStatus
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL

internal class AVPlayerMediaPlayerInterface : MediaPlayerInterface {
    private val player: AVPlayer = AVPlayer()
    private val queue = mutableListOf<GenericMediaItem>()
    private val listeners = mutableListOf<MediaPlayerListener>()
    private var currentIndex: Int = -1
    private var shuffleEnabled: Boolean = false
    private var repeatModeValue: Int = 0
    private var playWhenReadyValue: Boolean = false
    private var playbackParametersValue: GenericPlaybackParameters = GenericPlaybackParameters.DEFAULT
    private var volumeValue: Float = 1.0f
    private var skipSilenceEnabledValue: Boolean = false

    init {
        setupAudioSession()
        observeTrackEnd()
    }

    private fun setupAudioSession() {
        try {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryPlayback, null)
            session.setActive(true, null)
        } catch (_: Throwable) {
        }
    }

    private fun observeTrackEnd() {
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ ->
            listeners.toList().forEach { listener ->
                listener.onPlaybackStateChanged(STATE_ENDED)
            }
            if (hasNextMediaItem()) {
                seekToNext()
                if (playWhenReadyValue) play()
            }
        }
    }

    private fun loadItemAt(index: Int) {
        if (index !in queue.indices) {
            player.replaceCurrentItemWithPlayerItem(null)
            return
        }
        val mediaItem = queue[index]
        val streamUrl = mediaItem.uri
            ?: mediaItem.mediaId.takeIf { it.startsWith("http") }
            ?: return
        val nsUrl = NSURL.URLWithString(streamUrl) ?: return
        val playerItem = AVPlayerItem.playerItemWithURL(nsUrl)
        player.replaceCurrentItemWithPlayerItem(playerItem)
        listeners.toList().forEach { it.onMediaItemTransition(mediaItem, 0) }
    }

    override fun play() {
        playWhenReadyValue = true
        player.play()
        listeners.toList().forEach { it.onIsPlayingChanged(true) }
    }

    override fun pause() {
        playWhenReadyValue = false
        player.pause()
        listeners.toList().forEach { it.onIsPlayingChanged(false) }
    }

    override fun stop() {
        player.pause()
        player.seekToTime(CMTimeMake(0, 1))
        listeners.toList().forEach { it.onIsPlayingChanged(false) }
    }

    override fun seekTo(positionMs: Long) {
        val time = CMTimeMake(value = positionMs, timescale = 1000)
        player.seekToTime(time)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        if (mediaItemIndex != currentIndex) {
            currentIndex = mediaItemIndex
            loadItemAt(mediaItemIndex)
        }
        seekTo(positionMs)
    }

    override fun seekBack() {
        val newPos = (currentPosition - 10_000L).coerceAtLeast(0L)
        seekTo(newPos)
    }

    override fun seekForward() {
        val newPos = (currentPosition + 10_000L).coerceAtMost(duration)
        seekTo(newPos)
    }

    override fun seekToNext() {
        if (hasNextMediaItem()) {
            currentIndex++
            loadItemAt(currentIndex)
        }
    }

    override fun seekToPrevious() {
        if (hasPreviousMediaItem()) {
            currentIndex--
            loadItemAt(currentIndex)
        }
    }

    override fun prepare() {}

    override fun setMediaItem(mediaItem: GenericMediaItem) {
        queue.clear()
        queue.add(mediaItem)
        currentIndex = 0
        loadItemAt(0)
        listeners.toList().forEach { it.onTimelineChanged(queue.toList(), "setMediaItem") }
    }

    override fun addMediaItem(mediaItem: GenericMediaItem) {
        queue.add(mediaItem)
        if (currentIndex < 0) {
            currentIndex = 0
            loadItemAt(0)
        }
        listeners.toList().forEach { it.onTimelineChanged(queue.toList(), "addMediaItem") }
    }

    override fun addMediaItem(index: Int, mediaItem: GenericMediaItem) {
        val safeIndex = index.coerceIn(0, queue.size)
        queue.add(safeIndex, mediaItem)
        if (safeIndex <= currentIndex) currentIndex++
        listeners.toList().forEach { it.onTimelineChanged(queue.toList(), "addMediaItemAtIndex") }
    }

    override fun removeMediaItem(index: Int) {
        if (index !in queue.indices) return
        queue.removeAt(index)
        if (index == currentIndex) {
            if (queue.isEmpty()) {
                currentIndex = -1
                player.replaceCurrentItemWithPlayerItem(null)
            } else {
                currentIndex = currentIndex.coerceAtMost(queue.size - 1)
                loadItemAt(currentIndex)
            }
        } else if (index < currentIndex) {
            currentIndex--
        }
        listeners.toList().forEach { it.onTimelineChanged(queue.toList(), "removeMediaItem") }
    }

    override fun moveMediaItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in queue.indices || toIndex !in queue.indices) return
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
        currentIndex = when {
            fromIndex == currentIndex -> toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
        listeners.toList().forEach { it.onTimelineChanged(queue.toList(), "moveMediaItem") }
    }

    override fun clearMediaItems() {
        queue.clear()
        currentIndex = -1
        player.replaceCurrentItemWithPlayerItem(null)
        listeners.toList().forEach { it.onTimelineChanged(emptyList(), "clearMediaItems") }
    }

    override fun replaceMediaItem(index: Int, mediaItem: GenericMediaItem) {
        if (index !in queue.indices) return
        queue[index] = mediaItem
        if (index == currentIndex) loadItemAt(index)
        listeners.toList().forEach { it.onTimelineChanged(queue.toList(), "replaceMediaItem") }
    }

    override fun getMediaItemAt(index: Int): GenericMediaItem? = queue.getOrNull(index)

    override fun getCurrentMediaTimeLine(): List<GenericMediaItem> = queue.toList()

    override fun getUnshuffledIndex(shuffledIndex: Int): Int = shuffledIndex

    override val isPlaying: Boolean
        get() = player.timeControlStatus == AVPlayerTimeControlStatusPlaying

    override val currentPosition: Long
        get() {
            val item = player.currentItem ?: return 0L
            val seconds = CMTimeGetSeconds(player.currentTime())
            if (seconds.isNaN() || seconds < 0.0) return 0L
            return (seconds * 1000.0).toLong()
        }

    override val duration: Long
        get() {
            val item = player.currentItem ?: return 0L
            val seconds = CMTimeGetSeconds(item.duration)
            if (seconds.isNaN() || seconds < 0.0) return 0L
            return (seconds * 1000.0).toLong()
        }

    override val bufferedPosition: Long
        get() = currentPosition

    override val bufferedPercentage: Int
        get() = if (duration > 0) ((bufferedPosition * 100) / duration).toInt() else 0

    override val currentMediaItem: GenericMediaItem?
        get() = queue.getOrNull(currentIndex)

    override val currentMediaItemIndex: Int
        get() = currentIndex.coerceAtLeast(0)

    override val mediaItemCount: Int
        get() = queue.size

    override val contentPosition: Long
        get() = currentPosition

    override val playbackState: Int
        get() {
            val status = player.currentItem?.status
            return when {
                status == AVPlayerItemStatusReadyToPlay -> STATE_READY
                status == AVPlayerItemStatusFailed -> STATE_IDLE
                player.timeControlStatus == AVPlayerTimeControlStatusWaitingToPlayAtSpecifiedRate -> STATE_BUFFERING
                player.timeControlStatus == AVPlayerTimeControlStatusPaused -> STATE_READY
                else -> STATE_IDLE
            }
        }

    override fun hasNextMediaItem(): Boolean = currentIndex >= 0 && currentIndex < queue.size - 1

    override fun hasPreviousMediaItem(): Boolean = currentIndex > 0

    override var shuffleModeEnabled: Boolean
        get() = shuffleEnabled
        set(value) {
            shuffleEnabled = value
            listeners.toList().forEach { it.onShuffleModeEnabledChanged(value, queue.toList()) }
        }

    override var repeatMode: Int
        get() = repeatModeValue
        set(value) {
            repeatModeValue = value
            listeners.toList().forEach { it.onRepeatModeChanged(value) }
        }

    override var playWhenReady: Boolean
        get() = playWhenReadyValue
        set(value) {
            playWhenReadyValue = value
            if (value) player.play() else player.pause()
        }

    override var playbackParameters: GenericPlaybackParameters
        get() = playbackParametersValue
        set(value) {
            playbackParametersValue = value
        }

    override val audioSessionId: Int = 0

    override var volume: Float
        get() = volumeValue
        set(value) {
            volumeValue = value
            listeners.toList().forEach { it.onVolumeChanged(value) }
        }

    override var skipSilenceEnabled: Boolean
        get() = skipSilenceEnabledValue
        set(value) {
            skipSilenceEnabledValue = value
        }

    override fun addListener(listener: MediaPlayerListener) {
        if (listener !in listeners) listeners.add(listener)
    }

    override fun removeListener(listener: MediaPlayerListener) {
        listeners.remove(listener)
    }

    override fun release() {
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
        listeners.clear()
        queue.clear()
    }

    companion object {
        private const val STATE_IDLE = 1
        private const val STATE_BUFFERING = 2
        private const val STATE_READY = 3
        private const val STATE_ENDED = 4
    }
}
