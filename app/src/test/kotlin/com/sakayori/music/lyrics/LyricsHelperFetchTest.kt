/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.lyrics

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.test.core.app.ApplicationProvider
import com.sakayori.music.constants.LyricsProviderOrderKey
import com.sakayori.music.constants.EnableBetterLyricsKey
import com.sakayori.music.constants.EnableLrcLibKey
import com.sakayori.music.models.MediaMetadata
import com.sakayori.music.utils.NetworkConnectivityObserver
import com.sakayori.music.utils.dataStore
import com.sakayori.music.utils.get
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
/** Real helper with controlled providers; no external network timing dependency. */
class LyricsHelperFetchTest {
    private val storeField = Class.forName("com.sakayori.music.utils.DataStoreKt")
        .getDeclaredField("dataStoreInstance").apply { isAccessible = true }
    private val providersField = LyricsProviderRegistry::class.java
        .getDeclaredField("providerMap").apply { isAccessible = true }
    private var oldStore: Any? = null
    private var oldProviders = emptyMap<String, LyricsProvider>()
    private lateinit var helper: LyricsHelper
    private lateinit var observer: NetworkConnectivityObserver
    private val release = CompletableDeferred<Unit>()
    private val calls = AtomicInteger()
    private val fallbackCalls = AtomicInteger()
    private val preferenceReads = AtomicInteger()
    private var preferredFails = false

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(manager).setNetworkCapabilities(manager.activeNetwork, capabilities)
        observer = NetworkConnectivityObserver(context)
        assertTrue(observer.isCurrentlyConnected())
        oldStore = storeField.get(null)
        storeField.set(null, object : DataStore<Preferences> {
            override val data = flow {
                preferenceReads.incrementAndGet()
                emit(preferencesOf(LyricsProviderOrderKey to "BetterLyrics,LrcLib"))
            }
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
                throw AssertionError("Read-only preference fixture")
        })
        val providers = providersField.get(null) as MutableMap<*, *>
        oldProviders = providers.entries.associate { (it.key as String) to (it.value as LyricsProvider) }
        providers.clear()
        putProviders(providers, mapOf("BetterLyrics" to provider("Preferred", calls) {
            release.await()
            if (preferredFails) Result.failure(IllegalStateException("provider failed"))
            else Result.success("[00:01.00]Preferred lyrics")
        }, "LrcLib" to provider("Fallback", fallbackCalls) { Result.success("[00:01.00]Fallback lyrics") }))
        helper = LyricsHelper(context, observer)
    }

    @After
    fun tearDown() {
        release.complete(Unit)
        observer.unregister()
        storeField.set(null, oldStore)
        val providers = providersField.get(null) as MutableMap<*, *>
        providers.clear()
        putProviders(providers, oldProviders)
    }

    @Test
    fun `concurrent service and player lookups share one request for the same song`() = runBlocking {
        val first = async(start = CoroutineStart.UNDISPATCHED) { helper.getLyrics(song("a")) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { helper.getLyrics(song("a")) }
        release.complete(Unit)
        val results = awaitAll(first, second)

        assertEquals(1, calls.get())
        assertEquals(listOf("Preferred", "Preferred"), results.map { it.provider })
    }

    @Test
    fun `same-song followers reuse the full ordered fallback instead of starting it again`() = runBlocking {
        preferredFails = true
        val first = async(start = CoroutineStart.UNDISPATCHED) { helper.getLyrics(song("a")) }
        val second = async(start = CoroutineStart.UNDISPATCHED) { helper.getLyrics(song("a")) }
        release.complete(Unit)
        val results = awaitAll(first, second)

        assertEquals(1, calls.get())
        assertEquals(1, fallbackCalls.get())
        assertEquals(listOf("Fallback", "Fallback"), results.map { it.provider })
    }

    @Test
    fun `next-song prefetch cannot serialize a different current-song lookup`() = runBlocking {
        val next = async(start = CoroutineStart.UNDISPATCHED) { helper.getLyrics(song("next")) }
        val current = async(start = CoroutineStart.UNDISPATCHED) { helper.getLyrics(song("current")) }
        assertEquals(2, calls.get())
        release.complete(Unit)
        awaitAll(next, current)
        Unit
    }

    @Test
    fun `cancelled follower does not cancel the surviving request`() = runBlocking {
        val first = async(start = CoroutineStart.UNDISPATCHED) { helper.getLyrics(song("a")) }
        val follower = async(start = CoroutineStart.UNDISPATCHED) { helper.getLyrics(song("a")) }
        follower.cancelAndJoin()
        release.complete(Unit)
        assertEquals("Preferred", first.await().provider)
        assertEquals(1, calls.get())
    }

    @Test
    fun `a cancelled leader releases the key so the remaining caller can fetch`() = runBlocking {
        val first = async(start = CoroutineStart.UNDISPATCHED) { helper.getLyrics(song("a")) }
        val follower = async(start = CoroutineStart.UNDISPATCHED) { helper.getLyrics(song("a")) }
        first.cancelAndJoin()
        release.complete(Unit)
        assertEquals("Preferred", follower.await().provider)
        assertEquals(2, calls.get())
    }

    @Test
    fun `successful cached lyrics return without another source request`() = runBlocking {
        release.complete(Unit)
        val first = helper.getLyrics(song("a"))
        val second = helper.getLyrics(song("a"))
        assertEquals(first, second)
        assertEquals(1, calls.get())
        assertEquals(0, fallbackCalls.get())
    }

    @Test
    fun `source admission uses one consistent preference snapshot without per-provider blocking reads`() = runBlocking {
        release.complete(Unit)
        helper.getLyrics(song("a"))
        assertEquals(1, preferenceReads.get())
    }

    private fun song(id: String) = MediaMetadata(id, "Song", listOf(MediaMetadata.Artist(null, "Artist")), 180)

    private fun putProviders(target: MutableMap<*, *>, providers: Map<String, LyricsProvider>) {
        val put = target.javaClass.getMethod("put", Any::class.java, Any::class.java)
        providers.forEach { (key, value) -> put.invoke(target, key, value) }
    }

    private fun provider(
        label: String,
        counter: AtomicInteger,
        fetch: suspend () -> Result<String>,
    ) = object : LyricsProvider {
        override val name = label
        override fun isEnabled(context: Context) = context.dataStore[
            if (label == "Preferred") EnableBetterLyricsKey else EnableLrcLibKey
        ] ?: true
        override suspend fun getLyrics(context: Context, id: String, title: String, artist: String,
            duration: Int, album: String?): Result<String> {
            counter.incrementAndGet()
            return fetch()
        }
    }
}
