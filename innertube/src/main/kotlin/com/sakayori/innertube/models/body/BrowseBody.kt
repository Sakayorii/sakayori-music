package com.sakayori.innertube.models.body

import com.sakayori.innertube.models.Context
import com.sakayori.innertube.models.Continuation
import kotlinx.serialization.Serializable

@Serializable
data class BrowseBody(
    val context: Context,
    val browseId: String?,
    val params: String?,
    val continuation: String?
)
