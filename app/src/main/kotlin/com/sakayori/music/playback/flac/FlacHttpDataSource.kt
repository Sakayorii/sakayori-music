/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback.flac

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.sakayori.music.playback.PlaybackHttpClient
import com.sakayori.music.playback.RenditionUnavailableException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit

/**
 * A progressive native-FLAC transport for an explicitly supplied resource. It keeps Media3's
 * cancellable OkHttp transport, verifies ranges before body consumption, and replays the verified
 * STREAMINFO prefix into the extractor. No HEAD request or YouTube-specific range query is used.
 */
internal class FlacHttpDataSource(
    private val resource: FlacResource,
    private val client: PlaybackHttpClient,
    private val resolveLocation: suspend () -> FlacLocation,
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null
    private var openedUri: Uri? = null
    private var prefix = ByteArray(0)
    private var prefixPosition = 0
    private var remaining = 0L

    override fun addTransferListener(transferListener: TransferListener) {
        listeners += transferListener
        delegate?.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        try {
            openedUri = dataSpec.uri
            if (dataSpec.position > resource.contentLength) {
                throw DataSourceException(PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE)
            }
            if (dataSpec.httpMethod != DataSpec.HTTP_METHOD_GET) throw IOException("FLAC requires a GET request")
            prefix = ByteArray(0)
            prefixPosition = 0
            val available = resource.contentLength - dataSpec.position
            remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) available else minOf(dataSpec.length, available)
            if (remaining == 0L) return 0
            val requestedLength = if (dataSpec.position == 0L) maxOf(remaining, FlacStreamInfo.HEADER_LENGTH.toLong()) else remaining
            val location = runBlocking { resolveLocation() }
            val uri = location.url.toUri()
            if (!uri.scheme.equals("https", true) && !uri.scheme.equals("http", true)) throw IOException("FLAC resource requires an HTTP URL")
            val http = client.get().newBuilder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2500, TimeUnit.MILLISECONDS)
                .followSslRedirects(false)
                .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                try {
                    if (response.code == 412) throw RenditionUnavailableException("FLAC resource revision changed")
                    if (response.isSuccessful) {
                        validateFlacResponse(
                            resource, dataSpec.position, requestedLength, response.code,
                            response.header("ETag"), response.header("Content-Range"),
                            response.header("Content-Length")?.toLongOrNull(), response.header("Content-Encoding"),
                        )
                    }
                    response
                } catch (error: IOException) {
                    response.close()
                    throw error
                }
            }.build()
            val source = OkHttpDataSource.Factory(http).createDataSource()
            delegate = source
            listeners.forEach(source::addTransferListener)
            val headers = (dataSpec.httpRequestHeaders + location.requestHeaders).filterKeys { name ->
                !name.equals("If-Range", true) && !name.equals("If-Match", true) &&
                    !name.equals("Range", true) && !name.equals("Accept-Encoding", true)
            } + mapOf("If-Match" to resource.etag, "Accept-Encoding" to "identity")
            source.open(dataSpec.buildUpon().setUri(uri).setLength(requestedLength).setHttpRequestHeaders(headers).build())
            if (dataSpec.position == 0L) {
                prefix = ByteArray(FlacStreamInfo.HEADER_LENGTH)
                var offset = 0
                while (offset < prefix.size) {
                    val read = source.read(prefix, offset, prefix.size - offset)
                    if (read <= 0) throw EOFException("Truncated FLAC STREAMINFO")
                    offset += read
                }
                if (FlacStreamInfo.parse(prefix) != resource.streamInfo) {
                    throw RenditionUnavailableException("FLAC STREAMINFO does not match the selected resource")
                }
            }
            return remaining
        } catch (timeout: TimeoutCancellationException) {
            throw LosslessStreamException(IOException("FLAC resource renewal timed out", timeout))
        } catch (cancelled: CancellationException) {
            throw InterruptedIOException("FLAC request cancelled").apply { initCause(cancelled) }
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("FLAC request interrupted").apply { initCause(interrupted) }
        } catch (error: IOException) {
            throw LosslessStreamException(error)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try {
        readInternal(buffer, offset, length)
    } catch (error: IOException) {
        throw LosslessStreamException(error)
    }

    private fun readInternal(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val count = minOf(length.toLong(), remaining).toInt()
        val read = if (prefixPosition < prefix.size) {
            minOf(count, prefix.size - prefixPosition).also {
                prefix.copyInto(buffer, offset, prefixPosition, prefixPosition + it)
                prefixPosition += it
            }
        } else {
            val source = delegate ?: throw IOException("FLAC source is not open")
            source.read(buffer, offset, count).also {
                if (it == C.RESULT_END_OF_INPUT) throw EOFException("FLAC ended before its pinned content length")
            }
        }
        remaining -= read
        return read
    }

    override fun getUri(): Uri? = delegate?.uri ?: openedUri

    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()

    override fun close() {
        val source = delegate
        delegate = null
        openedUri = null
        prefix = ByteArray(0)
        remaining = 0L
        source?.close()
    }
}
