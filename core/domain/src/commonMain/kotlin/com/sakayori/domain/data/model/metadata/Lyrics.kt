package com.sakayori.domain.data.model.metadata

import kotlinx.serialization.Serializable

@Serializable
data class Lyrics(
    val error: Boolean = false,
    val lines: List<Line>?,
    val syncType: String?,
    val SakayoriMusicLyrics: SakayoriMusicLyrics? = null,
)

@Serializable
data class SakayoriMusicLyrics(
    val id: String,
    val vote: Int,
)
