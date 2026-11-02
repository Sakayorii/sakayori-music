package com.sakayori.music.playback

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.sakayori.music.models.MediaMetadata
import com.sakayori.music.models.PersistPlayerState
import com.sakayori.music.models.PersistQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PlaybackQueueStoreTest {
    private lateinit var context: Context
    private lateinit var scope: CoroutineScope
    private lateinit var store: PlaybackQueueStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = PlaybackQueueStore(context, scope)
    }

    @After
    fun tearDown() { scope.cancel() }

    private fun snapshot(position: Long): PlaybackQueueSnapshot {
        val queue = PersistQueue("queue", listOf(MediaMetadata("song", "A Track", emptyList(), 120)), 0, position)
        return PlaybackQueueSnapshot(queue, PersistQueue("automix", emptyList(), 0, 0),
            PersistPlayerState(false, 0, false, 0.8f, position, 0, 3))
    }

    @Test
    fun `new saves retain the existing serialized file contract`() = runBlocking {
        val expected = snapshot(10_000)
        store.save(expected)
        withTimeout(5_000) { store.awaitPendingWrites() }
        val restored = context.filesDir.resolve(PlaybackQueueStore.QUEUE_FILE).inputStream().use {
            ObjectInputStream(it).use { objects -> objects.readObject() as PersistQueue }
        }
        assertEquals(expected.queue?.items, restored.items)
        assertEquals(10_000L, restored.position)
    }

    @Test
    fun `rapid snapshots leave the newest position on disk`() = runBlocking {
        repeat(20) { store.save(snapshot(it.toLong())) }
        withTimeout(5_000) { store.awaitPendingWrites() }
        assertEquals(19L, store.load().queue?.position)
    }

    @Test
    fun `clearing waits behind a final pending service save`() = runBlocking {
        store.save(snapshot(10_000))
        withTimeout(5_000) { store.clear() }
        val restored = store.load()
        assertNull(restored.queue)
        assertNull(restored.automix)
        assertNull(restored.player)
    }

    @Test
    fun `a damaged auxiliary file does not discard a valid legacy queue`() = runBlocking {
        val expected = snapshot(12_000)
        context.filesDir.resolve(PlaybackQueueStore.QUEUE_FILE).outputStream().use {
            ObjectOutputStream(it).use { objects -> objects.writeObject(expected.queue) }
        }
        context.filesDir.resolve(PlaybackQueueStore.AUTOMIX_FILE).writeText("broken fixture")
        val restored = store.load()
        assertEquals(12_000L, restored.queue?.position)
        assertNull(restored.automix)
    }
}
