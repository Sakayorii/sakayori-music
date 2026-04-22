package com.sakayori.data.parser.search

import com.sakayori.domain.data.model.searchResult.songs.Album
import com.sakayori.domain.data.model.searchResult.songs.SongsResult
import com.sakayori.domain.data.model.searchResult.songs.Thumbnail
import com.sakayori.kotlinytmusicscraper.models.SongItem
import com.sakayori.kotlinytmusicscraper.pages.SearchResult

internal fun parseSearchSong(result: SearchResult): ArrayList<SongsResult> {
    val songsResult: ArrayList<SongsResult> = arrayListOf()
    result.items.forEach {
        val song = it as SongItem
        songsResult.add(
            SongsResult(
                album =
                    song.album?.let {
                        Album(
                            id = it.id,
                            name = it.name,
                        )
                    },
                artists =
                    song.artists.map { artistItem ->
                        com.sakayori.domain.data.model.searchResult.songs.Artist(
                            id = artistItem.id,
                            name = artistItem.name,
                        )
                    },
                category = "Song",
                duration = song.duration?.let { "${(it / 60).toString().padStart(2, '0')}:${(it % 60).toString().padStart(2, '0')}" } ?: "",
                durationSeconds = song.duration ?: 0,
                feedbackTokens = null,
                isExplicit = song.explicit,
                resultType = "Song",
                thumbnails = listOf(Thumbnail(544, Regex("([wh])120").replace(song.thumbnail, "$1544"), 544)),
                title = song.title,
                videoId = song.id,
                videoType = "Song",
                year = "",
            ),
        )
    }
    return songsResult
}
