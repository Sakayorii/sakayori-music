/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.app.Application
import android.app.Service
import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sakayori.music.db.InternalDatabase
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.playback.flac.ArchiveFlacSource
import com.sakayori.music.playback.flac.LosslessApiHttp
import com.sakayori.music.playback.flac.LosslessSourceRegistry
import com.sakayori.music.widget.MusicWidgetReceiver
import com.sakayori.music.widget.TurntableWidgetReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MusicServiceRunningStateTest {
    private lateinit var application: Application
    private lateinit var database: MusicDatabase
    private lateinit var provider: StandaloneDatabaseProvider
    private lateinit var resolver: LosslessResolver
    private val controllers = mutableListOf<ServiceController<MusicService>>()

    @Before
    fun setUp() {
        application = ApplicationProvider.getApplicationContext()
        database = MusicDatabase(Room.inMemoryDatabaseBuilder(application, InternalDatabase::class.java).build())
        provider = StandaloneDatabaseProvider(application)
        val unusedCache = Proxy.newProxyInstance(Cache::class.java.classLoader, arrayOf(Cache::class.java)) { _, method, _ ->
            throw AssertionError("Lifecycle flag test must not access cache: ${method.name}")
        } as Cache
        val store = AudioRenditionStore(database, provider, unusedCache, unusedCache)
        // The early-foreground-denial lifecycle does not need background source work.
        val cancelledScope = CoroutineScope(SupervisorJob().apply { cancel() } + Dispatchers.IO)
        val http = PlaybackHttpClient()
        val sources = LosslessSourceRegistry(application, cancelledScope, LosslessApiHttp(http), ArchiveFlacSource(http))
        resolver = LosslessResolver(database, store, sources, cancelledScope)
    }

    @After
    fun tearDown() {
        controllers.forEach { it.destroy() }
        controllers.clear()
        provider.close()
        database.close()
    }

    private fun createService(): MusicService {
        val controller = Robolectric.buildService(MusicService::class.java)
        val service = controller.get()
        // Supply just the collaborator used by early teardown. Hilt's generated
        // injected guard is a fixture detail; the real lifecycle methods run below.
        ReflectionHelpers.setField(service, "injected", true)
        service.losslessResolver = resolver
        shadowOf(service).setThrowInStartForeground(IllegalStateException("Foreground denied by test"))
        controllers.add(controller)
        controller.create()
        assertTrue(shadowOf(service).isStoppedBySelf)
        return service
    }

    @Test
    fun `creation publishes running state until destruction`() {
        assertFalse(MusicService.isRunning)
        createService()

        assertTrue(MusicService.isRunning)
        controllers.removeAt(0).destroy()
        assertFalse(MusicService.isRunning)
    }

    @Test
    fun `widget update reaches the existing service after creation`() {
        createService()

        MusicWidgetReceiver().onUpdate(application, AppWidgetManager.getInstance(application), intArrayOf(1))

        assertEquals(MusicWidgetReceiver.ACTION_UPDATE_WIDGET, shadowOf(application).nextStartedService?.action)
    }

    @Test
    fun `widget resize and turntable refresh use the same lifecycle state`() {
        val manager = AppWidgetManager.getInstance(application)
        MusicWidgetReceiver().onAppWidgetOptionsChanged(application, manager, 1, Bundle())
        TurntableWidgetReceiver().onUpdate(application, manager, intArrayOf(2))
        assertNull(shadowOf(application).nextStartedService)

        createService()
        MusicWidgetReceiver().onAppWidgetOptionsChanged(application, manager, 1, Bundle())
        TurntableWidgetReceiver().onUpdate(application, manager, intArrayOf(2))
        assertEquals(MusicWidgetReceiver.ACTION_UPDATE_WIDGET, shadowOf(application).nextStartedService?.action)
        assertEquals(TurntableWidgetReceiver.ACTION_UPDATE_TURNTABLE_WIDGET, shadowOf(application).nextStartedService?.action)

        controllers.removeAt(0).destroy()
        MusicWidgetReceiver().onAppWidgetOptionsChanged(application, manager, 1, Bundle())
        TurntableWidgetReceiver().onUpdate(application, manager, intArrayOf(2))
        assertNull(shadowOf(application).nextStartedService)
    }

    @Test
    fun `destroy and recreate publishes each new lifecycle`() {
        repeat(3) {
            assertFalse(MusicService.isRunning)
            createService()
            assertTrue(MusicService.isRunning)
            controllers.removeAt(0).destroy()
            assertFalse(MusicService.isRunning)
        }
    }

    @Test
    fun `constructing an unstarted instance does not reset the current service`() {
        createService()
        Robolectric.buildService(MusicService::class.java).get()

        assertTrue(MusicService.isRunning)
    }

    @Test
    fun `an old instances teardown cannot clear a newer owners running state`() {
        createService()
        createService()
        controllers.removeAt(0).destroy()

        assertTrue(MusicService.isRunning)
        controllers.removeAt(0).destroy()
        assertFalse(MusicService.isRunning)
    }

    @Test
    fun `a denied repeated start keeps lifecycle state until destruction`() {
        val service = createService()

        assertEquals(Service.START_NOT_STICKY, service.onStartCommand(null, 0, 2))
        assertTrue(MusicService.isRunning)
        controllers.removeAt(0).destroy()
        assertFalse(MusicService.isRunning)
    }
}
