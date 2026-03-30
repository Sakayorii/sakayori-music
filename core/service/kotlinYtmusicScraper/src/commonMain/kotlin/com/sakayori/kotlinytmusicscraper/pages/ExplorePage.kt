package com.sakayori.kotlinytmusicscraper.pages

import com.sakayori.kotlinytmusicscraper.models.PlaylistItem
import com.sakayori.kotlinytmusicscraper.models.VideoItem

data class ExplorePage(
    val released: List<PlaylistItem>,
    val musicVideo: List<VideoItem>,
)
