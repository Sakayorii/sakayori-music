package com.sakayori.lyrics.models.response

import kotlinx.serialization.Serializable

@Serializable
data class BetterLyricsResponse(
    val ttml: String,
)
