/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Monochrome/HiFi protocol adaptation: kipavy/spotube-plugin-lossless-sources,
 * revision f375d06c21f01ff3e662331293815bed2a852ded (MIT).
 * Attribution/license: docs/licenses/spotube-lossless-sources-MIT.txt.
 */

package com.sakayori.music.playback.flac

import android.content.Context
import android.os.SystemClock
import com.sakayori.music.constants.LosslessApiUrlKey
import com.sakayori.music.di.ApplicationScope
import com.sakayori.music.extensions.SilentHandler
import com.sakayori.music.models.MediaMetadata
import com.sakayori.music.playback.AudioRendition
import com.sakayori.music.playback.StreamRequestPriority
import com.sakayori.music.playback.StreamResolutionLimiter
import com.sakayori.music.playback.foregroundStreamPriority
import com.sakayori.music.utils.dataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.util.Base64
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** Source resolution only; no media-server provisioning, embedded account tokens or DRM keys. */
@Singleton
class LosslessSourceRegistry @Inject constructor(
    @ApplicationContext context: Context,
    @ApplicationScope applicationScope: CoroutineScope,
    private val http: LosslessApiHttp,
    private val archive: ArchiveFlacSource,
) {
    private data class Lease(val location: FlacLocation, val expiresAtMs: Long)
    private data class Protocol(val name: String, val expiresAtMs: Long)
    private val lock = Any()
    private val leases = LinkedHashMap<String, Lease>(0, 0.75f, true)
    private val cooldowns = LinkedHashMap<String, Long>()
    private val protocols = LinkedHashMap<String, Protocol>(0, 0.75f, true)
    private val mutableConfiguration = MutableStateFlow("")
    private val ready = CompletableDeferred<Unit>()
    private val apiPermits = StreamResolutionLimiter(maxConcurrent = 2, maxBackground = 1)
    private val json = Json { ignoreUnknownKeys = true }
    internal val configuration = mutableConfiguration.asStateFlow()
    internal val isConfigurationReady: Boolean get() = ready.isCompleted

    init {
        applicationScope.launch(Dispatchers.IO + SilentHandler) {
            try {
                context.dataStore.data.map { it[LosslessApiUrlKey].orEmpty() }
                    .distinctUntilChanged().collect {
                        if (ready.isCompleted && mutableConfiguration.value != it) synchronized(lock) {
                            protocols.clear()
                            cooldowns.clear()
                            leases.clear()
                        }
                        mutableConfiguration.value = it
                        ready.complete(Unit)
                    }
            } finally {
                // A settings read failure must not leave every optional lookup waiting forever.
                ready.complete(Unit)
            }
        }
    }

    internal suspend fun awaitConfiguration() = ready.await()

    internal suspend fun find(
        target: MediaMetadata,
        configuredBase: String,
        priority: StateFlow<StreamRequestPriority> = foregroundStreamPriority,
        onValidated: (FlacResource) -> Unit = {},
    ): FlacResource? = coroutineScope {
        if (target.duration <= 0 || target.title.isBlank() || target.title.length > 512 || target.artists.isEmpty() ||
            target.artists.any { it.name.isBlank() || it.name.length > 512 } ||
            target.isVideoSong || target.isEpisode || target.uploadEntityId != null ||
            FlacPlaybackCapabilities.inspect(FlacStreamInfo(44_100, 2, 16, 1)) != FlacPlaybackCapability.SUPPORTED
        ) return@coroutineScope null
        val custom = configuredBase.takeIf { it.isNotBlank() }?.let(LosslessApiHttp.Companion::validatedApiBase)
        val defaultBase = MONOCHROME_BASE.toHttpUrl()
        val resolvers = buildList<Pair<String, suspend () -> FlacResource?>> {
            if (custom != null) {
                val protocol = synchronized(lock) { protocols[custom.toString()] }
                    ?.takeIf { it.expiresAtMs > SystemClock.elapsedRealtime() }?.name
                if (protocol != MONOCHROME && custom != defaultBase) add("$HIFI:${custom}" to suspend { findHifi(target, custom) })
                if (protocol != HIFI && custom != defaultBase) add("$MONOCHROME:$custom" to suspend { findMonochrome(target, custom) })
            }
            add("$MONOCHROME:$defaultBase" to suspend { findMonochrome(target, defaultBase) })
            add(ArchiveFlacSource.SOURCE_ID to suspend { archive.find(target) })
        }
        val answers = Channel<Result<FlacResource?>>(resolvers.size)
        val jobs = resolvers.map { (key, resolve) ->
            launch(Dispatchers.IO) {
                val answer = apiPermits.withPermit(priority) {
                    if (inCooldown(key)) Result.failure(IOException("Optional source cooling down")) else try {
                        Result.success(resolve())
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: LosslessApiUnavailable) {
                        val pause = failure.retryAfterMs ?: if (failure.status == 401 || failure.status == 403) 15 * 60_000L else 60_000L
                        cooldown(key, pause)
                        Result.failure(failure)
                    } catch (failure: Exception) {
                        cooldown(key, 60_000L)
                        Result.failure(failure)
                    }
                }
                answers.send(answer)
            }
        }
        try {
            var failure: Throwable? = null
            repeat(resolvers.size) {
                val answer = answers.receive()
                answer.getOrNull()?.let {
                    // Publish before cancelled sibling calls unwind. Their cleanup does not
                    // get to turn a validated answer inside the window into a timeout.
                    onValidated(it)
                    return@coroutineScope it
                }
                if (answer.isFailure) failure = answer.exceptionOrNull()
            }
            failure?.let { throw it }
            null
        } finally {
            jobs.forEach { it.cancel() }
            answers.cancel()
        }
    }

    private suspend fun findMonochrome(target: MediaMetadata, base: HttpUrl): FlacResource? {
        val search = http.objectFrom(base.endpoint("search", "tracks").newBuilder()
            .addQueryParameter("q", query(target)).addQueryParameter("limit", "20").build())
        if (search["tracks"] !is kotlinx.serialization.json.JsonArray) throw IOException("Unsupported Monochrome search response")
        rememberProtocol(base, MONOCHROME)
        val candidates = search.arrayAt("tracks").take(20).mapNotNull { it as? JsonObject }.mapNotNull { track ->
            if (track.booleanAt("playable") == false) return@mapNotNull null
            val id = track.stringAt("id")?.takeIf(::validTrackId) ?: return@mapNotNull null
            LosslessTrackCandidate(
                id, track.stringAt("title").orEmpty(),
                track.arrayAt("artistNames").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                track.stringAt("duration")?.toDoubleOrNull()?.takeIf { it.isFinite() && it in 1.0..86_400_000.0 }?.toLong() ?: 0,
                isrc = track.stringAt("isrc"), explicit = track.booleanAt("explicit"),
            )
        }
        val chosen = matchLosslessTrack(target, candidates) ?: return null
        val location = FlacLocation(base.endpoint("track", chosen.id).toString())
        return http.probe(target.id, MONOCHROME, base.toString(), chosen.id, location, chosen.durationMs)
    }

    private suspend fun findHifi(target: MediaMetadata, base: HttpUrl): FlacResource? {
        val search = http.objectFrom(base.endpoint("search", "").newBuilder()
            .addQueryParameter("s", query(target)).addQueryParameter("limit", "20").build())
        if (search.objectAt("data")?.get("items") !is kotlinx.serialization.json.JsonArray) throw IOException("Unsupported HiFi search response")
        rememberProtocol(base, HIFI)
        val candidates = search.objectAt("data")?.arrayAt("items").orEmpty().take(20)
            .mapNotNull { it as? JsonObject }.mapNotNull { track ->
                val id = track.stringAt("id")?.takeIf { it.matches(TIDAL_ID) } ?: return@mapNotNull null
                val seconds = track.stringAt("duration")?.toLongOrNull()?.takeIf { it in 1..86_400 } ?: return@mapNotNull null
                val artists = (listOfNotNull(track.objectAt("artist")?.stringAt("name")) +
                    track.arrayAt("artists").mapNotNull { (it as? JsonObject)?.stringAt("name") }).distinct()
                LosslessTrackCandidate(id, track.stringAt("title").orEmpty(), artists, seconds * 1_000,
                    album = track.objectAt("album")?.stringAt("title"), isrc = track.stringAt("isrc"),
                    explicit = track.booleanAt("explicit"), version = track.stringAt("version"))
            }
        val chosen = matchLosslessTrack(target, candidates) ?: return null
        val location = hifiLocation(base, chosen.id) ?: return null
        val resource = http.probe(target.id, HIFI, base.toString(), chosen.id, location, chosen.durationMs) ?: return null
        remember(resource, location)
        return resource
    }

    private suspend fun hifiLocation(base: HttpUrl, trackId: String): FlacLocation? {
        // A requested tier is not proof of returned quality: lower-tier upstream accounts may
        // answer HIGH/AAC. Only a full, unencrypted native-FLAC manifest qualifies here.
        val response = http.objectFrom(base.endpoint("track", "").newBuilder()
            .addQueryParameter("id", trackId).addQueryParameter("quality", "LOSSLESS").build())
        return decodeHifiLocation(response, trackId)
    }

    internal fun restore(rendition: AudioRendition): FlacResource? {
        archive.restore(rendition)?.let { return it }
        if (rendition.provider != AudioRendition.DIRECT_FLAC || rendition.sourceId !in setOf(MONOCHROME, HIFI)) return null
        val base = rendition.sourceLocator?.let(LosslessApiHttp.Companion::validatedApiBase) ?: return null
        val id = rendition.sourceResourceId?.takeIf(::validTrackId) ?: return null
        if (rendition.sourceId == HIFI && !TIDAL_ID.matches(id)) return null
        return runCatching {
            FlacResource(rendition.mediaId, checkNotNull(rendition.sourceId), id, rendition.version ?: return null,
                rendition.contentLength ?: return null,
                FlacStreamInfo(rendition.sampleRate ?: return null, rendition.channels ?: return null,
                    rendition.bitDepth ?: return null, rendition.totalSamples ?: return null),
                contentHash = rendition.contentHash, sourceLocator = base.toString())
        }.getOrNull()?.takeIf { it.rendition().cacheKey == rendition.cacheKey }
    }

    internal suspend fun location(resource: FlacResource): FlacLocation = withContext(Dispatchers.IO) {
        when (resource.sourceId) {
            ArchiveFlacSource.SOURCE_ID -> archive.location(resource)
            MONOCHROME -> {
                if (resource.sourceLocator != MONOCHROME_BASE) withTimeout(2_000L) { awaitConfiguration() }
                if (!isCurrentResource(resource) || !validTrackId(resource.resourceId)) throw IOException("Lossless API configuration changed")
                val base = resource.sourceLocator?.let(LosslessApiHttp.Companion::validatedApiBase)
                    ?: throw IOException("Invalid lossless API location")
                FlacLocation(base.endpoint("track", resource.resourceId).toString())
            }
            HIFI -> {
                withTimeout(2_000L) { awaitConfiguration() }
                if (!isCurrentResource(resource)) throw IOException("Lossless API configuration changed")
                synchronized(lock) { leases[resource.rendition().cacheKey] }
                    ?.takeIf { it.expiresAtMs > SystemClock.elapsedRealtime() }?.let { return@withContext it.location }
                val base = resource.sourceLocator?.let(LosslessApiHttp.Companion::validatedApiBase)
                    ?: throw IOException("Invalid lossless API location")
                if (!TIDAL_ID.matches(resource.resourceId)) throw IOException("Invalid lossless resource ID")
                val location = withTimeout(2_000L) { hifiLocation(base, resource.resourceId) }
                    ?: throw IOException("Pinned lossless resource unavailable")
                currentCoroutineContext().ensureActive()
                if (!isCurrentResource(resource)) throw IOException("Lossless API configuration changed")
                remember(resource, location)
                // FlacHttpDataSource validates the persisted ETag and total length before any
                // renewed location can append bytes, including on a seek/resume.
                location
            }
            else -> throw IOException("Unsupported lossless source")
        }
    }

    internal fun isCurrentResource(resource: FlacResource): Boolean {
        if (resource.sourceId == ArchiveFlacSource.SOURCE_ID) return true
        val base = resource.sourceLocator?.let(LosslessApiHttp.Companion::validatedApiBase) ?: return false
        if (resource.sourceId == MONOCHROME && base == MONOCHROME_BASE.toHttpUrl()) return true
        return base == configuration.value.takeIf { it.isNotBlank() }?.let(LosslessApiHttp.Companion::validatedApiBase)
    }

    internal suspend fun validate(resource: FlacResource): Boolean {
        if (!isCurrentResource(resource)) return false
        val fresh = http.probe(resource.mediaId, resource.sourceId, resource.sourceLocator.orEmpty(), resource.resourceId,
            location(resource), resource.streamInfo.durationUs?.div(1_000) ?: return false)
        return fresh != null && fresh.etag == resource.etag && fresh.contentLength == resource.contentLength &&
            fresh.streamInfo == resource.streamInfo
    }

    private fun rememberProtocol(base: HttpUrl, name: String) = synchronized(lock) {
        protocols[base.toString()] = Protocol(name, SystemClock.elapsedRealtime() + 15 * 60_000L)
        trim(protocols, 16)
    }

    private fun decodeHifiLocation(response: JsonObject, trackId: String): FlacLocation? {
        val data = response.objectAt("data") ?: return null
        if (data.stringAt("trackId") != trackId || data.stringAt("assetPresentation") != "FULL" ||
            data.stringAt("audioMode") != "STEREO" || data.stringAt("audioQuality") !in setOf("LOSSLESS", "HI_RES_LOSSLESS") ||
            data.stringAt("manifestMimeType") != "application/vnd.tidal.bts"
        ) return null
        val encoded = data.stringAt("manifest")?.takeIf { it.length in 1..64_000 } ?: return null
        val manifest = try {
            json.parseToJsonElement(String(Base64.getDecoder().decode(encoded), Charsets.UTF_8)) as? JsonObject
        } catch (_: Exception) { null } ?: return null
        if (manifest.stringAt("encryptionType") != "NONE" ||
            manifest.stringAt("mimeType")?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT) != "audio/flac" ||
            manifest.stringAt("codecs")?.trim()?.lowercase(Locale.ROOT) != "flac"
        ) return null
        val url = manifest.arrayAt("urls").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            .singleOrNull()?.let(LosslessApiHttp.Companion::validatedMediaUrl) ?: return null
        return FlacLocation(url.toString())
    }

    private fun remember(resource: FlacResource, location: FlacLocation) = synchronized(lock) {
        val url = location.url.toHttpUrl()
        val epochExpiry = url.queryParameter("etsp")?.toLongOrNull()
            ?: url.queryParameter("token")?.substringBefore('~')?.toLongOrNull()
        val now = System.currentTimeMillis()
        val boundedExpiry = epochExpiry?.takeIf { it in 0..(now / 1_000 + 86_400) }
        val leaseMs = boundedExpiry?.let { (it * 1_000 - now - 5_000).coerceIn(0, 60_000) } ?: 60_000L
        leases[resource.rendition().cacheKey] = Lease(location, SystemClock.elapsedRealtime() + leaseMs)
        trim(leases, 128)
    }

    private fun inCooldown(key: String): Boolean = synchronized(lock) {
        val until = cooldowns[key] ?: return@synchronized false
        if (until > SystemClock.elapsedRealtime()) true else { cooldowns.remove(key); false }
    }

    private fun cooldown(key: String, millis: Long) = synchronized(lock) {
        cooldowns[key] = SystemClock.elapsedRealtime() + millis.coerceIn(1_000, 15 * 60_000)
        trim(cooldowns, 16)
    }

    private fun <K, V> trim(map: LinkedHashMap<K, V>, limit: Int) {
        while (map.size > limit) map.entries.iterator().apply { next(); remove() }
    }

    private fun query(target: MediaMetadata) = "${target.title} ${target.artists.joinToString { it.name }}"
    private fun validTrackId(id: String) = TRACK_ID.matches(id)
    private fun HttpUrl.endpoint(vararg segments: String) = newBuilder().apply { segments.forEach { addPathSegment(it) } }.build()

    internal companion object {
        const val MONOCHROME_BASE = "https://tracks.monochrome.st/"
        private const val MONOCHROME = "monochrome"
        private const val HIFI = "hifi-api"
        private val TRACK_ID = Regex("[A-Za-z0-9_-]{1,160}")
        private val TIDAL_ID = Regex("[0-9]{1,18}")
    }
}
