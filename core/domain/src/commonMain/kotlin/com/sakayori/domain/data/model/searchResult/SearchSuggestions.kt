package com.sakayori.domain.data.model.searchResult

import com.sakayori.domain.data.type.SearchResultType

data class SearchSuggestions(
    val queries: List<String>,
    val recommendedItems: List<SearchResultType>,
)
