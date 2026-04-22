package com.sakayori.data.parser.search

import com.sakayori.domain.data.model.searchResult.songs.Artist
import com.sakayori.domain.data.model.searchResult.songs.Thumbnail
import com.sakayori.domain.data.model.searchResult.videos.VideosResult
import com.sakayori.kotlinytmusicscraper.models.SongItem
import com.sakayori.kotlinytmusicscraper.pages.SearchResult

internal fun parseSearchVideo(result: SearchResult): ArrayList<VideosResult> {
    val songsResult: ArrayList<VideosResult> = arrayListOf()
    result.items.forEach {
        val song = it as SongItem
        songsResult.add(
            VideosResult(
                artists =
                    song.artists.map { artistItem ->
                        Artist(
                            id = artistItem.id,
                            name = artistItem.name,
                        )
                    },
                category = "Video",
                duration = song.duration?.let { "${(it / 60).toString().padStart(2, '0')}:${(it % 60).toString().padStart(2, '0')}" } ?: "",
                durationSeconds = song.duration ?: 0,
                resultType = "Video",
                thumbnails = listOf(Thumbnail(306, Regex("([wh])120").replace(song.thumbnail, "$1544"), 544)),
                title = song.title,
                videoId = song.id,
                videoType = "Video",
                views = null,
                year = "",
            ),
        )
    }
    return songsResult
}
