/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.utils

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.sakayori.music.db.entities.LyricsEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
class PlayerLyricsStateTest {
    private val clock = ManualFrameClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + clock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private val composition = Composition(UnitApplier(), recomposer)
    private val mounted = mutableStateOf(false)
    private val source = MutableStateFlow<LyricsEntity?>(null)
    private val observed = mutableListOf<LyricsEntity?>()
    private val queue = MutableStateFlow<List<Int>>(emptyList())
    private val index = MutableStateFlow(-1)
    private val observedQueues = mutableListOf<List<Int>>()
    private val observedIndices = mutableListOf<Int>()
    private lateinit var lifecycle: LifecycleRegistry
    private val owner = object : LifecycleOwner {
        override val lifecycle: Lifecycle get() = this@PlayerLyricsStateTest.lifecycle
    }

    @Before
    fun setUp() {
        lifecycle = LifecycleRegistry(owner).apply { currentState = Lifecycle.State.RESUMED }
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        composition.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                if (mounted.value) {
                    val lyrics = source.collectLyricsAsState().value
                    val queueValue = queue.collectLyricsQueueAsState().value
                    val indexValue = index.collectLyricsWindowAsState().value
                    // Records the state that the real pane's first composition sees,
                    // before a later collection can replace a fabricated loading state.
                    SideEffect {
                        observed += lyrics
                        observedQueues += queueValue
                        observedIndices += indexValue
                    }
                }
            }
        }
        advanceFrames(2)
    }

    @After
    fun tearDown() {
        composition.dispose()
        recomposer.cancel()
        scope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `cached lyrics are available in the opening composition`() {
        val cached = LyricsEntity("song", "[00:01.00]Already loaded", "Fixture")
        source.value = cached
        open()
        assertSame(cached, observed.first())
    }

    @Test
    fun `known missing lyrics do not create a fresh loading state on opening`() {
        val missing = LyricsEntity("song", LyricsEntity.LYRICS_NOT_FOUND)
        source.value = missing
        open()
        assertSame(missing, observed.first())
    }

    @Test
    fun `reopening the same loaded song does not restart at null`() {
        val cached = LyricsEntity("song", "[00:01.00]Already loaded", "Fixture")
        source.value = cached
        open()
        close()
        observed.clear()
        open()
        assertSame(cached, observed.first())
        assertFalse(observed.contains(null))
    }

    @Test
    fun `a result received while closed is used immediately on reopening`() {
        source.value = LyricsEntity("first", "First lyrics")
        open()
        close()
        val latest = LyricsEntity("second", "Second lyrics")
        source.value = latest
        observed.clear()
        open()
        assertSame(latest, observed.first())
    }

    @Test
    fun `an actually pending lookup remains null until its result arrives`() {
        open()
        assertNull(observed.first())
        val result = LyricsEntity("song", "Arrived")
        source.value = result
        pumpUntil { observed.lastOrNull() === result }
        assertSame(result, observed.last())
    }

    @Test
    fun `lifecycle still stops and resumes collection`() {
        val first = LyricsEntity("song", "First")
        source.value = first
        queue.value = listOf(0, 1)
        index.value = 0
        open()
        pumpUntil { observedQueues.lastOrNull() == listOf(0, 1) && observedIndices.lastOrNull() == 0 }
        assertSame(first, observed.last())
        lifecycle.currentState = Lifecycle.State.CREATED
        pumpUntil { source.subscriptionCount.value == 0 && queue.subscriptionCount.value == 0 && index.subscriptionCount.value == 0 }
        val later = LyricsEntity("song", "Later")
        source.value = later
        queue.value = listOf(1, 2)
        index.value = 1
        advanceFrames(10)
        assertSame(first, observed.last())
        org.junit.Assert.assertEquals(listOf(0, 1), observedQueues.last())
        org.junit.Assert.assertEquals(0, observedIndices.last())
        lifecycle.currentState = Lifecycle.State.RESUMED
        pumpUntil { observed.lastOrNull() === later && observedQueues.lastOrNull() == listOf(1, 2) && observedIndices.lastOrNull() == 1 }
        assertSame(later, observed.last())
    }

    @Test
    fun `a known queue is available without a fabricated empty first composition`() {
        val cached = listOf(0, 1, 2)
        queue.value = cached
        open()
        assertSame(cached, observedQueues.first())
    }

    @Test
    fun `a known current window is available without a fabricated unset first composition`() {
        index.value = 1
        open()
        org.junit.Assert.assertEquals(1, observedIndices.first())
    }

    private fun open() {
        mounted.value = true
        advanceFrames(1)
        pumpUntil { source.subscriptionCount.value > 0 }
        pumpUntil { observed.isNotEmpty() && observed.last() === source.value }
        assertTrue("The observation boundary must have composed", observed.isNotEmpty())
    }

    private fun close() {
        mounted.value = false
        advanceFrames(1)
        pumpUntil { source.subscriptionCount.value == 0 }
    }

    private fun pumpUntil(done: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (!done() && System.nanoTime() < deadline) {
            advanceFrames(1)
            Thread.yield()
        }
        assertTrue("Composition and lifecycle collection reached the expected state", done())
    }

    private fun advanceFrames(count: Int) {
        repeat(count) {
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            clock.advance()
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private class UnitApplier : AbstractApplier<Unit>(Unit) {
        override fun insertTopDown(index: Int, instance: Unit) = Unit
        override fun insertBottomUp(index: Int, instance: Unit) = Unit
        override fun move(from: Int, to: Int, count: Int) = Unit
        override fun remove(index: Int, count: Int) = Unit
        override fun onClear() = Unit
    }

    private class ManualFrameClock : MonotonicFrameClock {
        private val callbacks = mutableListOf<(Long) -> Unit>()
        private var time = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R = suspendCancellableCoroutine { continuation ->
            callbacks += { frame ->
                if (continuation.isActive) continuation.resumeWith(runCatching { onFrame(frame) })
            }
        }
        fun advance() {
            time += 16_666_667L
            val pending = callbacks.toList()
            callbacks.clear()
            pending.forEach { it(time) }
        }
    }
}
