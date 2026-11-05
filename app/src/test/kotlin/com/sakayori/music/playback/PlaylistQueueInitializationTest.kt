/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.app.Application
import android.content.ContextWrapper
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.sakayori.innertube.models.SongItem
import com.sakayori.music.extensions.metadata
import com.sakayori.music.extensions.toMediaItem
import com.sakayori.music.models.MediaMetadata
import com.sakayori.music.playback.queues.Queue
import com.sakayori.music.playback.queues.ListQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaylistQueueInitializationTest {
    private val storeField = Class.forName("com.sakayori.music.utils.DataStoreKt")
        .getDeclaredField("dataStoreInstance").apply { isAccessible = true }
    private var previousStore: Any? = null
    private lateinit var service: MusicService
    private lateinit var nativePlayer: ExoPlayer
    private lateinit var player: ExoPlayer
    private val gates = mutableListOf<HeldQueue>()
    private val storeScopes = mutableListOf<CoroutineScope>()
    @get:Rule val files = TemporaryFolder()
    private var prepared = true
    private var playWhenReady = false
    private var preparationCount = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        previousStore = storeField.get(null)
        storeField.set(null, ReadOnlyStore(flowOf(emptyPreferences())))
        // No onCreate: the real queue methods are exercised without Hilt,
        // network, decoders, sessions, caches or production listener work.
        service = Robolectric.buildService(MusicService::class.java).get()
        nativePlayer = ExoPlayer.Builder(context).build()
        player = Proxy.newProxyInstance(ExoPlayer::class.java.classLoader, arrayOf(ExoPlayer::class.java)) { proxy, method, args ->
            when (method.name) {
                "equals" -> proxy === args?.get(0)
                "hashCode" -> System.identityHashCode(proxy)
                "toString" -> "PlaylistInitializationPlayer"
                "prepare" -> { prepared = true; preparationCount++; null }
                "getPlaybackState" -> if (prepared) Player.STATE_READY else Player.STATE_IDLE
                "setPlayWhenReady" -> { playWhenReady = args!![0] as Boolean; null }
                "getPlayWhenReady" -> playWhenReady
                "isPlaying" -> playWhenReady && prepared
                else -> try {
                    // Real Media3 timeline/index/Window identity; sources are
                    // never prepared, so this cannot perform network playback.
                    method.invoke(nativePlayer, *(args ?: emptyArray()))
                } catch (failure: InvocationTargetException) {
                    throw failure.targetException
                }
            }
        } as ExoPlayer
        ReflectionHelpers.setField(service, "player", player)
        service.playerVolume = MutableStateFlow(1f)
        ReflectionHelpers.getField<MutableStateFlow<Boolean>>(service, "playerInitialized").value = true
        ReflectionHelpers.setField(service, "startupPrefs", emptyPreferences())
        ReflectionHelpers.setField(service, "cachedPersistentQueue", false)
        nativePlayer.setMediaItems(listOf(item("incumbent")))
    }

    @After
    fun tearDown() {
        gates.forEach { it.release.complete(Unit) }
        ReflectionHelpers.getField<CoroutineScope>(service, "scope").cancel()
        shadowOf(Looper.getMainLooper()).idle()
        nativePlayer.release()
        storeScopes.forEach { it.cancel() }
        storeField.set(null, previousStore)
    }

    @Test
    fun `cleared recommendation cannot crash or append through a stale add callback`() {
        val selected = item("recommendation")
        service.automixItems.value = emptyList()

        service.addToQueueAutomix(selected, 0)

        assertEquals(listOf("incumbent"), ids())
        assertTrue(service.automixItems.value.isEmpty())
    }

    @Test
    fun `cleared recommendation cannot crash through a stale play next callback`() {
        service.playNextAutomix(item("recommendation"), 0)

        assertEquals(listOf("incumbent"), ids())
    }

    @Test
    fun `a valid position in a replacement pool cannot remove or append the old add item`() {
        val replacement = item("replacement")
        val remainder = item("remainder")
        service.automixItems.value = listOf(replacement, remainder)

        service.addToQueueAutomix(item("old"), 0)

        assertEquals(listOf(replacement, remainder), service.automixItems.value)
        assertEquals(listOf("incumbent"), ids())
    }

    @Test
    fun `a valid position in a replacement pool cannot remove the old play next item`() {
        val replacement = item("replacement")
        service.automixItems.value = listOf(replacement)

        service.playNextAutomix(item("old"), 0)

        assertEquals(listOf(replacement), service.automixItems.value)
        assertEquals(listOf("incumbent"), ids())
    }

    @Test
    fun `repeating an old callback cannot consume the next recommendation`() {
        val selected = item("selected")
        val remaining = item("remaining")
        service.automixItems.value = listOf(selected, remaining)

        service.addToQueueAutomix(selected, 0)
        service.addToQueueAutomix(selected, 0)

        assertEquals(listOf("incumbent", "selected"), ids())
        assertEquals(listOf(remaining), service.automixItems.value)
    }

    @Test
    fun `held playlist initialization completes without automatic promotion as control`() {
        val queue = startHeldQueue()
        finish(queue)

        assertEquals(listOf("before", "selected", "after"), ids())
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals("playlist", service.queueTitle)
        assertEquals(1, preparationCount)
    }

    @Test
    fun `automatic recommendation cannot displace the pending playlist remainder`() {
        val queue = startHeldQueue()
        val pool = listOf(item("recommendation"))
        service.automixItems.value = pool

        service.promoteAutomixIfNeeded(false, pool)
        finish(queue)

        assertEquals(listOf("before", "selected", "after"), ids())
        assertEquals(1, player.currentMediaItemIndex)
        assertEquals("playlist", service.queueTitle)
        assertEquals(pool, service.automixItems.value)
        assertTrue(playWhenReady)
        assertEquals(1, preparationCount)
    }

    @Test
    fun `automatic append must recheck live next even if its UI capability is stale`() {
        val queue = startHeldQueue()
        finish(queue)
        val pool = listOf(item("recommendation"))
        service.automixItems.value = pool

        service.promoteAutomixIfNeeded(false, pool)

        assertEquals(listOf("before", "selected", "after"), ids())
        assertEquals(pool, service.automixItems.value)
    }

    @Test
    fun `replaced recommendation snapshot cannot authorize an automatic append`() {
        val queue = startHeldQueue(startIndex = 2)
        finish(queue)
        val oldPool = listOf(item("old"))
        val currentPool = listOf(item("current"))
        service.automixItems.value = currentPool

        service.promoteAutomixIfNeeded(false, oldPool)

        assertEquals(listOf("before", "selected", "after"), ids())
        assertEquals(currentPool, service.automixItems.value)
    }

    @Test
    fun `new request defeats the old held playlist result`() {
        val first = startHeldQueue()
        val second = startHeldQueue(listOf(item("new-before"), item("new-selected"), item("new-after")))
        finish(second)
        first.release.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("new-before", "new-selected", "new-after"), ids())
        assertEquals("new-selected", player.currentMediaItem?.mediaId)
    }

    @Test
    fun `manual play next remains immediate while the remainder is pending`() {
        val queue = startHeldQueue()
        val chosen = item("manual")
        service.automixItems.value = listOf(chosen)

        service.playNextAutomix(chosen, 0)
        assertEquals(listOf("selected", "manual"), ids())
        finish(queue, allowCancellation = true)

        assertEquals(listOf("selected", "manual"), ids())
    }

    @Test
    fun `a complete local list retains all 75 and 106 occurrences`() {
        for (size in listOf(75, 106)) {
            val items = (0 until size).map { item("song-$it") }
            val queue = startHeldQueue(items, startIndex = 22)
            finish(queue)
            assertEquals(items.map { it.mediaId }, ids())
            assertEquals(22, player.currentMediaItemIndex)
        }
    }

    @Test
    fun `a committed final item still permits one legitimate automatic recommendation`() {
        val queue = startHeldQueue(startIndex = 2)
        finish(queue)
        val first = item("recommendation")
        val second = item("remaining")
        val pool = listOf(first, second)
        service.automixItems.value = pool

        service.promoteAutomixIfNeeded(false, pool)

        assertEquals(listOf("before", "selected", "after", "recommendation"), ids())
        assertEquals(listOf(second), service.automixItems.value)
        assertEquals(2, player.currentMediaItemIndex)
    }

    @Test
    fun `a cancelled initial request cannot grant automatic promotion authority`() {
        val queue = startHeldQueue()
        queue.job.cancel()
        pumpUntil { queue.job.isCompleted }
        val pool = listOf(item("recommendation"))
        service.automixItems.value = pool

        service.promoteAutomixIfNeeded(false, pool)

        assertEquals(listOf("selected"), ids())
        assertEquals(pool, service.automixItems.value)
    }

    @Test
    fun `same recording replaced with a new native window defeats an old initial load`() {
        val queue = startHeldQueue()
        nativePlayer.setMediaItem(item("selected"))
        finish(queue)

        assertEquals(listOf("selected"), ids())
        assertNull(service.queueTitle)
    }

    @Test
    fun `valid preload completion preserves a newer seek and paused state`() {
        val queue = startHeldQueue()
        nativePlayer.seekTo(1234)
        playWhenReady = false
        finish(queue)

        assertEquals(listOf("before", "selected", "after"), ids())
        assertEquals(1234L, player.currentPosition)
        assertFalse(playWhenReady)
        assertEquals(1, preparationCount)
    }

    @Test
    fun `a callback from a cancelled service scope cannot consume recommendations`() {
        val recommendation = item("recommendation")
        val pool = listOf(recommendation)
        service.automixItems.value = pool
        ReflectionHelpers.getField<CoroutineScope>(service, "scope").cancel()

        service.addToQueueAutomix(recommendation, 0)

        assertEquals(pool, service.automixItems.value)
        assertEquals(listOf("incumbent"), ids())
    }

    @Test
    fun `a full initial commit submits the complete persisted native queue`() = runBlocking {
        val directory = files.newFolder("queue")
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Application>()) {
            override fun getFilesDir() = directory
        }
        val storeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        storeScopes += storeScope
        val store = PlaybackQueueStore(context, storeScope)
        ReflectionHelpers.setField(service, "queueStore", store)
        ReflectionHelpers.setField(service, "cachedPersistentQueue", true)
        service.playQueue(ListQueue("persisted", listOf(item("a"), item("b"), item("c")), 1))
        val job = ReflectionHelpers.getField<Job>(service, "queueLoadJob")
        pumpUntil { job.isCompleted }
        store.awaitPendingWrites()

        val snapshot = store.load()
        assertEquals(listOf("a", "b", "c"), snapshot.queue?.items?.map { it.id })
        assertEquals(1, snapshot.queue?.mediaItemIndex)
        assertEquals("persisted", snapshot.queue?.title)
    }

    @Test
    fun `a newer listener admitted queue wins over older add bookkeeping`() {
        assertReentrantQueueWins { service.addToQueue(listOf(item("old-manual"))) }
    }

    @Test
    fun `a newer listener admitted queue wins over older play next bookkeeping`() {
        assertReentrantQueueWins { service.playNext(listOf(item("old-manual"))) }
    }

    @Test
    fun `older recommendation consumption cannot remove a carried pool after a newer admission`() {
        val recommendation = item("old-manual")
        val pool = listOf(recommendation)
        service.automixItems.value = pool
        assertReentrantQueueWins { service.addToQueueAutomix(recommendation, 0) }

        assertEquals(pool, service.automixItems.value)
    }

    private fun assertReentrantQueueWins(command: () -> Unit) {
        var admitted = false
        nativePlayer.addListener(object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (!admitted && nativePlayer.mediaItemCount == 2) {
                    admitted = true
                    service.playQueue(ListQueue("newer", listOf(item("new-a"), item("new-b")), 0))
                }
            }
        })
        command()
        val latest = ReflectionHelpers.getField<Job>(service, "queueLoadJob")
        pumpUntil { latest.isCompleted }

        assertTrue("The actual native callback must admit the newer request", admitted)
        assertEquals(listOf("new-a", "new-b"), ids())
        assertEquals("newer", service.queueTitle)
    }

    private fun startHeldQueue(
        items: List<MediaItem> = listOf(item("before"), item("selected"), item("after")),
        startIndex: Int = 1,
    ): HeldQueue {
        val queue = HeldQueue(items, startIndex)
        gates += queue
        service.playQueue(queue)
        queue.job = ReflectionHelpers.getField(service, "queueLoadJob")
        queue.job.invokeOnCompletion { queue.failure.set(it) }
        pumpUntil { queue.entered.count == 0L }
        assertEquals(listOf(items[startIndex].mediaId), ids())
        assertFalse(queue.job.isCompleted)
        return queue
    }

    private fun finish(queue: HeldQueue, allowCancellation: Boolean = false) {
        queue.release.complete(Unit)
        pumpUntil { queue.job.isCompleted }
        val failure = queue.failure.get()
        if (!allowCancellation || failure !is CancellationException) {
            assertNull("Fixture must not mistake asynchronous exceptions for queue truncation", failure)
        }
    }

    private fun pumpUntil(done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!done() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        assertTrue("Main continuation completed", done())
    }

    private fun ids() = (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId }
    private fun item(id: String) = SongItem(id, id, emptyList(), thumbnail = "").toMediaItem()

    private class HeldQueue(private val items: List<MediaItem>, private val startIndex: Int) : Queue {
        val entered = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        val failure = AtomicReference<Throwable?>()
        lateinit var job: Job
        override val preloadItem: MediaMetadata? = items[startIndex].metadata
        override suspend fun getInitialStatus(): Queue.Status {
            entered.countDown()
            release.await()
            return Queue.Status("playlist", items, startIndex, 0)
        }
        override fun hasNextPage() = false
        override suspend fun nextPage(): List<MediaItem> = throw AssertionError("No network/paging in fixture")
    }

    private class ReadOnlyStore(override val data: Flow<Preferences>) : DataStore<Preferences> {
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw AssertionError("Queue command fixture must not write preferences")
    }
}
