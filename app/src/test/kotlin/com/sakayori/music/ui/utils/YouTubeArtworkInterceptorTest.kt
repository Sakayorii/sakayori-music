/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.utils

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import coil3.asImage
import coil3.ImageLoader
import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.network.NetworkResponse
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import com.sakayori.music.utils.YouTubeArtworkInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class YouTubeArtworkInterceptorTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImage()
    private val base = "https://i.ytimg.com/vi/WmCEwMT655o/"
    private val maxres = base + "maxresdefault.jpg"
    private val standard = base + "sddefault.jpg"
    private val high = base + "hqdefault.jpg"

    @Test
    fun `legacy unavailable maxres loads the highest available cover for the same video`() = runBlocking {
        val requests = mutableListOf<String>()
        val interceptor = YouTubeArtworkInterceptor()
        val result = interceptor.intercept(chain(maxres, requests = requests) { request ->
            if (request.data == standard) SuccessResult(image, request) else notFound(request)
        })

        assertTrue(result is SuccessResult)
        assertEquals(standard, result.request.data)
        assertEquals(listOf(maxres, standard), requests)
    }

    @Test
    fun `missing maxres and standard fall back to an available high thumbnail`() = runBlocking {
        val result = YouTubeArtworkInterceptor().intercept(chain(maxres) { request ->
            if (request.data == high) SuccessResult(image, request) else notFound(request)
        })
        assertTrue(result is SuccessResult)
        assertEquals(high, result.request.data)
    }

    @Test
    fun `large artwork keeps available maxres quality while stored source URL stays unchanged`() = runBlocking {
        val requests = mutableListOf<String>()
        val request = ImageRequest.Builder(context).data(high).build()
        val result = YouTubeArtworkInterceptor().intercept(FakeChain(request, Size(1080, 1080), requests) {
            SuccessResult(image, it)
        })
        assertEquals(high, request.data)
        assertEquals(maxres, result.request.data)
        assertEquals(listOf(maxres), requests)
    }

    @Test
    fun `small images and unrelated custom URLs retain their original request`() = runBlocking {
        for (url in listOf(high, "content://custom/art", "https://example.test/maxresdefault.jpg")) {
            val requests = mutableListOf<String>()
            val result = YouTubeArtworkInterceptor().intercept(chain(url, Size(120, 120), requests) {
                SuccessResult(image, it)
            })
            assertEquals(url, result.request.data)
            assertEquals(listOf(url), requests)
        }
    }

    @Test
    fun `authorization failures are returned without trying alternate assets`() = runBlocking {
        val requests = mutableListOf<String>()
        val result = YouTubeArtworkInterceptor().intercept(chain(maxres, requests = requests) {
            ErrorResult(null, it, HttpException(NetworkResponse(code = 403)))
        })
        assertTrue(result is ErrorResult)
        assertEquals(listOf(maxres), requests)
    }

    @Test
    fun `a successful fallback is reused without repeatedly requesting a known missing variant`() = runBlocking {
        val interceptor = YouTubeArtworkInterceptor()
        val requests = mutableListOf<String>()
        repeat(2) {
            val result = interceptor.intercept(chain(maxres, requests = requests) { request ->
                if (request.data == standard) SuccessResult(image, request) else notFound(request)
            })
            assertTrue(result is SuccessResult)
        }
        assertEquals(listOf(maxres, standard, standard), requests)
    }

    @Test
    fun `cancellation does not become a retry or publish a resolved asset`() = runBlocking {
        val cancellation = CancellationException("obsolete image")
        try {
            YouTubeArtworkInterceptor().intercept(chain(maxres) { throw cancellation })
            throw AssertionError("Expected cancellation")
        } catch (failure: CancellationException) {
            assertSame(cancellation, failure)
        }
    }

    @Test
    fun `every missing candidate terminates with an error instead of looping`() = runBlocking {
        val requests = mutableListOf<String>()
        val result = YouTubeArtworkInterceptor().intercept(chain(maxres, requests = requests, respond = ::notFound))
        assertTrue(result is ErrorResult)
        assertEquals(requests.size, requests.distinct().size)
        assertTrue(requests.size <= 6)
    }

    @Test
    fun `actual Coil network and decode pipeline returns available URI for legacy metadata`() = runBlocking {
        val bytes = ByteArrayOutputStream().also {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val available = request.url.toString() == standard
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(if (available) 200 else 404).message(if (available) "OK" else "Not Found")
                .body((if (available) bytes else byteArrayOf()).toResponseBody("image/png".toMediaType())).build()
        }.build()
        val loader = ImageLoader.Builder(context).components {
            add(YouTubeArtworkInterceptor())
            add(OkHttpNetworkFetcherFactory(callFactory = client))
        }.build()
        try {
            val result = loader.execute(ImageRequest.Builder(context).data(Uri.parse(maxres))
                .size(1080, 1080).allowHardware(false).build())
            assertTrue(result is SuccessResult)
            assertEquals(standard, result.request.data.toString())
        } finally {
            loader.shutdown()
        }
    }

    private fun notFound(request: ImageRequest) = ErrorResult(null, request, HttpException(NetworkResponse(code = 404)))

    private fun chain(
        url: String,
        size: Size = Size(1080, 1080),
        requests: MutableList<String> = mutableListOf(),
        respond: suspend (ImageRequest) -> ImageResult,
    ): Interceptor.Chain = FakeChain(ImageRequest.Builder(context).data(url).build(), size, requests, respond)

    private class FakeChain(
        override val request: ImageRequest,
        override val size: Size,
        private val requests: MutableList<String>,
        private val respond: suspend (ImageRequest) -> ImageResult,
    ) : Interceptor.Chain {
        override fun withRequest(request: ImageRequest) = FakeChain(request, size, requests, respond)
        override fun withSize(size: Size) = FakeChain(request, size, requests, respond)
        override suspend fun proceed(): ImageResult {
            requests += request.data.toString()
            return respond(request)
        }
    }
}
