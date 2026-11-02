package com.sakayori.music.playback

import android.app.Application
import com.sakayori.music.playback.flac.FlacPlaybackCapabilities
import com.sakayori.music.playback.flac.FlacPlaybackCapability
import com.sakayori.music.playback.flac.FlacStreamInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class FlacPlaybackCapabilitiesTest {
    @Test
    @Config(sdk = [26])
    fun `API 26 returns an explicit capability instead of assuming a FLAC decoder`() {
        assertEquals(
            FlacPlaybackCapability.ANDROID_26_REQUIRES_DECODER,
            FlacPlaybackCapabilities.inspect(FlacStreamInfo(44_100, 2, 16, 44_100)),
        )
    }

    @Test
    @Config(sdk = [28])
    fun `high resolution is outside the initial CD quality profile`() {
        assertEquals(
            FlacPlaybackCapability.PROFILE_NOT_SUPPORTED,
            FlacPlaybackCapabilities.inspect(FlacStreamInfo(96_000, 2, 24, 96_000)),
        )
    }
}
