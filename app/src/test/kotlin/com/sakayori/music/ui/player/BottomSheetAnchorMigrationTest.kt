/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.player

import android.app.Application
import android.os.Looper
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.sakayori.music.ui.component.BottomSheetState
import com.sakayori.music.ui.component.collapsedAnchor
import com.sakayori.music.ui.component.rememberBottomSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BottomSheetAnchorMigrationTest {
    private val clock = ManualFrameClock()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main + clock)
    private val recomposer = Recomposer(scope.coroutineContext)
    private val composition = Composition(UnitApplier(), recomposer)
    private val collapsedBound = mutableStateOf(40.dp)
    private lateinit var state: BottomSheetState
    private var fullContentWouldCompose = false

    @Before
    fun setUp() {
        scope.launch { recomposer.runRecomposeAndApplyChanges() }
        composition.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                state = rememberBottomSheetState(0.dp, 300.dp, collapsedBound.value, collapsedAnchor)
                // This is BottomSheet's actual expanded-content gate. No view,
                // artwork, renderer or Android app is run by this state fixture.
                fullContentWouldCompose = !state.isCollapsed
            }
        }
        advanceFrames(90)
        assertEquals(40.dp, state.value)
        assertTrue(state.isCollapsed)
        assertFalse(fullContentWouldCompose)
    }

    @After
    fun tearDown() {
        composition.dispose()
        recomposer.cancel()
        scope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun `raising a collapsed anchor cannot activate the full player while rebasing`() {
        collapsedBound.value = 100.dp
        advanceFrames(1)

        assertFalse("Collapsed navigation-bound migration must not mount full player content: ${stateDescription()}", fullContentWouldCompose)
        advanceFrames(90)
        assertEquals(100.dp, state.value)
        assertTrue(state.isCollapsed)
    }

    @Test
    fun `lowering a collapsed anchor cannot activate the full player while rebasing`() {
        collapsedBound.value = 100.dp
        advanceFrames(90)
        collapsedBound.value = 40.dp
        advanceFrames(1)

        assertFalse("Collapsed navigation-bound migration must not mount full player content", fullContentWouldCompose)
        advanceFrames(90)
        assertEquals(40.dp, state.value)
    }

    @Test
    fun `an actual expansion during a rebase still activates full content`() {
        collapsedBound.value = 100.dp
        advanceFrames(1)
        state.expandSoft()
        advanceFrames(3)

        assertTrue(fullContentWouldCompose)
        advanceFrames(90)
        assertTrue(state.isExpanded)
    }

    @Test
    fun `an actual collapse is not mistaken for an already collapsed rebase`() {
        state.expandSoft()
        advanceFrames(90)
        state.collapseSoft()
        advanceFrames(2)
        collapsedBound.value = 100.dp
        advanceFrames(1)

        assertTrue("Full content must stay mounted through a real collapse", fullContentWouldCompose)
        advanceFrames(90)
        assertTrue(state.isCollapsed)
    }

    @Test
    fun `a newer collapsed bound retains protection when the old animation is cancelled`() {
        collapsedBound.value = 100.dp
        advanceFrames(1)
        collapsedBound.value = 80.dp
        advanceFrames(2)

        assertFalse(stateDescription(), fullContentWouldCompose)
        advanceFrames(90)
        assertEquals(80.dp, state.value)
        assertTrue(state.isCollapsed)
    }

    @Test
    fun `a drag during collapsed rebasing can activate content immediately`() {
        collapsedBound.value = 100.dp
        advanceFrames(1)
        state.dispatchRawDelta(-10f)
        advanceFrames(3)

        assertTrue(stateDescription(), fullContentWouldCompose)
    }

    @Test
    fun `an expansion callback from the outgoing wrapper cannot leave new content suppressed`() {
        val outgoing = state
        collapsedBound.value = 100.dp
        advanceFrames(1)
        outgoing.expandSoft()
        advanceFrames(3)

        assertTrue(fullContentWouldCompose)
        advanceFrames(90)
        assertTrue(state.isExpanded)
    }

    private fun advanceFrames(count: Int) {
        repeat(count) {
            // A runtime-only Composition has no Android ComposeView owner to
            // start GlobalSnapshotManager. Publish state writes explicitly.
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            clock.advance()
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
        }
    }

    private fun stateDescription(): String {
        return "value=${state.value}, bound=${state.collapsedBound}, collapsed=${state.isCollapsed}, composed=$fullContentWouldCompose"
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
