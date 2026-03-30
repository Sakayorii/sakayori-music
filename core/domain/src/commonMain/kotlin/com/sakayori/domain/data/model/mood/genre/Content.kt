package com.sakayori.domain.data.model.mood.genre

import com.sakayori.domain.data.model.searchResult.songs.Thumbnail
import com.sakayori.domain.data.type.HomeContentType

data class Content(
    val playlistBrowseId: String,
    val thumbnail: List<Thumbnail>?,
    val title: Title,
) : HomeContentType
