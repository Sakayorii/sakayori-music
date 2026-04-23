@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.sakayori.music.expect.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.UIKitView
import com.sakayori.domain.data.model.metadata.Lyrics
import com.sakayori.domain.data.model.streams.TimeLine
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.play
import platform.AVKit.AVPlayerViewController
import platform.Foundation.NSURL

@Composable
actual fun MediaPlayerView(
    url: String,
    modifier: Modifier,
) {
    UIKitView(
        factory = {
            val vc = AVPlayerViewController()
            val nsUrl = NSURL.URLWithString(url)
            if (nsUrl != null) {
                val item = AVPlayerItem.playerItemWithURL(nsUrl)
                vc.player = AVPlayer.playerWithPlayerItem(item)
                vc.player?.play()
            }
            vc.view
        },
        modifier = modifier,
    )
}

@Composable
actual fun MediaPlayerViewWithSubtitle(
    modifier: Modifier,
    playerName: String,
    shouldPip: Boolean,
    shouldShowSubtitle: Boolean,
    shouldScaleDownSubtitle: Boolean,
    isInPipMode: Boolean,
    timelineState: TimeLine,
    lyricsData: Lyrics?,
    translatedLyricsData: Lyrics?,
    mainTextStyle: TextStyle,
    translatedTextStyle: TextStyle,
) {
}
