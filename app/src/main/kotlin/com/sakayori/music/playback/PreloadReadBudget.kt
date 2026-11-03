/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import java.io.InterruptedIOException
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * A loader-side read budget, not an artificial end of the file. At the boundary the loader waits
 * until its period becomes ordinary playback or Media3 cancels it. No player/main thread waits.
 */
internal class PreloadReadBudget(maxBytes: Long) {
    private val lock = ReentrantLock()
    private val changed = lock.newCondition()
    private var remaining = maxBytes
    private var foregroundLoading = false
    private var promoted = false
    private var cancelled = false
    private val mutablePriority = MutableStateFlow(StreamRequestPriority.BACKGROUND)
    val resolutionPriority = mutablePriority.asStateFlow()

    init {
        require(maxBytes > 0)
    }

    val isSpeculative: Boolean
        get() = lock.withLock { !promoted && !cancelled }

    fun awaitReadLength(requested: Int): Int {
        if (requested == 0) return 0
        require(requested > 0)
        try {
            return lock.withLock {
                while (!promoted && !cancelled && (foregroundLoading || remaining == 0L)) changed.await()
                if (promoted) requested
                else {
                    if (cancelled) throw InterruptedIOException("Preload cancelled")
                    minOf(requested.toLong(), remaining).toInt()
                }
            }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Preload interrupted").apply { initCause(interrupted) }
        }
    }

    fun onBytesRead(bytes: Int) = lock.withLock {
        if (!promoted && bytes > 0) remaining = (remaining - bytes).coerceAtLeast(0L)
    }

    fun setForegroundLoading(loading: Boolean) = lock.withLock {
        foregroundLoading = loading
        if (!loading) changed.signalAll()
    }

    fun promote() = lock.withLock {
        promoted = true
        mutablePriority.value = StreamRequestPriority.FOREGROUND
        changed.signalAll()
    }

    fun cancel() = lock.withLock {
        cancelled = true
        changed.signalAll()
    }

    /** The same bounded reader can prepare a muted rendition and then be promoted in place. */
    fun wrap(factory: DataSource.Factory): DataSource.Factory = DataSource.Factory {
        val source = factory.createDataSource()
        source.addTransferListener(object : TransferListener {
            override fun onTransferInitializing(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
            override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
            override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) = Unit
            override fun onBytesTransferred(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean, bytesTransferred: Int) {
                awaitReadLength(1)
                onBytesRead(bytesTransferred)
            }
        })
        object : DataSource by source {
            override fun open(dataSpec: DataSpec): Long {
                awaitReadLength(1)
                return source.open(dataSpec)
            }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                source.read(buffer, offset, awaitReadLength(length))
        }
    }
}
