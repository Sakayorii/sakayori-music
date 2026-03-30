package com.sakayori.kotlinytmusicscraper.extractor

import com.sakayori.kotlinytmusicscraper.models.SongItem
import com.sakayori.kotlinytmusicscraper.models.response.DownloadProgress

expect class Extractor() {
    fun init()

    fun mergeAudioVideoDownload(filePath: String): DownloadProgress

    fun saveAudioWithThumbnail(
        filePath: String,
        track: SongItem,
    ): DownloadProgress

    fun newPipePlayer(videoId: String): List<Pair<Int, String>>
}
