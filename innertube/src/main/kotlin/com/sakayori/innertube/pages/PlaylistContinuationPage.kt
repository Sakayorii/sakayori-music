package com.sakayori.innertube.pages

import com.sakayori.innertube.models.SongItem

data class PlaylistContinuationPage(
    val songs: List<SongItem>,
    val continuation: String?,
)
