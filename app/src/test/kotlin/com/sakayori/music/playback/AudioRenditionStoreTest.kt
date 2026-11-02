package com.sakayori.music.playback

import android.app.Application
import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadataMutations
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sakayori.music.db.InternalDatabase
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.db.entities.SongEntity
import com.sakayori.music.constants.AudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AudioRenditionStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var database: MusicDatabase
    private lateinit var provider: StandaloneDatabaseProvider
    private lateinit var playerDirectory: File
    private lateinit var downloadDirectory: File
    private lateinit var playerCache: SimpleCache
    private lateinit var downloadCache: SimpleCache
    private lateinit var store: AudioRenditionStore

    private val rendition = AudioRendition("song", "youtube", "song", "audio/webm", "opus", 160_000, contentLength = 4, itag = 251)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = MusicDatabase(Room.inMemoryDatabaseBuilder(context, InternalDatabase::class.java).allowMainThreadQueries().build())
        provider = StandaloneDatabaseProvider(context)
        playerDirectory = temporary.newFolder("player")
        downloadDirectory = temporary.newFolder("download")
        reopenCaches()
    }

    @After
    fun tearDown() {
        playerCache.release()
        downloadCache.release()
        provider.close()
        database.close()
    }

    private fun reopenCaches() {
        playerCache = SimpleCache(playerDirectory, NoOpCacheEvictor(), provider)
        downloadCache = SimpleCache(downloadDirectory, NoOpCacheEvictor(), provider)
        store = AudioRenditionStore(database, provider, playerCache, downloadCache)
    }

    private fun putBytes(cache: Cache, key: String, bytes: ByteArray = byteArrayOf(1, 2, 3, 4)) {
        val hole = checkNotNull(cache.startReadWriteNonBlocking(key, 0, bytes.size.toLong()))
        try {
            val file = cache.startFile(key, 0, bytes.size.toLong())
            file.writeBytes(bytes)
            cache.commitFile(file, bytes.size.toLong())
            val metadata = ContentMetadataMutations()
            ContentMetadataMutations.setContentLength(metadata, bytes.size.toLong())
            cache.applyContentMetadataMutations(key, metadata)
        } finally {
            cache.releaseHoleSpan(hole)
        }
    }

    @Test
    fun `binding survives cache reopening even before the first bytes arrive`() {
        store.bindDownload("song", rendition)
        playerCache.release()
        downloadCache.release()
        reopenCaches()
        assertEquals(rendition, store.downloaded("song"))
    }

    @Test
    fun `removing player copies preserves the downloaded original`() {
        putBytes(playerCache, rendition.cacheKey)
        putBytes(downloadCache, rendition.cacheKey)
        store.bindPlayback("song", "HIGH", rendition)
        store.bindDownload("song", rendition)
        store.removePlayerCopies("song")
        assertFalse(playerCache.isCached(rendition.cacheKey, 0, 4))
        assertTrue(downloadCache.isCached(rendition.cacheKey, 0, 4))
        assertEquals(rendition, store.downloaded("song"))
        assertNull(store.playback("song", "HIGH"))
    }

    @Test
    fun `quality profiles do not replace a pinned download`() {
        val alternate = rendition.copy(itag = 140, mimeType = "audio/mp4", codecs = "mp4a.40.2")
        store.bindDownload("song", rendition)
        store.bindPlayback("song", "HIGH", alternate)
        assertEquals(rendition, store.downloaded("song"))
        assertEquals(alternate, store.playback("song", "HIGH"))
    }

    @Test
    fun `complete legacy bytes can be adopted without a format row`() = runBlocking {
        putBytes(downloadCache, "old-song")
        val legacy = store.legacyComplete("old-song")
        assertNotNull(legacy)
        assertEquals("old-song", legacy?.cacheKey)
        assertTrue(legacy?.legacy == true)
    }

    @Test
    fun `a binding alone is never proof that audio is cached`() {
        store.bindPlayback("song", "HIGH", rendition)
        assertFalse(store.hasBytes(rendition))
        assertFalse(store.isFullyCached("song", null))
        putBytes(playerCache, rendition.cacheKey)
        assertTrue(store.isFullyCached("song", null))
    }

    @Test
    fun `legacy playback uses one complete cache without mixing another cache's fragments`() = runBlocking(Dispatchers.IO) {
        putBytes(playerCache, "old-song")
        putBytes(downloadCache, "old-song", byteArrayOf(9, 9))
        val metadata = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(metadata, 4)
        downloadCache.applyContentMetadataMutations("old-song", metadata)

        val context = ApplicationProvider.getApplicationContext<Context>()
        val sources = RenditionDataSources(downloadCache, playerCache, store, PlaybackStreamResolver(context, database), PlaybackHttpClient(),
            dagger.Lazy { throw AssertionError("Disabled lossless must not resolve a source") })
        val source = sources.playbackFactory(
            quality = { AudioQuality.HIGH },
            selectionVersion = { 0L },
            usePlayerCache = { true },
            freshSelectionToken = { null },
            beforeSelection = {},
            onSelected = {},
        ).createDataSource()
        try {
            assertEquals(4L, source.open(DataSpec.Builder().setUri("old-song").setKey("old-song").build()))
            val bytes = ByteArray(4)
            var offset = 0
            while (offset < bytes.size) {
                val read = source.read(bytes, offset, bytes.size - offset)
                assertTrue(read > 0)
                offset += read
            }
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), bytes)
        } finally {
            source.close()
        }
    }

    @Test
    fun `disabled playback cache does not adopt its legacy file`() = runBlocking {
        putBytes(playerCache, "old-song")
        assertNull(store.legacyComplete("old-song", includePlayerCache = false))
    }

    @Test
    fun `legacy fragments remain incomplete even when a newer format row is shorter`() = runBlocking {
        putBytes(downloadCache, "old-song", byteArrayOf(1, 2))
        val metadata = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(metadata, 4)
        downloadCache.applyContentMetadataMutations("old-song", metadata)
        assertNull(store.legacyComplete("old-song"))
        assertFalse(store.isFullyCached("old-song", 2))
    }

    @Test
    fun `unknown rendition length can use the completed cache length`() {
        val unknownLength = rendition.copy(contentLength = null)
        store.bindPlayback("song", "HIGH", unknownLength)
        putBytes(playerCache, unknownLength.cacheKey)
        assertEquals(4L, store.knownLength(unknownLength))
        assertTrue(store.isFullyCached("song", null))
    }

    @Test
    fun `external byte clearing does not turn a persisted binding into a cache hit`() {
        store.bindPlayback("song", "HIGH", rendition)
        putBytes(playerCache, rendition.cacheKey)
        playerCache.removeResource(rendition.cacheKey)
        assertEquals(rendition, store.playback("song", "HIGH"))
        assertFalse(store.hasBytes(rendition))
        assertFalse(store.isFullyCached("song", null))
    }

    @Test
    fun `a new format row cannot establish the end of unbound legacy fragments`() = runBlocking {
        putBytes(playerCache, "song", byteArrayOf(1, 2))
        val metadata = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(metadata, -1)
        playerCache.applyContentMetadataMutations("song", metadata)
        val newRendition = rendition.copy(contentLength = 2)
        store.bindPlayback("song", "HIGH", newRendition)
        database.upsert(checkNotNull(newRendition.toFormatEntity()))
        assertNull(store.legacyComplete("song"))
        assertFalse(store.isFullyCached("song", 2))
    }

    @Test
    fun `renewed playback metadata keeps byte identity and the downloaded original`() {
        store.bindPlayback("song", "HIGH", rendition)
        store.bindDownload("song", rendition)
        val refreshed = rendition.copy(clientName = "WEB_REMIX", loudnessDb = -7.0)
        store.refreshPlaybackMetadata("HIGH", refreshed)
        assertEquals(rendition.cacheKey, refreshed.cacheKey)
        assertEquals(refreshed, store.playback("song", "HIGH"))
        assertEquals(rendition, store.downloaded("song"))
    }

    @Test
    fun `late metadata cannot overwrite a newer rendition binding`() {
        val newer = rendition.copy(itag = 140, mimeType = "audio/mp4", codecs = "mp4a.40.2")
        store.bindPlayback("song", "HIGH", rendition)
        store.bindPlayback("song", "HIGH", newer)
        store.refreshPlaybackMetadata("HIGH", rendition.copy(loudnessDb = -7.0))
        assertEquals(newer, store.playback("song", "HIGH"))
    }

    @Test
    fun `cache markers leave library and like state intact`() = runBlocking {
        val date = LocalDateTime.of(2026, 11, 2, 0, 0)
        database.insert(SongEntity("marked", "A Track", liked = true, inLibrary = date))
        database.markPlaybackCached("marked", date)
        database.clearPlaybackCached("marked")
        val song = checkNotNull(database.songEntity("marked"))
        assertTrue(song.liked)
        assertEquals(date, song.inLibrary)
        assertNull(song.dateDownload)
    }

    @Test
    fun `playback cache cleanup never clears a saved download marker`() = runBlocking {
        val date = LocalDateTime.of(2026, 11, 2, 0, 0)
        database.insert(SongEntity("downloaded", "A Track", isDownloaded = true, dateDownload = date))
        database.clearPlaybackCached("downloaded")
        assertEquals(date, database.songEntity("downloaded")?.dateDownload)
    }

    @Test
    fun `an optional FLAC descriptor does not replace the legacy YouTube format row`() = runBlocking {
        putBytes(playerCache, "song")
        val metadata = ContentMetadataMutations()
        ContentMetadataMutations.setContentLength(metadata, -1)
        playerCache.applyContentMetadataMutations("song", metadata)
        database.upsert(checkNotNull(rendition.toFormatEntity()))
        store.bindPlayback("song", AudioRendition.DIRECT_FLAC_PROFILE,
            rendition.copy(provider = AudioRendition.DIRECT_FLAC, itag = null, mimeType = "audio/flac", codecs = "flac"))
        assertNotNull(store.legacyComplete("song"))
        assertTrue(store.isFullyCached("song", 4))
    }
}
