/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.utils

import android.app.Application
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.asImage
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.sakayori.music.discord.resolveDiscordArtworkUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class DiscordArtworkResolverTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val missing = "https://i.ytimg.com/vi/WmCEwMT655o/maxresdefault.jpg"
    private val available = "https://i.ytimg.com/vi/WmCEwMT655o/sddefault.jpg"

    @Test
    fun `external presence uses the image pipeline's available URL for the same video`() = runBlocking {
        val resolved = resolveDiscordArtworkUrl(missing) {
            assertEquals(missing, it)
            SuccessResult(Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImage(),
                ImageRequest.Builder(context).data(available).build())
        }
        assertEquals(available, resolved)
    }

    @Test
    fun `unavailable artwork keeps text-only presence without another track's image`() = runBlocking {
        assertNull(resolveDiscordArtworkUrl(missing) {
            ErrorResult(null, ImageRequest.Builder(context).data(it).build(), IllegalStateException("missing"))
        })
    }

    @Test
    fun `non-YouTube custom assets keep their existing resolution path`() = runBlocking {
        val url = "https://example.test/custom.jpg"
        assertEquals(url, resolveDiscordArtworkUrl(url) { throw AssertionError("Unexpected image request") })
    }

    @Test
    fun `cancelled image work cannot become a presence update`() = runBlocking {
        val cancellation = CancellationException("new activity")
        try {
            resolveDiscordArtworkUrl(missing) { throw cancellation }
            throw AssertionError("Expected cancellation")
        } catch (failure: CancellationException) {
            assertSame(cancellation, failure)
        }
    }
}
