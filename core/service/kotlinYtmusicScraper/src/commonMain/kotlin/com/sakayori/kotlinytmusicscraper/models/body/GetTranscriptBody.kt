package com.sakayori.kotlinytmusicscraper.models.body

import com.sakayori.kotlinytmusicscraper.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class GetTranscriptBody(
    val context: Context,
    val params: String,
)
