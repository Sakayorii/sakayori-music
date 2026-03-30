package com.sakayori.kotlinytmusicscraper.pages

import com.sakayori.kotlinytmusicscraper.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
