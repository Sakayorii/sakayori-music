package com.sakayori.kotlinytmusicscraper.models.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SakayoriChartItem(
    @SerialName("id")
    val id: String?,
    @SerialName("title")
    val title: String?,
    @SerialName("description")
    val description: String?,
    @SerialName("thumbnail")
    val thumbnail: String?,
    @SerialName("category")
    val category: String?,
)
