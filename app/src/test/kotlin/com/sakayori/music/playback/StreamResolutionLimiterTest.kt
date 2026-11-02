package com.sakayori.music.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StreamResolutionLimiterTest {
    @Test
    fun `background work leaves the final slot for playback`() = runBlocking {
        val limiter = StreamResolutionLimiter(2, 1)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async { limiter.withPermit(backgroundStreamPriority) { entered.complete(Unit); release.await() } }
        entered.await()
        val second = async { limiter.withPermit(backgroundStreamPriority) { "background" } }
        yield()
        assertFalse(second.isCompleted)
        assertEquals("playback", withTimeout(2_000) { limiter.withPermit(foregroundStreamPriority) { "playback" } })
        release.complete(Unit)
        first.await()
        assertEquals("background", second.await())
    }

    @Test
    fun `queued next item is promoted without waiting behind downloads`() = runBlocking {
        val limiter = StreamResolutionLimiter(2, 1)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val download = async { limiter.withPermit(backgroundStreamPriority) { entered.complete(Unit); release.await() } }
        entered.await()
        val priority = MutableStateFlow(StreamRequestPriority.BACKGROUND)
        val preload = async { limiter.withPermit(priority) { "next item" } }
        yield()
        assertFalse(preload.isCompleted)
        priority.value = StreamRequestPriority.FOREGROUND
        assertEquals("next item", withTimeout(2_000) { preload.await() })
        release.complete(Unit)
        download.await()
    }

    @Test
    fun `cancelling a queued preload does not leak a background permit`() = runBlocking {
        val limiter = StreamResolutionLimiter(2, 1)
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val first = async { limiter.withPermit(backgroundStreamPriority) { entered.complete(Unit); release.await() } }
        entered.await()
        val cancelled = async { limiter.withPermit(backgroundStreamPriority) { } }
        yield()
        cancelled.cancelAndJoin()
        release.complete(Unit)
        first.await()
        assertEquals("available", withTimeout(2_000) { limiter.withPermit(backgroundStreamPriority) { "available" } })
    }

    @Test
    fun `failure releases both foreground and background limits`() = runBlocking {
        val limiter = StreamResolutionLimiter(2, 1)
        runCatching { limiter.withPermit(backgroundStreamPriority) { throw IllegalStateException("fixture") } }
        assertEquals("available", withTimeout(2_000) { limiter.withPermit(backgroundStreamPriority) { "available" } })
    }
}
