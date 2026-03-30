package com.sakayori.kotlinytmusicscraper.extractor

import com.sakayori.kotlinytmusicscraper.models.SongItem
import com.sakayori.kotlinytmusicscraper.models.response.DownloadProgress
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfo

actual class Extractor {
    private var newPipeDownloader = NewPipeDownloaderImpl(proxy = null)

    actual fun init() {
        NewPipe.init(newPipeDownloader)
    }

    actual fun newPipePlayer(videoId: String): List<Pair<Int, String>> {
        val streamInfo = StreamInfo.getInfo(NewPipe.getService(0), "https://www.youtube.com/watch?v=$videoId")
        val streamsList = streamInfo.audioStreams + streamInfo.videoStreams + streamInfo.videoOnlyStreams
        val temp =
            streamsList
                .mapNotNull {
                    (it.itagItem?.id ?: return@mapNotNull null) to it.content
                }.toMutableList()
        temp.add(96 to (streamInfo.dashMpdUrl.takeIf { !it.isNullOrEmpty() } ?: streamInfo.hlsUrl))
        return temp
    }

    actual fun mergeAudioVideoDownload(filePath: String): DownloadProgress = DownloadProgress.failed("Not supported on JVM")

    actual fun saveAudioWithThumbnail(
        filePath: String,
        track: SongItem,
    ): DownloadProgress = DownloadProgress.AUDIO_DONE
}
