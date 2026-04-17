package com.sakayori.domain.data.type

data class ChartItem(
    val title: String,
    val description: String = "",
    val thumbnail: String? = null,
    val category: String = "top",
    val ytPlaylistId: String,
) : PlaylistType {
    override fun playlistType(): PlaylistType.Type = PlaylistType.Type.YOUTUBE_PLAYLIST
}
