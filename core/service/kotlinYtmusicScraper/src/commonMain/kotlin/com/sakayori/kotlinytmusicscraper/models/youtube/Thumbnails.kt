package com.sakayori.kotlinytmusicscraper.models.youtube

import com.sakayori.kotlinytmusicscraper.models.Thumbnail
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Thumbnails(
    @SerialName("thumbnails")
    val thumbnails: List<Thumbnail>? = null,
)
