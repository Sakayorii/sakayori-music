package com.sakayori.domain.data.model.searchResult.albums

import com.sakayori.domain.data.model.searchResult.songs.Artist
import com.sakayori.domain.data.model.searchResult.songs.Thumbnail
import com.sakayori.domain.data.type.PlaylistType
import com.sakayori.domain.data.type.SearchResultType

data class AlbumsResult(
    val artists: List<Artist>,
    val browseId: String,
    val category: String,
    val duration: Any,
    val isExplicit: Boolean,
    val resultType: String,
    val thumbnails: List<Thumbnail>,
    val title: String,
    val type: String,
    val year: String,
) : PlaylistType,
    SearchResultType {
    override fun objectType(): SearchResultType.Type = SearchResultType.Type.ALBUM

    override fun playlistType(): PlaylistType.Type = PlaylistType.Type.ALBUM
}
