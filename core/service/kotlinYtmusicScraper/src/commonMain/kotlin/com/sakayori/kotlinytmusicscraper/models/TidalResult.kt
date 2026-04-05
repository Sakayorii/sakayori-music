package com.sakayori.kotlinytmusicscraper.models

import com.sakayori.kotlinytmusicscraper.models.response.TidalStreamResponse

data class TidalStreamResult(
    val stream: TidalStreamResponse,
    val bpm: Int?,
    val musicKey: String?,
    val keyScale: String?,
)

data class TidalMetadataResult(
    val bpm: Int?,
    val musicKey: String?,
    val keyScale: String?,
)
