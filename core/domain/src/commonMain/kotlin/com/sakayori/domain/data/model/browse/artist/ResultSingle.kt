package com.sakayori.domain.data.model.browse.artist

import com.sakayori.domain.data.model.searchResult.songs.Thumbnail
import com.sakayori.domain.data.type.HomeContentType

data class ResultSingle(
    val browseId: String,
    val thumbnails: List<Thumbnail>,
    val title: String,
    val year: String,
) : HomeContentType
