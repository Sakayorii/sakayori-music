/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback.flac

import com.sakayori.music.models.MediaMetadata
import com.sakayori.music.playback.AudioRendition
import com.sakayori.music.playback.PlaybackHttpClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.abs

/** Public, pre-existing originals from Internet Archive netlabels. No paid account or transcoding. */
@Singleton
class ArchiveFlacSource @Inject constructor(private val httpClient: PlaybackHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    internal suspend fun find(target: MediaMetadata): FlacResource? {
        val album = target.album?.title?.takeIf { it.isNotBlank() } ?: return null
        val artist = target.artists.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: return null
        if (target.title.isBlank() || target.duration <= 0 || target.isEpisode || target.isVideoSong || target.uploadEntityId != null) return null
        if (FlacPlaybackCapabilities.inspect(FlacStreamInfo(44_100, 2, 16, 1)) != FlacPlaybackCapability.SUPPORTED) return null
        val query = "mediatype:audio AND collection:netlabels AND format:Flac AND creator:${quote(artist)} AND title:${quote(album)}"
        val searchUrl = BASE.newBuilder().addPathSegment("advancedsearch.php")
            .addQueryParameter("q", query).addQueryParameter("fl[]", "identifier")
            .addQueryParameter("rows", MAX_ALBUMS.toString()).addQueryParameter("output", "json").build()
        val search = objectFrom(searchUrl)?.objectAt("response") ?: return null
        val count = search.text("numFound")?.toIntOrNull() ?: return null
        if (count !in 1..MAX_ALBUMS) return null
        val candidates = mutableListOf<ArchiveCandidate>()
        for (entry in search.arrayAt("docs")) {
            val id = (entry as? JsonObject)?.text("identifier")?.takeIf(::validItemId) ?: continue
            val document = objectFrom(BASE.newBuilder().addPathSegment("metadata").addPathSegment(id).build()) ?: continue
            candidates += matchingFiles(target, id, document)
        }
        // Duplicate uploads of exactly the same file are fine; different files with the same
        // labels are ambiguous and stay on the normal source.
        val candidate = uniqueCandidate(candidates) ?: return null
        val request = Request.Builder().url(location(candidate.itemId, candidate.fileName))
            .header("Range", "bytes=0-${FlacStreamInfo.HEADER_LENGTH - 1}")
            .header("Accept-Encoding", "identity").build()
        return response(request) { reply ->
            val etag = reply.header("ETag")?.takeIf(::isStrongEtag) ?: return@response null
            if (reply.code != 206 || reply.header("Content-Range") != "bytes 0-41/${candidate.size}") return@response null
            val bytes = reply.body?.byteStream()?.let { input ->
                val header = ByteArray(FlacStreamInfo.HEADER_LENGTH)
                var offset = 0
                while (offset < header.size) {
                    val read = input.read(header, offset, header.size - offset)
                    if (read <= 0) return@let null
                    offset += read
                }
                header
            } ?: return@response null
            val info = FlacStreamInfo.parse(bytes)
            val durationUs = info.durationUs ?: return@response null
            if (abs(durationUs - target.duration.toLong() * 1_000_000) > DURATION_SLACK_US ||
                FlacPlaybackCapabilities.inspect(info) != FlacPlaybackCapability.SUPPORTED
            ) return@response null
            FlacResource(target.id, SOURCE_ID, "${candidate.itemId}/${candidate.fileName}", etag, candidate.size, info, candidate.md5)
        }
    }

    internal fun restore(rendition: AudioRendition): FlacResource? {
        if (rendition.provider != AudioRendition.DIRECT_FLAC || rendition.sourceId != SOURCE_ID) return null
        val path = rendition.sourceResourceId ?: return null
        val parts = path.split('/', limit = 2)
        if (parts.size != 2 || !validItemId(parts[0]) || !validFileName(parts[1])) return null
        return runCatching {
            FlacResource(
                rendition.mediaId, SOURCE_ID, path, rendition.version ?: return null,
                rendition.contentLength ?: return null,
                FlacStreamInfo(rendition.sampleRate ?: return null, rendition.channels ?: return null,
                    rendition.bitDepth ?: return null, rendition.totalSamples ?: return null),
                rendition.contentHash,
            )
        }.getOrNull()?.takeIf { it.rendition().cacheKey == rendition.cacheKey }
    }

    internal fun location(resource: FlacResource): FlacLocation {
        require(resource.sourceId == SOURCE_ID)
        val parts = resource.resourceId.split('/', limit = 2)
        require(parts.size == 2 && validItemId(parts[0]) && validFileName(parts[1]))
        return FlacLocation(location(parts[0], parts[1]).toString())
    }

    private suspend fun objectFrom(url: HttpUrl): JsonObject? = response(Request.Builder().url(url).build()) { reply ->
        if (reply.code != 200) return@response null
        val body = reply.body ?: return@response null
        if (body.contentLength() > MAX_JSON_BYTES) return@response null
        val buffer = Buffer()
        val source = body.source()
        while (buffer.size <= MAX_JSON_BYTES) {
            if (source.read(buffer, minOf(8192L, MAX_JSON_BYTES + 1 - buffer.size)) == -1L) break
        }
        if (buffer.size > MAX_JSON_BYTES) return@response null
        json.parseToJsonElement(buffer.readUtf8()) as? JsonObject
    }

    private suspend fun <T> response(request: Request, read: (Response) -> T?): T? = suspendCancellableCoroutine { continuation ->
        val call = httpClient.get().newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val value = if (continuation.isActive) runCatching { read(it) }.getOrNull() else null
                    if (continuation.isActive) continuation.resume(value)
                }
            }
        })
    }

    internal data class ArchiveCandidate(val itemId: String, val fileName: String, val size: Long, val md5: String)

    internal companion object {
        const val SOURCE_ID = "internet-archive"
        private const val MAX_ALBUMS = 3
        private const val MAX_JSON_BYTES = 1024 * 1024L
        private const val DURATION_SLACK_US = 1_500_000L
        private val BASE = "https://archive.org/".toHttpUrl()
        private val TRACK_NUMBER = Regex("^\\d{1,3}[\\s._-]+")
        private val MD5 = Regex("[a-fA-F0-9]{32}")
        private val ITEM_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,199}")
        private val PUNCTUATION = Regex("[\\p{P}\\p{S}]")
        private val WHITESPACE = Regex("\\s+")

        internal fun uniqueCandidate(candidates: List<ArchiveCandidate>): ArchiveCandidate? = candidates.distinctBy { it.md5 }.singleOrNull()

        internal fun matchingFiles(target: MediaMetadata, itemId: String, document: JsonObject): List<ArchiveCandidate> {
            if (target.album?.title.isNullOrBlank() || target.title.isBlank() || target.isEpisode || target.isVideoSong || target.uploadEntityId != null) return emptyList()
            val metadata = document.objectAt("metadata") ?: return emptyList()
            if (metadata.text("access-restricted-item") == "true" || "netlabels" !in metadata.texts("collection")) return emptyList()
            val creators = metadata.texts("creator").map(::normalized).toSet()
            val expectedCreators = target.artists.map { normalized(it.name) }.toSet()
            if (expectedCreators.isEmpty() || "" in expectedCreators || creators != expectedCreators) return emptyList()
            if (normalized(metadata.text("title").orEmpty()) != normalized(target.album?.title.orEmpty())) return emptyList()
            val files = document.arrayAt("files").mapNotNull { it as? JsonObject }
            val byName = files.mapNotNull { file -> file.text("name")?.let { it to file } }.toMap()
            val expectedTitle = normalized(target.title)
            return files.mapNotNull { file ->
                if (!file.text("format").equals("Flac", true) || file.text("private") == "true") return@mapNotNull null
                val original = if (file.text("source") == "original") file else file.text("original")?.let(byName::get)
                if (original?.text("source") != "original" || original.text("format")?.uppercase(Locale.ROOT) !in setOf("FLAC", "WAVE", "AIFF")) return@mapNotNull null
                val name = file.text("name")?.takeIf(::validFileName) ?: return@mapNotNull null
                val title = file.text("title") ?: original.text("title") ?: name.substringAfterLast('/').substringBeforeLast('.').replaceFirst(TRACK_NUMBER, "")
                if (normalized(title) != expectedTitle) return@mapNotNull null
                val duration = parseDuration(file.text("length") ?: original.text("length")) ?: return@mapNotNull null
                if (target.duration <= 0 || abs(duration - target.duration) > 1.5) return@mapNotNull null
                val size = file.text("size")?.toLongOrNull()?.takeIf { it >= FlacStreamInfo.HEADER_LENGTH } ?: return@mapNotNull null
                val md5 = file.text("md5")?.takeIf { MD5.matches(it) }?.lowercase(Locale.ROOT) ?: return@mapNotNull null
                ArchiveCandidate(itemId, name, size, md5)
            }
        }

        private fun normalized(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .lowercase(Locale.ROOT).replace(PUNCTUATION, " ").replace(WHITESPACE, " ").trim()

        private fun quote(text: String) = "\"${text.replace("\\", "\\\\").replace("\"", "\\\"")}\""
        private fun validItemId(value: String) = ITEM_ID.matches(value)
        private fun validFileName(value: String) = value.isNotBlank() && value.length <= 1024 &&
            !value.startsWith('/') && value.split('/').none { it == "." || it == ".." || it.isEmpty() }

        private fun location(itemId: String, fileName: String): HttpUrl = BASE.newBuilder()
            .addPathSegment("download").addPathSegment(itemId).apply { fileName.split('/').forEach { addPathSegment(it) } }.build()

        private fun parseDuration(value: String?): Double? = value?.split(':')?.let { parts ->
            var seconds = 0.0
            for (part in parts) seconds = seconds * 60 + (part.toDoubleOrNull() ?: return null)
            seconds.takeIf { it.isFinite() && it > 0 }
        }

        private fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
        private fun JsonObject.objectAt(key: String) = get(key) as? JsonObject
        private fun JsonObject.arrayAt(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()
        private fun JsonObject.texts(key: String): List<String> = when (val value = get(key)) {
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            is JsonPrimitive -> listOfNotNull(value.contentOrNull)
            else -> emptyList()
        }
    }
}
