package com.sakayori.innertube.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ArtistIdLookupCacheTest {
    @Test
    fun `concurrent identical names share the successful lookup`() = runBlocking {
        val cache = ArtistIdLookupCache(currentRevision = { 0L })
        val response = CompletableDeferred<String?>()
        var calls = 0
        val lookup: suspend (String) -> Result<String?> = {
            calls++
            Result.success(response.await())
        }
        val first = async(start = CoroutineStart.UNDISPATCHED) { cache.resolve(0L, " Artist ", lookup) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { cache.resolve(0L, "Artist", lookup) }
        assertEquals(1, calls)
        response.complete("UC-artist")
        assertEquals(listOf("UC-artist", "UC-artist"), awaitAll(first, second))
        assertEquals(1, calls)
    }

    @Test
    fun `cancelling a waiting caller does not cancel the active lookup`() = runBlocking {
        val cache = ArtistIdLookupCache(currentRevision = { 0L })
        val response = CompletableDeferred<String?>()
        var calls = 0
        val lookup: suspend (String) -> Result<String?> = {
            calls++
            Result.success(response.await())
        }
        val owner = async(start = CoroutineStart.UNDISPATCHED) { cache.resolve(0L, "Artist", lookup) }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { cache.resolve(0L, "Artist", lookup) }
        waiter.cancelAndJoin()
        response.complete("UC-artist")
        assertEquals("UC-artist", owner.await())
        assertEquals("UC-artist", cache.resolve(0L, "Artist", lookup))
        assertEquals(1, calls)
    }

    @Test
    fun `cancelled owner releases the name gate and network permit`() = runBlocking {
        val cache = ArtistIdLookupCache(currentRevision = { 0L }, maxConcurrentLookups = 1)
        var calls = 0
        val lookup: suspend (String) -> Result<String?> = {
            calls++
            if (calls == 1) awaitCancellation()
            Result.success("UC-retry")
        }
        val owner = async(start = CoroutineStart.UNDISPATCHED) { cache.resolve(0L, "Artist", lookup) }
        val waiter = async(start = CoroutineStart.UNDISPATCHED) { cache.resolve(0L, "Artist", lookup) }
        owner.cancelAndJoin()
        assertEquals("UC-retry", waiter.await())
        assertEquals(2, calls)
    }

    @Test
    fun `account change rejects an old response and does not cache it`() = runBlocking {
        var revision = 0L
        val cache = ArtistIdLookupCache(currentRevision = { revision })
        val response = CompletableDeferred<String?>()
        val old = async(start = CoroutineStart.UNDISPATCHED) {
            cache.resolve(0L, "Artist") { Result.success(response.await()) }
        }
        revision = 1L
        cache.clear()
        response.complete("UC-old")
        assertTrue(runCatching { old.await() }.exceptionOrNull() is CancellationException)
        assertEquals("UC-new", cache.resolve(1L, "Artist") { Result.success("UC-new") })
    }

    @Test
    fun `different names obey the shared concurrency limit`() = runBlocking {
        val cache = ArtistIdLookupCache(currentRevision = { 0L }, maxConcurrentLookups = 2)
        val release = CompletableDeferred<Unit>()
        var active = 0
        var peak = 0
        val work = (1..10).map { index ->
            async(start = CoroutineStart.UNDISPATCHED) {
                cache.resolve(0L, "Artist $index") { name ->
                    active++
                    peak = maxOf(peak, active)
                    try {
                        release.await()
                        Result.success("UC-$name")
                    } finally {
                        active--
                    }
                }
            }
        }
        assertEquals(2, active)
        release.complete(Unit)
        assertEquals(10, work.awaitAll().size)
        assertEquals(2, peak)
        assertEquals(0, active)
    }

    @Test
    fun `failed lookup is retried and completed cache is bounded by LRU`() = runBlocking {
        val cache = ArtistIdLookupCache(currentRevision = { 0L }, maxEntries = 2)
        assertEquals(null, cache.resolve(0L, "A") { Result.failure(IOException("offline")) })
        var calls = 0
        val lookup: suspend (String) -> Result<String?> = { calls++; Result.success("UC-$it") }
        cache.resolve(0L, "A", lookup)
        cache.resolve(0L, "B", lookup)
        cache.resolve(0L, "A", lookup)
        cache.resolve(0L, "C", lookup)
        cache.resolve(0L, "B", lookup)
        assertEquals(4, calls)
    }
}
