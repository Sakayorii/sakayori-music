/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.utils

import android.app.Application
import androidx.media3.common.MediaItem
import com.sakayori.innertube.models.SongItem
import com.sakayori.music.discord.DiscordActivityBuilder
import com.sakayori.music.db.entities.Song
import com.sakayori.music.extensions.toMediaItem
import com.sakayori.music.models.toMediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class YouTubeArtworkAvailabilityTest {
    private val supplied = "https://i.ytimg.com/vi/WmCEwMT655o/hqdefault.jpg?sqp=source-size&rs=source-signature"

    @Test
    fun `requested display size does not replace a provider supplied thumbnail with an unavailable variant`() {
        assertEquals(supplied, supplied.resize(1080, 1080))
    }

    @Test
    fun `row sizing and current metadata retain the available video thumbnail`() {
        val item = SongItem("WmCEwMT655o", "10-yuri.ogg", emptyList(), thumbnail = supplied)
        val metadata = item.toMediaMetadata()

        assertEquals(supplied, supplied.resize(544, 544))
        assertEquals(supplied, metadata.thumbnailUrl)
        assertEquals(supplied, metadata.toSongEntity().thumbnailUrl)
    }

    @Test
    fun `native media and Discord assets receive the same source supplied image`() {
        val metadata = SongItem("WmCEwMT655o", "10-yuri.ogg", emptyList(), thumbnail = supplied).toMediaMetadata()
        val mediaItem: MediaItem = metadata.toMediaItem()
        val song = Song(metadata.toSongEntity(), emptyList())
        val activity = DiscordActivityBuilder.build(song, "Sayori", null, null,
            "10-yuri.ogg", 0L, null, advancedMode = false)

        assertEquals(supplied, mediaItem.mediaMetadata.artworkUri.toString())
        assertEquals(supplied, activity.largeImage)
    }

    @Test
    fun `already available maxres and custom local artwork are preserved`() {
        for (url in listOf("https://i.ytimg.com/vi/abcdefghijk/maxresdefault.jpg", "content://artwork/custom")) {
            assertEquals(url, url.resize(1080, 1080))
        }
    }
}
