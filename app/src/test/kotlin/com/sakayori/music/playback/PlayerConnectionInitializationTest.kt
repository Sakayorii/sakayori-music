/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.app.Application
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sakayori.innertube.models.SongItem
import com.sakayori.music.db.InternalDatabase
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.extensions.toMediaItem
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
class PlayerConnectionInitializationTest {
    private lateinit var context: Application
    private lateinit var database: MusicDatabase
    private lateinit var service: MusicService
    private val owners = mutableListOf<ConnectionOwner>()
    private val connections = mutableListOf<PlayerConnection>()
    private val players = mutableListOf<ExoPlayer>()
    private val coroutineFailures = mutableListOf<Throwable>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = MusicDatabase(Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).build())
        // Attach the real service without onCreate's Hilt, audio, cache or network work.
        service = Robolectric.buildService(MusicService::class.java).get()
        assertSame(Looper.getMainLooper(), Looper.myLooper())
        assertFalse(Dispatchers.Main.immediate.isDispatchNeeded(EmptyCoroutineContext))
    }

    @After
    fun tearDown() {
        connections.forEach { it.dispose() }
        owners.forEach { it.lifecycle.currentState = Lifecycle.State.DESTROYED }
        ReflectionHelpers.getField<CoroutineScope>(service, "scope").cancel()
        shadowOf(Looper.getMainLooper()).idle()
        players.forEach { it.release() }
        database.close()
    }

    @Test
    fun `cold service connection consumes initial committed state without a coroutine crash`() {
        assertFalse(service.isPlayerReady.value)
        assertTrue(service.queueLoadState.value.committed)

        val connection = connect()

        assertNoCoroutineFailure()
        assertNull(connection.queueTitle.value)
        assertEquals(Player.STATE_IDLE, connection.playbackState.value)
        assertFalse(connection.isPlaying.value)
        assertTrue(connection.queueWindows.value.isEmpty())
    }

    @Test
    fun `initial committed title is delivered synchronously even before player readiness`() {
        service.queueTitle = "Restored playlist"

        val connection = connect()

        assertNoCoroutineFailure()
        assertEquals("Restored playlist", connection.queueTitle.value)
        publishCommit(1, "New playlist")
        assertEquals("New playlist", connection.queueTitle.value)
        publishCommit(2, null)
        assertNull(connection.queueTitle.value)
        assertNoCoroutineFailure()
    }

    @Test
    fun `connection to a ready player preserves the existing queue and selected occurrence`() {
        val player = publishPlayer()
        service.queueTitle = "Liked"

        val connection = connect()

        assertNoCoroutineFailure()
        assertSame(player, connection.player)
        assertEquals("Liked", connection.queueTitle.value)
        assertEquals(listOf("a", "b", "c"), connection.queueWindows.value.map { it.mediaItem.mediaId })
        assertEquals(1, connection.currentMediaItemIndex.value)
        assertEquals("b", connection.mediaMetadata.value?.id)
    }

    @Test
    fun `pending load followed by a commit publishes the title without dropping the update`() {
        loadState().value = QueueLoadState(generation = 1, committed = false)
        service.queueTitle = "Pending playlist"
        val connection = connect()

        assertNoCoroutineFailure()
        assertNull(connection.queueTitle.value)
        publishCommit(1, "Completed playlist")

        assertEquals("Completed playlist", connection.queueTitle.value)
        assertNoCoroutineFailure()
    }

    @Test
    fun `deferred main dispatcher is a scheduling control for initial committed state`() {
        service.queueTitle = "Deferred playlist"
        val connection = connect(deferred = true)
        shadowOf(Looper.getMainLooper()).idle()

        assertNoCoroutineFailure()
        assertEquals("Deferred playlist", connection.queueTitle.value)
    }

    @Test
    fun `cold connection receives a later player publication with fully initialized state`() {
        val connection = connect()
        assertNoCoroutineFailure()
        service.queueTitle = "Ready later"

        val player = publishPlayer()

        assertNoCoroutineFailure()
        assertSame(player, connection.player)
        assertEquals("Ready later", connection.queueTitle.value)
        assertEquals(1, connection.currentMediaItemIndex.value)
        assertEquals(3, connection.queueWindows.value.size)
    }

    @Test
    fun `three immediate reconnections retain a live service queue and accept later commits`() {
        publishPlayer()
        repeat(3) { cycle ->
            service.queueTitle = "Connection $cycle"
            val connection = connect()
            assertNoCoroutineFailure()
            assertEquals("Connection $cycle", connection.queueTitle.value)
            publishCommit(cycle + 1, "Committed $cycle")
            assertEquals("Committed $cycle", connection.queueTitle.value)
            connection.dispose()
            owners.last().lifecycle.currentState = Lifecycle.State.DESTROYED
        }
        assertNoCoroutineFailure()
    }

    @Test
    fun `destroyed connection lifecycle stops collecting queue commits`() {
        loadState().value = QueueLoadState(generation = 1, committed = false)
        val connection = connect()
        publishCommit(1, "Last live title")
        assertEquals("Last live title", connection.queueTitle.value)

        owners.last().lifecycle.currentState = Lifecycle.State.DESTROYED
        publishCommit(2, "Next owner's title")

        assertEquals("Last live title", connection.queueTitle.value)
        assertNoCoroutineFailure()
    }

    private fun connect(deferred: Boolean = false): PlayerConnection {
        val owner = ConnectionOwner().also { owners += it }
        // MainActivity.onServiceConnected passes its lifecycleScope on Main. Keep
        // that real Main.immediate dispatcher and lifecycle job; capture child
        // failures so a SupervisorJob cannot make a crashing collector pass a test.
        val handler = CoroutineExceptionHandler { _, failure -> coroutineFailures += failure }
        val scope = CoroutineScope(
            owner.lifecycleScope.coroutineContext + handler +
                if (deferred) Dispatchers.Main else EmptyCoroutineContext,
        )
        return PlayerConnection(context, service.MusicBinder(), database, scope).also { connections += it }
    }

    private fun publishPlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(context).build().also { players += it }
        player.setMediaItems(listOf("a", "b", "c").map {
            SongItem(it, it, emptyList(), thumbnail = "").toMediaItem()
        }, 1, 0L)
        // Real native timeline and callbacks, never prepare network/audio sources.
        ReflectionHelpers.setField(service, "player", player)
        ReflectionHelpers.getField<MutableStateFlow<Boolean>>(service, "playerInitialized").value = true
        ReflectionHelpers.getField<MutableStateFlow<ExoPlayer?>>(service, "_playerFlow").value = player
        return player
    }

    private fun loadState(): MutableStateFlow<QueueLoadState> = ReflectionHelpers.getField(service, "_queueLoadState")

    private fun publishCommit(generation: Int, title: String?) {
        service.queueTitle = title
        loadState().value = QueueLoadState(generation, committed = true)
    }

    private fun assertNoCoroutineFailure() {
        coroutineFailures.firstOrNull()?.let { throw AssertionError("PlayerConnection collector crashed", it) }
    }

    private class ConnectionOwner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
    }
}
