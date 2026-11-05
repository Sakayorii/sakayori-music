package com.sakayori.innertube

import com.sakayori.innertube.models.YouTubeClient
import com.sakayori.innertube.models.response.PlayerResponse
import io.ktor.http.URLBuilder
import io.ktor.http.parseQueryString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.CancellableCall
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ParsingException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Proxy
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException

private class NewPipeDownloaderImpl : Downloader() {
    private fun normalizeResponseBody(
        url: String,
        body: String?,
    ): String? {
        if (!url.contains("returnyoutubedislikeapi.com", ignoreCase = true)) {
            return body
        }

        val trimmed = body?.trimStart().orEmpty()
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return body
        }

        return "{\"likes\":0,\"dislikes\":0,\"viewCount\":0}"
    }

    private data class ProxySettings(val proxy: Proxy?, val credentials: String?)
    private var settings: ProxySettings? = null
    private var client: OkHttpClient? = null

    @Synchronized
    private fun httpClient(): OkHttpClient {
        val next = ProxySettings(YouTube.proxy, YouTube.proxyAuth)
        if (settings == next) client?.let { return it }
        return OkHttpClient.Builder()
            .proxy(next.proxy)
            .proxyAuthenticator { _, response ->
                val credentials = next.credentials ?: return@proxyAuthenticator null
                if (response.request.header("Proxy-Authorization") == credentials) null
                else response.request.newBuilder().header("Proxy-Authorization", credentials).build()
            }.build().also {
                settings = next
                client = it
            }
    }

    // Matches SakayoriExtractor ab382ba1dfac01a999ed859a9f2a9af93454fa8e. Like
    // Media3's OkHttp bridge, wait interruptibly instead of executing on the caller's thread.
    // Complete only after body conversion so interruption also cancels an in-progress body read.
    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        if (Thread.currentThread().isInterrupted) throw InterruptedIOException("Extractor request interrupted")
        val result = CompletableFuture<Response>()
        val pending = executeAsync(request, object : Downloader.AsyncCallback {
            override fun onSuccess(response: Response) {
                result.complete(response)
            }

            override fun onError(error: Exception) {
                result.completeExceptionally(error)
            }
        })
        return try {
            result.get()
        } catch (interrupted: InterruptedException) {
            pending.cancel()
            result.cancel(false)
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Extractor request interrupted").apply { initCause(interrupted) }
        } catch (failed: ExecutionException) {
            when (val cause = failed.cause) {
                is IOException -> throw cause
                is ReCaptchaException -> throw cause
                is RuntimeException -> throw cause
                else -> throw IOException("Extractor request failed", cause)
            }
        }
    }

    private fun newCall(request: Request): Call {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()
        // Downloader.post explicitly permits a null payload; OkHttp requires an empty body.
        val body = dataToSend?.toRequestBody() ?: if (httpMethod == "POST") ByteArray(0).toRequestBody() else null

        val requestBuilder =
            okhttp3.Request
                .Builder()
                .method(httpMethod, body)
                .url(url)
                .addHeader("User-Agent", YouTubeClient.USER_AGENT_WEB)

        headers.forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        val base = httpClient()
        val transport = if (base.followRedirects == request.followRedirects()) base else
            base.newBuilder().followRedirects(request.followRedirects()).build()
        return transport.newCall(requestBuilder.build())
    }

    private fun toExtractorResponse(response: okhttp3.Response, requestedUrl: String): Response {
        if (response.code == 429) {
            throw ReCaptchaException("reCaptcha Challenge requested", requestedUrl)
        }

        val latestUrl = response.request.url.toString()
        val responseBodyToReturn = normalizeResponseBody(latestUrl, response.body.string())
        return Response(
            response.code,
            response.message,
            response.headers.toMultimap(),
            responseBodyToReturn,
            responseBodyToReturn?.toByteArray(Charsets.UTF_8) ?: ByteArray(0),
            latestUrl,
        )
    }

    @Throws(IOException::class, ReCaptchaException::class)
    override fun executeAsync(request: Request, callback: Downloader.AsyncCallback?): CancellableCall {
        val call = newCall(request)
        val pending = CancellableCall(call)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                try {
                    callback?.onError(e)
                } finally {
                    pending.setFinished()
                }
            }

            override fun onResponse(call: Call, response: okhttp3.Response) {
                try {
                    val result = response.use {
                        if (call.isCanceled()) throw IOException("Extractor request cancelled")
                        toExtractorResponse(it, request.url())
                    }
                    if (call.isCanceled()) throw IOException("Extractor request cancelled")
                    callback?.onSuccess(result)
                } catch (error: Exception) {
                    callback?.onError(error)
                } finally {
                    pending.setFinished()
                }
            }
        })
        return pending
    }
}

object NewPipeUtils {
    init {
        NewPipe.init(NewPipeDownloaderImpl())
    }

    internal fun ensureInitialized() = Unit

    fun getSignatureTimestamp(videoId: String): Result<Int> = runCatching {
        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
    }

    fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): Result<String> =
        runCatching {
            val url =
                format.url ?: format.signatureCipher?.let { signatureCipher ->
                    val params = parseQueryString(signatureCipher)
                    val obfuscatedSignature =
                        params["s"]
                            ?: throw ParsingException("Could not parse cipher signature")
                    val signatureParam =
                        params["sp"]
                            ?: throw ParsingException("Could not parse cipher signature parameter")
                    val url =
                        params["url"]?.let { URLBuilder(it) }
                            ?: throw ParsingException("Could not parse cipher url")
                    url.parameters[signatureParam] =
                        YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                            videoId,
                            obfuscatedSignature,
                        )
                    url.toString()
                } ?: throw ParsingException("Could not find format url")

            return@runCatching YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                videoId,
                url,
            )
        }
}

object NewPipeExtractor {
    init {
        // Playback uses this facade directly; merely declaring NewPipeUtils does not run its init.
        NewPipeUtils.ensureInitialized()
    }

    fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        return try {
            val streamInfo =
                StreamInfo.getInfo(
                    NewPipe.getService(0),
                    "https://www.youtube.com/watch?v=$videoId",
                )
            val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
            streamsList.mapNotNull {
                (it.itagItem?.id ?: return@mapNotNull null) to it.content
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> = runCatching {
        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
    }

    fun getStreamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? {
        return try {
            val url = format.url ?: format.signatureCipher?.let { signatureCipher ->
                val params = parseQueryString(signatureCipher)
                val obfuscatedSignature = params["s"]
                    ?: throw ParsingException("Could not parse cipher signature")
                val signatureParam = params["sp"]
                    ?: throw ParsingException("Could not parse cipher signature parameter")
                val url = params["url"]?.let { URLBuilder(it) }
                    ?: throw ParsingException("Could not parse cipher url")
                url.parameters[signatureParam] =
                    YoutubeJavaScriptPlayerManager.deobfuscateSignature(
                        videoId,
                        obfuscatedSignature
                    )
                url.toString()
            } ?: throw ParsingException("Could not find format url")

            YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(
                videoId,
                url,
            )
        } catch (e: Exception) {
            null
        }
    }
}
