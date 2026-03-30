package com.sakayori.domain.data.model.home.chart

import com.sakayori.domain.data.model.browse.artist.ResultPlaylist

data class ChartItemPlaylist(
    val title: String,
    val playlists: List<ResultPlaylist>,
)
