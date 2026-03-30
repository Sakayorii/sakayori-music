package com.sakayori.kotlinytmusicscraper.models.body

import com.sakayori.kotlinytmusicscraper.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class CreatePlaylistBody(
    val context: Context,
    val title: String,
    val description: String? = "Created by SakayoriMusic",
    val privacyStatus: String = PrivacyStatus.PRIVATE,
    val videoIds: List<String>? = null,
) {
    object PrivacyStatus {
        const val PRIVATE = "PRIVATE"
        const val PUBLIC = "PUBLIC"
        const val UNLISTED = "UNLISTED"
    }
}
