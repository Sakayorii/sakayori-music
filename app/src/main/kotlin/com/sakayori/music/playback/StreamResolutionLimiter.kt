/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

internal enum class StreamRequestPriority { FOREGROUND, BACKGROUND }
internal val foregroundStreamPriority = MutableStateFlow(StreamRequestPriority.FOREGROUND).asStateFlow()
internal val backgroundStreamPriority = MutableStateFlow(StreamRequestPriority.BACKGROUND).asStateFlow()

/** Reserve one resolution slot for playback. A queued preload can become foreground mid-wait. */
internal class StreamResolutionLimiter(maxConcurrent: Int = 4, maxBackground: Int = 3) {
    private val all = Semaphore(maxConcurrent)
    private val background = Semaphore(maxBackground)

    init {
        require(maxBackground in 1 until maxConcurrent)
    }

    suspend fun <T> withPermit(priority: StateFlow<StreamRequestPriority>, block: suspend () -> T): T {
        if (priority.value == StreamRequestPriority.FOREGROUND) return all.withPermit { block() }
        return coroutineScope {
            var holdsBackgroundPermit = false
            val backgroundSlot = async {
                background.acquire()
                holdsBackgroundPermit = true
            }
            val promotion = async { priority.first { it == StreamRequestPriority.FOREGROUND } }
            try {
                select<Unit> {
                    backgroundSlot.onAwait { }
                    promotion.onAwait { }
                }
                backgroundSlot.cancelAndJoin()
                promotion.cancelAndJoin()
                if (holdsBackgroundPermit && priority.value == StreamRequestPriority.FOREGROUND) {
                    background.release()
                    holdsBackgroundPermit = false
                }
                all.withPermit { block() }
            } finally {
                // Join before reading the ownership flag: acquire and promotion may have raced.
                withContext(NonCancellable) {
                    backgroundSlot.cancelAndJoin()
                    promotion.cancelAndJoin()
                }
                if (holdsBackgroundPermit) background.release()
            }
        }
    }
}
