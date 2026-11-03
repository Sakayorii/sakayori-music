/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback.flac

import com.sakayori.music.playback.PlaybackHttpClient
import com.sakayori.music.playback.readCancellable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import okhttp3.OkHttpClient
import okio.Buffer
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

internal class LosslessApiUnavailable(val status: Int, val retryAfterMs: Long? = null) :
    IOException("Lossless API unavailable")

/** Small API/STREAMINFO reads. Cancellation closes the actual OkHttp call, including body reads. */
@Singleton
class LosslessApiHttp @Inject constructor(private val clients: PlaybackHttpClient) {
    private val json = Json { ignoreUnknownKeys = true }
    private var parentClient: OkHttpClient? = null
    private var apiClient: OkHttpClient? = null

    internal suspend fun objectFrom(url: HttpUrl): JsonObject = request(
        Request.Builder().url(url).header("Accept", "application/json").build(),
    ) { response ->
        if (response.code != 200) throw unavailable(response)
        val body = response.body ?: throw IOException("Missing lossless API response")
        if (body.contentLength() > MAX_JSON_BYTES) throw IOException("Lossless API response too large")
        val output = Buffer()
        val input = body.source()
        while (output.size <= MAX_JSON_BYTES) {
            if (input.read(output, minOf(8_192L, MAX_JSON_BYTES + 1 - output.size)) == -1L) break
        }
        if (output.size > MAX_JSON_BYTES) throw IOException("Lossless API response too large")
        json.parseToJsonElement(output.readUtf8()) as? JsonObject ?: throw IOException("Expected lossless API object")
    }

    internal suspend fun probe(
        mediaId: String,
        sourceId: String,
        sourceLocator: String,
        resourceId: String,
        location: FlacLocation,
        expectedDurationMs: Long,
    ): FlacResource? {
        val url = validatedMediaUrl(location.url) ?: return null
        return request(Request.Builder().url(url)
            .header("Range", "bytes=0-41").header("Accept-Encoding", "identity")
            .apply { location.requestHeaders.forEach { (name, value) -> header(name, value) } }.build()) { response ->
            if (response.code >= 500 || response.code == 401 || response.code == 403 || response.code == 429) throw unavailable(response)
            if (response.code != 206) return@request null
            val range = response.header("Content-Range")?.let(PREFIX_RANGE::matchEntire) ?: return@request null
            val size = range.groupValues[1].toLongOrNull()?.takeIf { it >= FlacStreamInfo.HEADER_LENGTH } ?: return@request null
            val etag = response.header("ETag")?.takeIf(::isStrongEtag) ?: return@request null
            val encoding = response.header("Content-Encoding")
            if (encoding != null && !encoding.equals("identity", true)) return@request null
            val bytes = ByteArray(FlacStreamInfo.HEADER_LENGTH)
            val input = response.body?.byteStream() ?: return@request null
            var offset = 0
            while (offset < bytes.size) {
                val read = input.read(bytes, offset, bytes.size - offset)
                if (read <= 0) return@request null
                offset += read
            }
            val info = FlacStreamInfo.parse(bytes)
            val durationUs = info.durationUs ?: return@request null
            if (expectedDurationMs <= 0 || kotlin.math.abs(durationUs / 1_000 - expectedDurationMs) > 1_500 ||
                FlacPlaybackCapabilities.inspect(info) != FlacPlaybackCapability.SUPPORTED
            ) return@request null
            FlacResource(mediaId, sourceId, resourceId, etag, size, info, sourceLocator = sourceLocator)
        }
    }

    private suspend fun <T> request(request: Request, read: (Response) -> T): T {
        val parent = clients.get()
        val client = synchronized(this) {
            if (parentClient !== parent) {
                parentClient = parent
                apiClient = parent.newBuilder()
                    .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS)
                    .followSslRedirects(false).callTimeout(4, TimeUnit.SECONDS).build()
            }
            checkNotNull(apiClient)
        }
        return client.readCancellable(request, read)
    }

    private fun unavailable(response: Response): LosslessApiUnavailable {
        val retry = response.header("Retry-After")?.toLongOrNull()?.takeIf { it >= 0 }
            ?.coerceAtMost(600)?.times(1_000)
        return LosslessApiUnavailable(response.code, retry)
    }

    internal companion object {
        private const val MAX_JSON_BYTES = 512 * 1024L
        private val PREFIX_RANGE = Regex("bytes 0-41/(\\d+)", RegexOption.IGNORE_CASE)

        internal fun validatedMediaUrl(value: String): HttpUrl? = value.takeIf { it.length <= 8_192 }?.toHttpUrlOrNull()?.takeIf {
            it.isHttps && it.username.isEmpty() && it.password.isEmpty()
        }

        internal fun validatedApiBase(value: String): HttpUrl? = value.trim().takeIf { it.length <= 2_048 }?.let(::validatedMediaUrl)?.takeIf {
            it.query == null && it.fragment == null
        }
    }
}

internal fun JsonObject.stringAt(key: String): String? = (get(key) as? JsonPrimitive)?.contentOrNull
internal fun JsonObject.objectAt(key: String): JsonObject? = get(key) as? JsonObject
internal fun JsonObject.arrayAt(key: String): List<JsonElement> = (get(key) as? JsonArray).orEmpty()
internal fun JsonObject.booleanAt(key: String): Boolean? = (get(key) as? JsonPrimitive)?.booleanOrNull
