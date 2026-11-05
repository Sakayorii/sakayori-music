/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.viewmodels

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sakayori.innertube.InnerTube
import com.sakayori.innertube.YouTube
import com.sakayori.innertube.models.SongItem
import com.sakayori.music.db.InternalDatabase
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.db.entities.PlaylistEntity
import com.sakayori.music.ui.menu.addOnlinePlaylistSelection
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class OnlinePlaylistSelectionTest {
    @Test
    fun `copying selected occurrences preserves identity and order without re-searching`() = runBlocking {
        val database = MusicDatabase(Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), InternalDatabase::class.java).allowMainThreadQueries().build())
        val tube = ReflectionHelpers.getField<InnerTube>(YouTube, "innerTube")
        val previous = ReflectionHelpers.getField<HttpClient>(tube, "httpClient")
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond("""{"responseContext":{}}""", headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) {
            defaultRequest { url("https://example.test/") }
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true; explicitNulls = false }) }
        }
        ReflectionHelpers.setField(tube, "httpClient", client)
        try {
            val playlist = PlaylistEntity(name = "Copy")
            database.insert(playlist)
            val target = checkNotNull(database.playlistBlocking(playlist.id))
            val a = SongItem("a", "A", emptyList(), thumbnail = "", setVideoId = "first")
            val b = SongItem("b", "B", emptyList(), thumbnail = "", setVideoId = "b")
            addOnlinePlaylistSelection(database, target, listOf(a, b, a.copy(setVideoId = "second"), a.copy(setVideoId = null)))

            val saved = database.playlistSongsBlocking(playlist.id)
            assertEquals(listOf("a", "b", "a", "a"), saved.map { it.song.id })
            assertEquals(listOf(0, 1, 2, 3), saved.map { it.map.position })
            assertEquals(listOf(null, null, null, null), saved.map { it.map.setVideoId })
            assertEquals(0, requests)
        } finally {
            ReflectionHelpers.setField(tube, "httpClient", previous)
            client.close()
            database.close()
        }
    }
}
