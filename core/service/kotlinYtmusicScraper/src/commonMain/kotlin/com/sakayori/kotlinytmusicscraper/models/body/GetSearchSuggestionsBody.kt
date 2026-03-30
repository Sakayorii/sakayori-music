package com.sakayori.kotlinytmusicscraper.models.body

import com.sakayori.kotlinytmusicscraper.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetSearchSuggestionsBody(
    val context: Context,
    val input: String,
)
