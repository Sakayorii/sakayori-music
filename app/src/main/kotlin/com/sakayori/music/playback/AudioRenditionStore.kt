/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.di.DownloadCache
import com.sakayori.music.di.PlayerCache
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bindings use an app-owned table in the existing Media3 database. They cannot be metadata-only
 * SimpleCache entries: Media3 removes empty cached-content entries when it opens the cache.
 * Access from media/download loader or IO threads only: the injected caches initialise lazily.
 */
@Singleton
class AudioRenditionStore @Inject constructor(
    private val database: MusicDatabase,
    private val databaseProvider: DatabaseProvider,
    @PlayerCache private val playerCache: Cache,
    @DownloadCache private val downloadCache: Cache,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private data class BindingKey(val cache: String, val mediaId: String, val profile: String)
    private data class CachedBinding(val rendition: AudioRendition?)
    private val lock = Any()
    private val bindings = LinkedHashMap<BindingKey, CachedBinding>(0, 0.75f, true)
    @Volatile private var tableReady = false

    internal fun playback(mediaId: String, profile: String): AudioRendition? =
        read(PLAYER, mediaId, profile)

    internal fun downloaded(mediaId: String): AudioRendition? =
        read(DOWNLOAD, mediaId, DOWNLOAD)

    internal fun bindPlayback(mediaId: String, profile: String, rendition: AudioRendition) {
        require(rendition.mediaId == mediaId && profile in PROFILES)
        write(PLAYER, mediaId, profile, rendition)
    }

    internal fun bindDownload(mediaId: String, rendition: AudioRendition) {
        require(rendition.mediaId == mediaId)
        write(DOWNLOAD, mediaId, DOWNLOAD, rendition)
    }

    internal fun refreshPlaybackMetadata(profile: String, rendition: AudioRendition) = synchronized(lock) {
        if (read(PLAYER, rendition.mediaId, profile)?.cacheKey == rendition.cacheKey) {
            write(PLAYER, rendition.mediaId, profile, rendition)
        }
    }

    internal fun forgetPlayback(mediaId: String, profile: String, expectedKey: String) = synchronized(lock) {
        if (read(PLAYER, mediaId, profile)?.cacheKey == expectedKey) {
            bindingsDatabase().delete(TABLE, "cache_kind = ? AND media_id = ? AND profile = ?", arrayOf(PLAYER, mediaId, profile))
            bindings.remove(BindingKey(PLAYER, mediaId, profile))
        }
    }

    /** Adopt only a complete legacy file. Never append newly resolved bytes to an unbound fragment. */
    internal suspend fun legacyComplete(mediaId: String, includePlayerCache: Boolean = true): AudioRendition? {
        val candidates = if (includePlayerCache) {
            listOf(LegacyAudioCache.DOWNLOAD to downloadCache, LegacyAudioCache.PLAYER to playerCache)
        } else {
            listOf(LegacyAudioCache.DOWNLOAD to downloadCache)
        }
        if (candidates.none { (_, cache) -> cache.getCachedSpans(mediaId).isNotEmpty() }) return null
        val format = if (canUseLegacyFormat(mediaId)) database.formatOnce(mediaId) else null
        for ((kind, cache) in candidates) {
            val length = completeLegacyLength(cache, mediaId, format?.contentLength) ?: continue
            // A legacy format row may describe a later quality selection. If its size differs,
            // retain the complete file without publishing that unrelated encoding as fact.
            val rendition = if (format != null && format.contentLength == length) {
                AudioRendition.legacy(mediaId, format)
            } else {
                AudioRendition(mediaId, AudioRendition.YOUTUBE, mediaId, "", "", 0, legacy = true)
            }
            return rendition.copy(contentLength = length, legacyCache = kind)
        }
        return null
    }

    internal fun completeInDownloads(rendition: AudioRendition): Boolean =
        rendition.legacyCache != LegacyAudioCache.PLAYER && complete(downloadCache, rendition)

    internal fun completeInEither(rendition: AudioRendition): Boolean =
        if (rendition.legacy) {
            legacyCache(rendition)?.let { complete(it, rendition) } == true
        } else {
            complete(downloadCache, rendition) || complete(playerCache, rendition)
        }

    internal fun hasBytes(rendition: AudioRendition, includePlayerCache: Boolean = true): Boolean =
        if (rendition.legacy) {
            (includePlayerCache || rendition.legacyCache == LegacyAudioCache.DOWNLOAD) && completeInEither(rendition)
        } else {
            downloadCache.getCachedSpans(rendition.cacheKey).isNotEmpty() ||
                (includePlayerCache && playerCache.getCachedSpans(rendition.cacheKey).isNotEmpty())
        }

    internal fun knownLength(rendition: AudioRendition): Long? =
        rendition.contentLength?.takeIf { it > 0 }
            ?: ContentMetadata.getContentLength(downloadCache.getContentMetadata(rendition.cacheKey)).takeIf { it > 0 }
            ?: ContentMetadata.getContentLength(playerCache.getContentMetadata(rendition.cacheKey)).takeIf { it > 0 }

    internal fun legacyCache(rendition: AudioRendition): Cache? = when (rendition.legacyCache) {
        LegacyAudioCache.DOWNLOAD -> downloadCache
        LegacyAudioCache.PLAYER -> playerCache
        null -> null
    }

    /** Used by the existing Cache Playlist contract, whose input remains the logical song ID. */
    fun isFullyCached(mediaId: String, legacyLength: Long?): Boolean {
        val download = downloaded(mediaId)
        download?.let { if (completeInDownloads(it)) return true }
        val playback = playbackBindings(mediaId)
        if (playback.any(::completeInEither)) return true
        val fallback = legacyLength.takeUnless {
            download?.let { it.provider == AudioRendition.YOUTUBE && !it.legacy } == true ||
                playback.any { it.provider == AudioRendition.YOUTUBE && !it.legacy }
        }
        if (completeLegacyLength(downloadCache, mediaId, fallback) != null ||
            completeLegacyLength(playerCache, mediaId, fallback) != null
        ) return true
        return false
    }

    fun removePlayerCopies(mediaId: String) {
        playbackBindings(mediaId).map { it.cacheKey }.distinct().forEach(playerCache::removeResource)
        // Also remove older revisions displaced from a profile. This is an explicit IO action,
        // never part of a lookup or playback callback.
        playerCache.keys.filter { key -> playerCache.getContentMetadata(key).get(OWNER, "") == mediaId }
            .forEach(playerCache::removeResource)
        playerCache.removeResource(mediaId)
        removeBindings(PLAYER, mediaId)
    }

    internal fun removeDownload(mediaId: String) {
        downloaded(mediaId)?.let { downloadCache.removeResource(it.cacheKey) }
        downloadCache.keys.filter { key -> downloadCache.getContentMetadata(key).get(OWNER, "") == mediaId }
            .forEach(downloadCache::removeResource)
        downloadCache.removeResource(mediaId)
        removeBindings(DOWNLOAD, mediaId)
    }

    private fun playbackBindings(mediaId: String): List<AudioRendition> = PROFILES.mapNotNull { profile ->
        playback(mediaId, profile)
    }

    private fun complete(cache: Cache, rendition: AudioRendition): Boolean {
        val storedLength = ContentMetadata.getContentLength(cache.getContentMetadata(rendition.cacheKey)).takeIf { it > 0 }
        val expectedLength = rendition.contentLength?.takeIf { it > 0 }
        if (storedLength != null && expectedLength != null && storedLength != expectedLength) return false
        val length = expectedLength ?: storedLength ?: return false
        return cache.isCached(rendition.cacheKey, 0, length)
    }

    private fun completeLegacyLength(cache: Cache, mediaId: String, fallbackLength: Long?): Long? {
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(mediaId)).takeIf { it > 0 }
            ?: fallbackLength?.takeIf { it > 0 }
            ?: return null
        return length.takeIf { cache.isCached(mediaId, 0, it) }
    }

    private fun canUseLegacyFormat(mediaId: String): Boolean =
        downloaded(mediaId)?.let { it.provider == AudioRendition.YOUTUBE && !it.legacy } != true &&
            playbackBindings(mediaId).none { it.provider == AudioRendition.YOUTUBE && !it.legacy }

    private fun read(cache: String, mediaId: String, profile: String): AudioRendition? = synchronized(lock) {
        val key = BindingKey(cache, mediaId, profile)
        bindings[key]?.let { return@synchronized it.rendition }
        val encoded = bindingsDatabase().query(
            TABLE, arrayOf("descriptor"), "cache_kind = ? AND media_id = ? AND profile = ?",
            arrayOf(cache, mediaId, profile), null, null, null,
        ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        val rendition = encoded?.let { runCatching { json.decodeFromString<AudioRendition>(it) }.getOrNull() }
            ?.takeIf { it.mediaId == mediaId }
            ?.let {
                if (it.legacy && it.legacyCache == null && cache == DOWNLOAD) it.copy(legacyCache = LegacyAudioCache.DOWNLOAD) else it
            }
        remember(key, rendition)
        rendition
    }

    private fun write(cache: String, mediaId: String, profile: String, rendition: AudioRendition) {
        synchronized(lock) {
            if (read(cache, mediaId, profile) != rendition) {
                val values = ContentValues().apply {
                    put("cache_kind", cache)
                    put("media_id", mediaId)
                    put("profile", profile)
                    put("descriptor", json.encodeToString(rendition))
                }
                if (bindingsDatabase().insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE) == -1L) {
                    throw IOException("Unable to persist audio rendition")
                }
                remember(BindingKey(cache, mediaId, profile), rendition)
            }
        }
        val byteCache = if (cache == PLAYER) playerCache else downloadCache
        if (byteCache.getContentMetadata(rendition.cacheKey).get(OWNER, "") != rendition.mediaId) {
            byteCache.applyContentMetadataMutations(rendition.cacheKey, ContentMetadataMutations().set(OWNER, rendition.mediaId))
        }
    }

    private fun removeBindings(cache: String, mediaId: String) = synchronized(lock) {
        bindingsDatabase().delete(TABLE, "cache_kind = ? AND media_id = ?", arrayOf(cache, mediaId))
        bindings.keys.removeAll { it.cache == cache && it.mediaId == mediaId }
    }

    private fun remember(key: BindingKey, rendition: AudioRendition?) {
        bindings[key] = CachedBinding(rendition)
        while (bindings.size > 500) bindings.entries.iterator().apply { next(); remove() }
    }

    private fun bindingsDatabase(): SQLiteDatabase {
        val db = databaseProvider.writableDatabase
        if (!tableReady) synchronized(lock) {
            if (!tableReady) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS $TABLE (
                        cache_kind TEXT NOT NULL,
                        media_id TEXT NOT NULL,
                        profile TEXT NOT NULL,
                        descriptor TEXT NOT NULL,
                        PRIMARY KEY (cache_kind, media_id, profile)
                    )
                """.trimIndent())
                tableReady = true
            }
        }
        return db
    }

    internal companion object {
        const val DOWNLOAD = "download"
        private const val PLAYER = "player"
        private const val TABLE = "sakayori_audio_renditions_v1"
        private const val OWNER = "sakayori.media-id"
        val PROFILES = listOf("LOW", "HIGH", "AUTO_METERED", "AUTO_UNMETERED", AudioRendition.DIRECT_FLAC_PROFILE)
    }
}
