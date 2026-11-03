/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import androidx.media3.common.C
import androidx.media3.common.Timeline
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource.MediaPeriodId
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import kotlinx.coroutines.flow.StateFlow

/** One controller per ExoPlayer. The native MediaPeriodQueue owns preparation and cancellation. */
internal class NextItemPreloadController {
    data class Target(
        val windowUid: Any,
        val mediaId: String,
        val selectionVersion: Long,
        val contextRevision: Long,
        val disabledClients: Set<String>,
    )

    private data class Pending(val mediaId: String, val periodId: MediaPeriodId, val budget: PreloadReadBudget)
    private val lock = Any()
    private var target: Target? = null
    private var pending: Pending? = null
    private var foregroundLoading = false
    // Used only by Media3's playback thread, never by update() or datasource loader threads.
    private val period = Timeline.Period()
    private val window = Timeline.Window()

    /** Called on the application thread. Returns whether the native preload pool must be reset. */
    fun update(next: Target?, activeMediaId: String?): Boolean = synchronized(lock) {
        pending?.takeIf { it.mediaId == activeMediaId }?.budget?.promote()
        if (target == next) return@synchronized false
        pending?.budget?.cancel()
        pending = null
        target = next
        true
    }

    fun release() = synchronized(lock) {
        target = null
        pending?.budget?.cancel()
        pending = null
    }

    fun priority(mediaId: String): StateFlow<StreamRequestPriority> = synchronized(lock) {
        pending?.takeIf { it.mediaId == mediaId }?.budget?.resolutionPriority ?: foregroundStreamPriority
    }

    // Java default interface methods are not forwarded by Kotlin's `by` delegation.
    // Explicitly forward the Media3 1.10.1 entry points to the configured load control.
    fun wrap(delegate: LoadControl): LoadControl = object : LoadControl {
        override fun onPrepared(playerId: PlayerId) = delegate.onPrepared(playerId)

        override fun onTracksSelected(
            parameters: LoadControl.Parameters,
            trackGroups: TrackGroupArray,
            trackSelections: Array<out ExoTrackSelection?>,
        ) = delegate.onTracksSelected(parameters, trackGroups, trackSelections)

        override fun onStopped(playerId: PlayerId) = delegate.onStopped(playerId)

        override fun onReleased(playerId: PlayerId) = delegate.onReleased(playerId)

        override fun getAllocator(playerId: PlayerId): Allocator = delegate.getAllocator(playerId)

        override fun getBackBufferDurationUs(playerId: PlayerId): Long =
            delegate.getBackBufferDurationUs(playerId)

        override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean =
            delegate.retainBackBufferFromKeyframe(playerId)

        override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean =
            delegate.shouldStartPlayback(parameters)

        override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
            // Native preloaded periods are promoted into the normal queue before playback needs
            // them. Remove their read restriction at that point, even before the UI event arrives.
            synchronized(lock) {
                pending?.takeIf { it.periodId == parameters.mediaPeriodId }?.budget?.promote()
            }
            return delegate.shouldContinueLoading(parameters).also { loading ->
                synchronized(lock) {
                    foregroundLoading = loading
                    pending?.budget?.setForegroundLoading(loading)
                }
            }
        }

        override fun shouldContinuePreloading(
            playerId: PlayerId,
            timeline: Timeline,
            mediaPeriodId: MediaPeriodId,
            bufferedDurationUs: Long,
        ): Boolean {
            if (!delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)) return false
            if (bufferedDurationUs == C.TIME_END_OF_SOURCE || bufferedDurationUs >= TARGET_DURATION_US) return false
            val index = timeline.getIndexOfPeriod(mediaPeriodId.periodUid)
            if (index == C.INDEX_UNSET) return false
            timeline.getPeriod(index, period)
            timeline.getWindow(period.windowIndex, window)
            return synchronized(lock) {
                val desired = target ?: return@synchronized false
                // Media3 preloads next-to-LOADING, which can be farther ahead than next-to-playing.
                if (window.uid != desired.windowUid || window.mediaItem.mediaId != desired.mediaId || window.isLive) {
                    return@synchronized false
                }
                if (pending?.periodId != mediaPeriodId) {
                    pending?.budget?.cancel()
                    pending = Pending(desired.mediaId, mediaPeriodId, PreloadReadBudget(MAX_BYTES)).also {
                        it.budget.setForegroundLoading(foregroundLoading)
                    }
                }
                pending?.budget?.isSpeculative == true
            }
        }
    }

    fun wrap(factory: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val delegate = factory.createDataSource()
        object : DataSource by delegate {
            private var budget: PreloadReadBudget? = null

            init {
                // Count the actual underlying transfers, including cached reads and HTTP's
                // internal skip when a server ignores Range. Counting only read()'s return
                // values would miss those bytes consumed during open().
                delegate.addTransferListener(object : TransferListener {
                    override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
                    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
                    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit

                    override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                        budget?.let {
                            it.awaitReadLength(1)
                            it.onBytesRead(bytesTransferred)
                        }
                    }
                })
            }

            override fun open(dataSpec: DataSpec): Long {
                budget = synchronized(lock) {
                    pending?.takeIf { it.mediaId == dataSpec.key && it.budget.isSpeculative }?.budget
                }
                budget?.awaitReadLength(1)
                return delegate.open(dataSpec)
            }

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                val allowed = budget?.awaitReadLength(length) ?: length
                return delegate.read(buffer, offset, allowed)
            }
        }
    }

    companion object {
        const val TARGET_DURATION_US = 2_000_000L
        // An underlying transfer already in flight can exceed this boundary by one read.
        // Socket buffers and metadata objects are additional overhead, not measured here.
        const val MAX_BYTES = 512 * 1024L
    }
}
