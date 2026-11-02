package com.sakayori.innertube.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Shares successful artist lookups across sections without a background scope or player work.
 * Waiting callers retain their own cancellation; a cancelled lookup owner releases the gate so
 * another live caller can retry. Network concurrency is bounded independently of the result cache.
 */
internal class ArtistIdLookupCache(
    private val currentRevision: () -> Long,
    private val maxEntries: Int = 128,
    maxConcurrentLookups: Int = 8,
) {
    private data class Key(val revision: Long, val name: String)
    private class Gate(val mutex: Mutex = Mutex(), var users: Int = 0)

    private val lock = Any()
    private val permits = Semaphore(maxConcurrentLookups)
    private val results = LinkedHashMap<Key, String>(0, 0.75f, true)
    private val gates = mutableMapOf<Key, Gate>()

    init {
        require(maxEntries > 0)
        require(maxConcurrentLookups > 0)
    }

    fun clear() = synchronized(lock) { results.clear() }

    suspend fun resolve(
        revision: Long,
        name: String,
        lookup: suspend (String) -> Result<String?>,
    ): String? {
        val key = Key(revision, name.trim())
        if (key.name.isEmpty()) return null
        currentCoroutineContext().ensureActive()
        checkRevision(revision)
        synchronized(lock) { results[key] }?.let { return it }
        val gate = synchronized(lock) { gates.getOrPut(key) { Gate() }.also { it.users++ } }
        try {
            return gate.mutex.withLock {
                currentCoroutineContext().ensureActive()
                checkRevision(revision)
                synchronized(lock) { results[key] }?.let { return@withLock it }
                permits.withPermit {
                    checkRevision(revision)
                    val result = lookup(key.name)
                    currentCoroutineContext().ensureActive()
                    checkRevision(revision)
                    result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
                    // A failure or an unresolved name is retriable; do not cache either as an ID.
                    val id = result.getOrNull()?.takeIf { it.isNotBlank() }
                    if (id != null) synchronized(lock) {
                        if (currentRevision() == revision) {
                            results[key] = id
                            while (results.size > maxEntries) {
                                results.entries.iterator().apply { next(); remove() }
                            }
                        }
                    }
                    id
                }
            }
        } finally {
            synchronized(lock) {
                gate.users--
                if (gate.users == 0 && gates[key] === gate) gates.remove(key)
            }
        }
    }

    private fun checkRevision(revision: Long) {
        if (revision != currentRevision()) throw CancellationException("YouTube request context changed")
    }
}
