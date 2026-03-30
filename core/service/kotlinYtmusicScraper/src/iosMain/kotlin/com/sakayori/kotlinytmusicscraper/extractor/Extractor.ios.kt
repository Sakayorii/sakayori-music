package com.sakayori.kotlinytmusicscraper.extractor

import com.sakayori.kotlinytmusicscraper.models.SongItem
import com.sakayori.kotlinytmusicscraper.models.response.DownloadProgress

actual class Extractor {
    actual fun init() {
    }

    actual fun newPipePlayer(videoId: String): List<Pair<Int, String>> = emptyList()

    actual fun mergeAudioVideoDownload(filePath: String): DownloadProgress = DownloadProgress.failed("Not supported on iOS")

    actual fun saveAudioWithThumbnail(
        filePath: String,
        track: SongItem,
    ): DownloadProgress = DownloadProgress.failed("Not supported on iOS")
}
