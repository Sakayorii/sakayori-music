package com.sakayori.innertube.pages

import com.sakayori.innertube.models.YTItem

data class LibraryContinuationPage(
    val items: List<YTItem>,
    val continuation: String?,
)
