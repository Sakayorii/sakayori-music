/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.player

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.media3.common.MediaItem
import coil3.asImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
class PlayerArtworkPresentationTest {
    private val image = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).asImage()
    private val a = item("a", "https://example.test/a.jpg")
    private val b = item("b", "https://example.test/missing.jpg")
    private val c = item("c", "https://example.test/c.jpg")

    @Test
    fun `terminal image failure advances the displayed track and carousel window`() {
        val first = ready(a, listOf(a, b), 0)
        val next = updatePlayerArtworkPresentation(first, MediaItemsData(listOf(a, b, c), 1), b,
            b.mediaMetadata.artworkUri.toString(), null, imageUnavailable = true)

        assertEquals("b", next?.current?.mediaId)
        assertEquals(listOf(a, b, c), next?.mediaItems?.items)
        assertEquals(1, next?.mediaItems?.currentIndex)
        assertNull(next?.imageFor(b))
        assertSame(image, next?.imageFor(a))
    }

    @Test
    fun `first track with no artwork still has a committed display identity`() {
        val missing = item("missing", null)
        val next = updatePlayerArtworkPresentation(null, MediaItemsData(listOf(missing, a), 0),
            missing, null, null, imageUnavailable = true)

        assertEquals("missing", next?.current?.mediaId)
        assertEquals(0, next?.mediaItems?.currentIndex)
        assertNull(next?.imageFor(missing))
    }

    @Test
    fun `valid to missing to valid to missing cannot resurrect the last valid cover`() {
        val first = ready(a, listOf(a, b), 0)
        val missing = updatePlayerArtworkPresentation(first, MediaItemsData(listOf(a, b, c), 1), b,
            b.mediaMetadata.artworkUri.toString(), null, imageUnavailable = true)
        val valid = updatePlayerArtworkPresentation(missing, MediaItemsData(listOf(b, c), 1), c,
            c.mediaMetadata.artworkUri.toString(), image, imageUnavailable = false)
        val back = updatePlayerArtworkPresentation(valid, MediaItemsData(listOf(a, b, c), 1), b,
            b.mediaMetadata.artworkUri.toString(), null, imageUnavailable = true)

        assertEquals("b", back?.current?.mediaId)
        assertNull(back?.imageFor(b))
        assertSame(image, back?.imageFor(c))
    }

    @Test
    fun `an in-flight load retains the decoded incumbent until a result arrives`() {
        val first = ready(a, listOf(a, b), 0)
        val pending = updatePlayerArtworkPresentation(first, MediaItemsData(listOf(a, b, c), 1), b,
            b.mediaMetadata.artworkUri.toString(), null, imageUnavailable = false)

        assertSame(first, pending)
    }

    @Test
    fun `a decoded replacement retains the outgoing image only on its original tile`() {
        val first = ready(a, listOf(a, b), 0)
        val next = updatePlayerArtworkPresentation(first, MediaItemsData(listOf(a, b, c), 1), b,
            b.mediaMetadata.artworkUri.toString(), image, imageUnavailable = false)

        assertEquals("b", next?.current?.mediaId)
        assertSame(image, next?.imageFor(a))
        assertSame(image, next?.imageFor(b))
        assertNull(next?.imageFor(c))
    }

    @Test
    fun `same selected track retains its outgoing motion state when the window grows`() {
        val first = ready(a, listOf(a, b), 0)
        val second = updatePlayerArtworkPresentation(first, MediaItemsData(listOf(a, b), 1), b,
            b.mediaMetadata.artworkUri.toString(), image, imageUnavailable = false)
        val grown = updatePlayerArtworkPresentation(second, MediaItemsData(listOf(a, b, c), 1), b,
            b.mediaMetadata.artworkUri.toString(), image, imageUnavailable = false)

        assertEquals(second?.previous, grown?.previous)
        assertEquals(1, grown?.mediaItems?.currentIndex)
    }

    private fun ready(item: MediaItem, items: List<MediaItem>, index: Int) =
        updatePlayerArtworkPresentation(null, MediaItemsData(items, index), item,
            item.mediaMetadata.artworkUri?.toString(), image, imageUnavailable = false)

    private fun item(id: String, uri: String?) = MediaItem.Builder().setMediaId(id).setUri(id)
        .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder()
            .setArtworkUri(uri?.let(Uri::parse)).build()).build()
}
