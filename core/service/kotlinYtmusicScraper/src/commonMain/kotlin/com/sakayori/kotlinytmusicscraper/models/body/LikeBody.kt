package com.sakayori.kotlinytmusicscraper.models.body

import com.sakayori.kotlinytmusicscraper.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class LikeBody(
    val context: Context,
    val target: Target,
) {
    @Serializable
    data class Target(
        val videoId: String,
    )
}
