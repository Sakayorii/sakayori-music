/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.content.Context
import android.util.AtomicFile
import com.sakayori.music.di.ApplicationScope
import com.sakayori.music.models.PersistPlayerState
import com.sakayori.music.models.PersistQueue
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

internal data class PlaybackQueueSnapshot(
    val queue: PersistQueue?,
    val automix: PersistQueue?,
    val player: PersistPlayerState?,
)

/**
 * One application-owned writer survives a MusicService handoff. Submission only retains the
 * newest immutable snapshot; serialization, fsync and reads are confined to IO. Existing file
 * names and serialized classes remain readable by the existing backup/restore contract.
 */
@Singleton
class PlaybackQueueStore @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
) {
    private data class Operation(
        val revision: Long,
        val snapshot: PlaybackQueueSnapshot?,
        val completion: CompletableDeferred<Unit>? = null,
    )
    private data class Completion(val revision: Long, val failure: Exception? = null)
    private val directory by lazy { context.filesDir }
    private val lock = Any()
    private val io = Mutex()
    private var revision = 0L
    private val pending = ArrayDeque<Operation>()
    private var writing: Operation? = null
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val completed = MutableStateFlow(Completion(0L))
    @Volatile var enabled = true

    init {
        applicationScope.launch(Dispatchers.IO) {
            for (ignored in wakeups) {
                while (true) {
                    val operation = synchronized(lock) {
                        pending.removeFirstOrNull()?.also { writing = it }
                    } ?: break
                    var failure: Exception? = null
                    try {
                        io.withLock {
                            val snapshot = operation.snapshot
                            if (snapshot == null) {
                                FILES.forEach { name ->
                                    val file = AtomicFile(directory.resolve(name))
                                    file.delete()
                                    if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
                                        throw IOException("Unable to clear playback queue")
                                    }
                                }
                            } else if (enabled) {
                                snapshot.queue?.let { write(QUEUE_FILE, it) }
                                snapshot.automix?.let { write(AUTOMIX_FILE, it) }
                                snapshot.player?.let { write(PLAYER_STATE_FILE, it) }
                            }
                        }
                    } catch (cancelled: CancellationException) {
                        failure = cancelled
                        throw cancelled
                    } catch (error: Exception) {
                        failure = error
                        Timber.tag(TAG).e(error, "Unable to persist playback queue")
                    } finally {
                        synchronized(lock) {
                            writing = null
                            completed.value = Completion(operation.revision, failure)
                        }
                        if (failure == null) operation.completion?.complete(Unit)
                        else operation.completion?.completeExceptionally(failure)
                    }
                }
            }
        }
    }

    internal fun save(snapshot: PlaybackQueueSnapshot) {
        if (enabled) enqueue(snapshot)
    }

    /** Serialize clearing behind any final service snapshot, so it cannot recreate cleared files. */
    suspend fun clear() {
        val completion = CompletableDeferred<Unit>()
        enqueue(null, completion)
        completion.await()
    }

    internal suspend fun awaitPendingWrites() {
        val target = synchronized(lock) { revision }
        completed.first { it.revision >= target }.failure?.let { throw it }
    }

    internal suspend fun load(): PlaybackQueueSnapshot = withContext(Dispatchers.IO) {
        io.withLock {
            synchronized(lock) { pending.lastOrNull() ?: writing }?.let {
                return@withLock it.snapshot ?: PlaybackQueueSnapshot(null, null, null)
            }
            PlaybackQueueSnapshot(
                read(QUEUE_FILE, PersistQueue::class.java),
                read(AUTOMIX_FILE, PersistQueue::class.java),
                read(PLAYER_STATE_FILE, PersistPlayerState::class.java),
            )
        }
    }

    private fun enqueue(snapshot: PlaybackQueueSnapshot?, completion: CompletableDeferred<Unit>? = null): Long = synchronized(lock) {
        val next = ++revision
        // Only adjacent saves coalesce. A clear remains ordered and has its own completion,
        // so a subsequent save cannot impersonate a clear that was never executed.
        if (snapshot != null && pending.lastOrNull()?.snapshot != null) pending.removeLast()
        pending.addLast(Operation(next, snapshot, completion))
        wakeups.trySend(Unit)
        next
    }

    private fun <T> read(name: String, type: Class<T>): T? {
        val file = AtomicFile(directory.resolve(name))
        if (!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) return null
        return try {
            file.openRead().use { input -> ObjectInputStream(input).use { type.cast(it.readObject()) } }
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Unable to restore $name")
            file.delete()
            null
        }
    }

    private fun write(name: String, value: Any) {
        val file = AtomicFile(directory.resolve(name))
        val output = file.startWrite()
        try {
            val objects = ObjectOutputStream(output)
            objects.writeObject(value)
            objects.flush()
            // AtomicFile owns closing/fsync of the underlying stream.
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    companion object {
        const val QUEUE_FILE = "persistent_queue.data"
        const val AUTOMIX_FILE = "persistent_automix.data"
        const val PLAYER_STATE_FILE = "persistent_player_state.data"
        private val FILES = listOf(QUEUE_FILE, AUTOMIX_FILE, PLAYER_STATE_FILE)
        private const val TAG = "PlaybackQueueStore"
    }
}
