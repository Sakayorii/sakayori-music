/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.viewmodels

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.preferencesOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sakayori.music.db.InternalDatabase
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.db.entities.ArtistEntity
import com.sakayori.music.constants.HideExplicitKey
import com.sakayori.music.constants.HideYoutubeShortsKey
import com.sakayori.music.utils.SyncUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ArtistViewModelPreferenceTest {
    private val storeField = Class.forName("com.sakayori.music.utils.DataStoreKt")
        .getDeclaredField("dataStoreInstance").apply { isAccessible = true }
    private var previousStore: Any? = null
    private lateinit var database: MusicDatabase
    private lateinit var viewModel: ArtistViewModel

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        previousStore = storeField.get(null)
        storeField.set(null, TestStore(MutableStateFlow(emptyPreferences())))
        database = MusicDatabase(
            Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build(),
        )
        val sync = SyncUtils(context, database)
        val syncJob = ReflectionHelpers.getField<Job>(sync, "syncJob")
        syncJob.cancel()
        runBlocking { syncJob.join() }

        // Prevent automatic refresh/network work; exercise each traced entry
        // point explicitly against the real ViewModel and Room cache below.
        storeField.set(null, TestStore(flow { awaitCancellation() }))
        viewModel = ArtistViewModel(context, database, sync, SavedStateHandle(mapOf("artistId" to "artist")))
        val initialJobs = viewModel.viewModelScope.coroutineContext[Job]!!.children.toList()
        viewModel.viewModelScope.coroutineContext.cancelChildren()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (initialJobs.any { !it.isCompleted } && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        assertTrue("Initial work must finish cancellation before replacing its store", initialJobs.all { it.isCompleted })
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.cancel()
        shadowOf(Looper.getMainLooper()).idle()
        storeField.set(null, previousStore)
        database.close()
    }

    @Test
    fun `manual artist refresh leaves Main available while preferences are pending`() {
        assertInputRunsBeforePreferencesComplete {
            viewModel.fetchArtistsFromYTM()
        }
    }

    @Test
    fun `cached artist page leaves Main available while preferences are pending`() {
        database.insert(ArtistEntity(id = "artist", name = "Artist", cachedPageJson = """{"sections":[]}"""))

        assertInputRunsBeforePreferencesComplete {
            viewModel.viewModelScope.launch { loadCachedPage() }
        }
    }

    @Test
    fun `absent preferences preserve cached sections and subscription`() {
        seedCachedPage()
        storeField.set(null, TestStore(flowOf(emptyPreferences())))
        completeCachedLoad()

        assertEquals(listOf("clean", "explicit", "SS-short"), viewModel.artistPage!!.sections.flatMap { it.items }.map { it.id })
        assertTrue(viewModel.artistPage!!.isSubscribed)
    }

    @Test
    fun `one loaded snapshot preserves explicit and shorts filtering`() {
        seedCachedPage()
        storeField.set(null, TestStore(flowOf(preferencesOf(HideExplicitKey to true, HideYoutubeShortsKey to true))))
        completeCachedLoad()

        assertEquals(listOf("clean"), viewModel.artistPage!!.sections.flatMap { it.items }.map { it.id })
    }

    @Test
    fun `cancelling pending cached preferences cannot publish a late page`() {
        seedCachedPage()
        val pending = CompletableDeferred<Preferences>()
        val started = CountDownLatch(1)
        storeField.set(null, TestStore(flow { started.countDown(); emit(pending.await()) }))
        val job = viewModel.viewModelScope.launch { loadCachedPage() }
        pumpUntil { started.count == 0L }
        job.cancel()
        pending.complete(emptyPreferences())
        pumpUntil { job.isCompleted }

        assertNull(viewModel.artistPage)
    }

    private fun seedCachedPage() {
        database.insert(ArtistEntity(id = "artist", name = "Artist", cachedPageJson = """
            {"isSubscribed":true,"sections":[{"title":"Songs","items":[
                {"type":"song","id":"clean","title":"Clean","thumbnail":""},
                {"type":"song","id":"explicit","title":"Explicit","thumbnail":"","explicit":true},
                {"type":"playlist","id":"SS-short","title":"Shorts","thumbnail":""}
            ]}]}
        """.trimIndent()))
    }

    private fun completeCachedLoad() {
        val job = viewModel.viewModelScope.launch { loadCachedPage() }
        pumpUntil { job.isCompleted }
    }

    private fun pumpUntil(done: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!done() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
        assertTrue("Main continuation completed", done())
    }

    private suspend fun loadCachedPage(): Unit = suspendCoroutineUninterceptedOrReturn { continuation ->
        val method = ArtistViewModel::class.java.getDeclaredMethod("loadCachedPage", Continuation::class.java)
        method.isAccessible = true
        try {
            method.invoke(viewModel, continuation)
        } catch (wrapped: InvocationTargetException) {
            throw wrapped.targetException
        }
    }

    private fun assertInputRunsBeforePreferencesComplete(start: () -> Unit) {
        val readStarted = CountDownLatch(1)
        val inputPosted = CountDownLatch(1)
        val inputRan = CountDownLatch(1)
        val releaseRead = CompletableDeferred<Unit>()
        val respondedWhileReadPending = AtomicBoolean(false)
        storeField.set(null, TestStore(flow {
            readStarted.countDown()
            releaseRead.await()
            // Stop at the preference boundary so the reproduction performs no
            // YouTube request even on the old blocking implementation.
            throw CancellationException("End of controlled preference read")
        }))
        val main = Looper.getMainLooper()
        val observer = thread(name = "preference-input-observer") {
            try {
                if (readStarted.await(5, TimeUnit.SECONDS)) {
                    Handler(main).post { inputRan.countDown() }
                    inputPosted.countDown()
                    respondedWhileReadPending.set(inputRan.await(2, TimeUnit.SECONDS))
                }
            } finally {
                releaseRead.complete(Unit)
            }
        }
        try {
            start()
            // Room/deserialization can finish on IO. Pump its real Main
            // continuation; the old runBlocking call holds this pump until the
            // observer releases the read after the input deadline.
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (readStarted.count != 0L && System.nanoTime() < deadline) {
                shadowOf(main).idle()
                Thread.yield()
            }
            assertTrue("The traced preference read was reached", readStarted.count == 0L)
            assertTrue("Input was posted behind the pending read", inputPosted.await(5, TimeUnit.SECONDS))
            shadowOf(main).idle()
            observer.join(5_000)
            assertTrue("Main input must run before preference IO is released", respondedWhileReadPending.get())
        } finally {
            releaseRead.complete(Unit)
            observer.join(8_000)
            shadowOf(main).idle()
        }
    }

    private class TestStore(override val data: Flow<Preferences>) : DataStore<Preferences> {
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
            throw AssertionError("Preference reads must not write settings")
    }
}
