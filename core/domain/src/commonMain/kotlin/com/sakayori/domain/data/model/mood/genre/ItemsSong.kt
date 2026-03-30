package com.sakayori.domain.data.model.mood.genre

import com.sakayori.domain.data.model.searchResult.songs.Artist

data class ItemsSong(
    val title: String,
    val artist: List<Artist>?,
    val videoId: String,
)
