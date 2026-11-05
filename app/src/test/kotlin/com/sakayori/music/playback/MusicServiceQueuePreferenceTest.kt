/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.sakayori.innertube.models.SongItem
import com.sakayori.music.constants.PreventDuplicateTracksInQueueKey
import com.sakayori.music.extensions.toMediaItem
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class MusicServiceQueuePreferenceTest {
    private val storeField = Class.forName("com.sakayori.music.utils.DataStoreKt")
        .getDeclaredField("dataStoreInstance").apply { isAccessible = true }
    private var previousStore: Any? = null
    private lateinit var service: MusicService
    private lateinit var nativePlayer: ExoPlayer

    @Before
    fun setUp() {
        previousStore = storeField.get(null)
        observePreferences(emptyPreferences())
        service = Robolectric.buildService(MusicService::class.java).get()
        nativePlayer = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Application>()).build()
        val player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "QueuePreferencePlayer"
                "prepare", "setPlayWhenReady" -> null
                "getPlaybackState" -> Player.STATE_READY
                else -> try {
                    method.invoke(nativePlayer, *(args ?: emptyArray()))
                } catch (failure: InvocationTargetException) {
                    throw failure.targetException
                }
            }
        } as ExoPlayer
        ReflectionHelpers.setField(service, "player", player)
        ReflectionHelpers.setField(service, "startupPrefs", emptyPreferences())
        nativePlayer.setMediaItems(listOf(item("current")))
    }

    @After
    fun tearDown() {
        ReflectionHelpers.getField<CoroutineScope>(service, "scope").cancel()
        shadowOf(Looper.getMainLooper()).idle()
        nativePlayer.release()
        storeField.set(null, previousStore)
    }

    @Test
    fun `add to queue leaves Main input runnable while preference IO is held`() {
        assertInputResponds { service.addToQueue(listOf(item("new"))) }
    }

    @Test
    fun `play next leaves Main input runnable while preference IO is held`() {
        assertInputResponds { service.playNext(listOf(item("new"))) }
    }

    @Test
    fun `missing duplicate policy preserves duplicates`() {
        nativePlayer.setMediaItems(listOf(item("current"), item("duplicate")))
        service.addToQueue(listOf(item("duplicate")))
        assertEquals(listOf("current", "duplicate", "duplicate"), ids())
    }

    @Test
    fun `persisted duplicate policy removes later copies but retains current occurrence`() {
        setObservedPreferences(preferencesOf(PreventDuplicateTracksInQueueKey to true))
        nativePlayer.setMediaItems(listOf(item("current"), item("duplicate"), item("current")))

        service.addToQueue(listOf(item("current"), item("duplicate")))

        assertEquals(listOf("current", "current", "duplicate"), ids())
        assertEquals(0, nativePlayer.currentMediaItemIndex)
    }

    @Test
    fun `next command uses the updated observed policy without losing insertion order`() {
        setObservedPreferences(preferencesOf(PreventDuplicateTracksInQueueKey to false))
        nativePlayer.setMediaItems(listOf(item("current"), item("later"), item("duplicate")))
        setObservedPreferences(preferencesOf(PreventDuplicateTracksInQueueKey to true))

        service.playNext(listOf(item("duplicate"), item("second")))

        assertEquals(listOf("current", "duplicate", "second", "later"), ids())
    }

    private fun setObservedPreferences(preferences: Preferences) {
        // This is the existing service snapshot updated by its settings collector,
        // not a new preferences writer or a production lifecycle initialization.
        ReflectionHelpers.setField(service, "startupPrefs", preferences)
        observePreferences(preferences)
    }

    private fun observePreferences(preferences: Preferences) {
        storeField.set(null, ReadOnlyStore(flowOf(preferences)))
    }

    private fun assertInputResponds(command: () -> Unit) {
        val entered = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val sentinelRan = CountDownLatch(1)
        val released = CompletableDeferred<Preferences>()
        val reads = AtomicInteger()
        val respondedBeforeRelease = AtomicBoolean(false)
        val failure = AtomicReference<Throwable?>()
        storeField.set(null, ReadOnlyStore(flow {
            reads.incrementAndGet()
            emit(released.await())
        }))
        val main = Looper.getMainLooper()
        val observer = thread(name = "queue-preference-input") {
            try {
                if (entered.await(5, TimeUnit.SECONDS)) {
                    Handler(main).post { sentinelRan.countDown() }
                    respondedBeforeRelease.set(sentinelRan.await(2, TimeUnit.SECONDS))
                }
            } finally {
                released.complete(emptyPreferences())
            }
        }
        Handler(main).post {
            entered.countDown()
            try {
                command()
            } catch (error: Throwable) {
                failure.set(error)
            } finally {
                finished.countDown()
            }
        }
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6)
            while ((finished.count > 0 || observer.isAlive) && System.nanoTime() < deadline) {
                shadowOf(main).idle()
                Thread.yield()
            }
            assertTrue("Command completed", finished.count == 0L)
            observer.join(3_000)
            assertNull("Fixture player command must succeed", failure.get())
            assertTrue("Main input must run before held preference IO is released", respondedBeforeRelease.get())
            assertEquals("A seeded queue command must not re-read preferences", 0, reads.get())
        } finally {
            released.complete(emptyPreferences())
            observer.join(5_000)
            shadowOf(main).idle()
        }
    }

    private fun ids() = (0 until nativePlayer.mediaItemCount).map { nativePlayer.getMediaItemAt(it).mediaId }
    private fun item(id: String): MediaItem = SongItem(id, id, emptyList(), thumbnail = "").toMediaItem()
    private class ReadOnlyStore(override val data: Flow<Preferences>) : DataStore<Preferences> {
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw AssertionError("Queue commands must not write settings")
    }
}
