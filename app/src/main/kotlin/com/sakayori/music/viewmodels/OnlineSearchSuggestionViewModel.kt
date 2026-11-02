/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakayori.innertube.YouTube
import com.sakayori.innertube.models.WatchEndpoint
import com.sakayori.innertube.models.YTItem
import com.sakayori.innertube.models.filterExplicit
import com.sakayori.innertube.models.filterVideoSongs
import com.sakayori.innertube.utils.YouTubeUrlParser
import com.sakayori.music.constants.HideExplicitKey
import com.sakayori.music.constants.HideVideoSongsKey
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.db.entities.SearchHistory
import com.sakayori.music.utils.dataStore
import com.sakayori.music.utils.get
import com.sakayori.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnlineSearchSuggestionViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        database: MusicDatabase,
    ) : ViewModel() {
        val query = MutableStateFlow("")
        private val _viewState = MutableStateFlow(SearchSuggestionViewState())
        val viewState = _viewState.asStateFlow()

        private data class SearchRequest(val query: String, val revision: Long)

        init {
            viewModelScope.launch {
                combine(query, YouTube.requestContextRevision, ::SearchRequest)
                    .flatMapLatest { request ->
                        val history = database.searchHistory(request.query)
                            .map { if (request.query.isEmpty()) it else it.take(3) }
                            .onStart { emit(emptyList()) }
                        val remote = if (request.query.isEmpty()) {
                            flowOf(SearchSuggestionViewState())
                        } else {
                            flow {
                                val parsedUrl = YouTubeUrlParser.parse(request.query)
                                val initial = SearchSuggestionViewState(isUrlQuery = parsedUrl != null)
                                emit(initial)
                                // The local history flow is already running while this suspends.
                                val result = withContext(Dispatchers.IO) {
                                    if (parsedUrl != null) {
                                        val item = fetchParsedUrlItem(parsedUrl)
                                        initial.copy(items = listOfNotNull(item), parsedUrlItem = item)
                                    } else {
                                        val suggestions = YouTube.searchSuggestions(request.query).valueOrNull()
                                        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
                                        val hideVideos = context.dataStore.get(HideVideoSongsKey, false)
                                        initial.copy(
                                            suggestions = suggestions?.queries.orEmpty(),
                                            items = suggestions?.recommendedItems.orEmpty()
                                                .distinctBy { it.id }
                                                .filterExplicit(hideExplicit)
                                                .filterVideoSongs(hideVideos),
                                        )
                                    }
                                }
                                currentCoroutineContext().ensureActive()
                                emit(result)
                            }.catch { error ->
                                if (error is CancellationException || error !is Exception) throw error
                                // Initial remote state was emitted already; keep local history live.
                                reportException(error)
                            }
                        }
                        combine(history, remote) { local, result ->
                            request to result.copy(
                                history = local,
                                suggestions = result.suggestions.filter { suggestion -> local.none { it.query == suggestion } },
                            )
                        }
                    }.collect { (request, result) ->
                        // flatMapLatest can have buffered emissions; do not publish an old query
                        // or the previous account/locale after the source has already changed.
                        if (request.query == query.value && request.revision == YouTube.requestContextRevision.value) {
                            _viewState.value = result
                        }
                    }
            }
        }

        private suspend fun fetchParsedUrlItem(parsedUrl: YouTubeUrlParser.ParsedUrl): YTItem? =
            when (parsedUrl) {
                is YouTubeUrlParser.ParsedUrl.Video -> {
                    // Use next() to get the song details from a video ID
                    YouTube
                        .next(WatchEndpoint(videoId = parsedUrl.id))
                        .valueOrNull()
                        ?.items
                        ?.firstOrNull()
                }

                is YouTubeUrlParser.ParsedUrl.Playlist -> {
                    // Fetch playlist details
                    YouTube
                        .playlist(parsedUrl.id)
                        .valueOrNull()
                        ?.playlist
                }

                is YouTubeUrlParser.ParsedUrl.Album -> {
                    // For albums, we need to get the browseId from the playlist
                    // First, try to get the album page
                    val albumResult = YouTube.album("MPREb_${parsedUrl.id}").valueOrNull()
                    if (albumResult != null) {
                        albumResult.album
                    } else {
                        // If that fails, treat it as a playlist
                        YouTube
                            .playlist(parsedUrl.id)
                            .valueOrNull()
                            ?.playlist
                    }
                }

                is YouTubeUrlParser.ParsedUrl.Artist -> {
                    // Fetch artist details
                    if (parsedUrl.id.startsWith("MPRE")) {
                        // It's a browse ID
                        YouTube
                            .artist(parsedUrl.id)
                            .valueOrNull()
                            ?.artist
                    } else {
                        // It's a channel ID, we need to find the browse ID
                        // For now, try using the channel ID as browse ID
                        YouTube
                            .artist(parsedUrl.id)
                            .valueOrNull()
                            ?.artist
                    }
                }
            }

        private fun <T> Result<T>.valueOrNull(): T? = getOrElse {
            if (it is CancellationException) throw it
            null
        }
    }

data class SearchSuggestionViewState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
    val parsedUrlItem: YTItem? = null,
    val isUrlQuery: Boolean = false,
)
