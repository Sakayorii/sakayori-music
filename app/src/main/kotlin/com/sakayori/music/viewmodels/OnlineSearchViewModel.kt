/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.viewmodels

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakayori.innertube.YouTube
import com.sakayori.innertube.models.filterExplicit
import com.sakayori.innertube.models.filterVideoSongs
import com.sakayori.innertube.models.filterYoutubeShorts
import com.sakayori.innertube.models.withResolvedArtistIds
import com.sakayori.innertube.pages.SearchSummaryPage
import com.sakayori.music.constants.HideExplicitKey
import com.sakayori.music.constants.HideVideoSongsKey
import com.sakayori.music.constants.HideYoutubeShortsKey
import com.sakayori.music.models.ItemsPage
import com.sakayori.music.utils.SearchRoutes
import com.sakayori.music.utils.dataStore
import com.sakayori.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlineSearchViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val query = SearchRoutes.decodeQuery(savedStateHandle.get<String>("query").orEmpty())
    val filter = MutableStateFlow<YouTube.SearchFilter?>(null)
    var summaryPage by mutableStateOf<SearchSummaryPage?>(null)
    val viewStateMap = mutableStateMapOf<String, ItemsPage?>()

    private data class ContentPolicy(val hideExplicit: Boolean, val hideVideos: Boolean, val hideShorts: Boolean)
    private data class Selection(val filter: YouTube.SearchFilter?, val revision: Long)
    private data class PageRequest(val generation: Long, val filter: String, val continuation: String)

    // Keep raw pages so preference changes do not need another network search. Only the existing
    // filtered public state reaches the UI. All mutations are on viewModelScope's main dispatcher.
    private var rawSummary: SearchSummaryPage? = null
    private val rawPages = mutableMapOf<String, ItemsPage>()
    private val consumedCursors = mutableMapOf<String, MutableSet<String>>()
    private var revision = YouTube.requestContextRevision.value
    private var generation = 0L
    private var activeSelection: Selection? = null
    private var contentPolicy = ContentPolicy(hideExplicit = true, hideVideos = true, hideShorts = true)
    private val pageRequests = Channel<PageRequest>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            context.dataStore.data.map {
                ContentPolicy(it[HideExplicitKey] ?: false, it[HideVideoSongsKey] ?: false, it[HideYoutubeShortsKey] ?: false)
            }.distinctUntilChanged().catch { error ->
                if (error is CancellationException) throw error
                reportException(error)
                emit(ContentPolicy(hideExplicit = true, hideVideos = true, hideShorts = true))
            }.collect { policy ->
                contentPolicy = policy
                publish(policy)
            }
        }
        viewModelScope.launch {
            // Settings arrival/refiltering is not a second network selection. A cold settings
            // read and the first search can run independently without restarting the request.
            combine(filter, YouTube.requestContextRevision, ::Selection).collectLatest { selection ->
                val selectedGeneration = ++generation
                activeSelection = selection
                if (revision != selection.revision) {
                    revision = selection.revision
                    rawSummary = null
                    rawPages.clear()
                    consumedCursors.clear()
                    summaryPage = null
                    viewStateMap.clear()
                }
                publish(contentPolicy)
                coroutineScope {
                    var enrichment: Job? = null
                    fun enrich() {
                        enrichment?.cancel()
                        enrichment = launch {
                            handleFailure {
                                enrichSelection(selection, selectedGeneration)
                            }
                        }
                    }
                    // One receiver serialises cursor requests. Repeated requests for a cursor
                    // already consumed are ignored, and filter changes cancel the whole scope.
                    launch {
                        for (request in pageRequests) {
                            if (request.generation != selectedGeneration || !isCurrent(selection, selectedGeneration)) continue
                            handleFailure {
                                if (loadPage(request, selection, selectedGeneration)) enrich()
                            }
                        }
                    }
                    handleFailure {
                        loadInitial(selection, selectedGeneration)
                        if (isCurrent(selection, selectedGeneration)) enrich()
                    }
                }
            }
        }
    }

    fun loadMore() {
        val selection = activeSelection ?: return
        if (!isCurrent(selection, generation)) return
        val key = selection.filter?.value ?: return
        val continuation = rawPages[key]?.continuation ?: return
        pageRequests.trySend(PageRequest(generation, key, continuation))
    }

    private suspend fun loadInitial(selection: Selection, selectedGeneration: Long) {
        val selectedFilter = selection.filter
        if (selectedFilter == null || selectedFilter == YouTube.SearchFilter.FILTER_EPISODE) {
            val page = rawSummary ?: YouTube.searchSummary(query).getOrThrow()
            currentCoroutineContext().ensureActive()
            if (!isCurrent(selection, selectedGeneration)) return
            rawSummary = page
        } else if (rawPages[selectedFilter.value] == null) {
            val result = YouTube.search(query, selectedFilter).getOrThrow()
            currentCoroutineContext().ensureActive()
            if (!isCurrent(selection, selectedGeneration)) return
            rawPages[selectedFilter.value] = ItemsPage(result.items.distinctBy { it.id }, result.continuation)
        }
        publish(contentPolicy)
    }

    private suspend fun loadPage(request: PageRequest, selection: Selection, selectedGeneration: Long): Boolean {
        val key = selection.filter?.value ?: return false
        if (request.filter != key || rawPages[key]?.continuation != request.continuation) return false
        val consumed = consumedCursors.getOrPut(key) { mutableSetOf() }
        if (request.continuation in consumed) return false
        val result = YouTube.searchContinuation(request.continuation).getOrThrow()
        currentCoroutineContext().ensureActive()
        if (!isCurrent(selection, selectedGeneration)) return false
        val latest = rawPages[key] ?: return false
        if (latest.continuation != request.continuation) return false
        consumed.add(request.continuation)
        rawPages[key] = ItemsPage(
            // Re-read after suspension: enrichment may already have filled IDs in this page.
            (latest.items + result.items).distinctBy { it.id },
            result.continuation?.takeUnless { it in consumed },
        )
        publish(contentPolicy)
        return true
    }

    private suspend fun enrichSelection(selection: Selection, selectedGeneration: Long) {
        val selectedFilter = selection.filter
        if (selectedFilter == null || selectedFilter == YouTube.SearchFilter.FILTER_EPISODE) {
            val original = rawSummary ?: return
            val ids = YouTube.resolveArtistIdMap(original.summaries.flatMap { it.items })
            currentCoroutineContext().ensureActive()
            if (ids.isEmpty() || !isCurrent(selection, selectedGeneration)) return
            val latest = rawSummary ?: return
            rawSummary = latest.copy(summaries = latest.summaries.map { section ->
                section.copy(items = section.items.withResolvedArtistIds(ids))
            })
        } else {
            val key = selectedFilter.value
            val original = rawPages[key] ?: return
            val ids = YouTube.resolveArtistIdMap(original.items)
            currentCoroutineContext().ensureActive()
            if (ids.isEmpty() || !isCurrent(selection, selectedGeneration)) return
            val latest = rawPages[key] ?: return
            // Do not replace the page captured before the lookup: it may now have more items.
            rawPages[key] = latest.copy(items = latest.items.withResolvedArtistIds(ids))
        }
        publish(contentPolicy)
    }

    private fun publish(policy: ContentPolicy) {
        if (revision != YouTube.requestContextRevision.value) {
            summaryPage = null
            viewStateMap.clear()
            return
        }
        summaryPage = rawSummary?.filterExplicit(policy.hideExplicit)
            ?.filterVideoSongs(policy.hideVideos)?.filterYoutubeShorts(policy.hideShorts)
        rawPages.forEach { (key, page) ->
            viewStateMap[key] = page.copy(items = page.items.filterExplicit(policy.hideExplicit)
                .filterVideoSongs(policy.hideVideos).filterYoutubeShorts(policy.hideShorts))
        }
        // Keep the existing episode workaround: that endpoint's item structure is different.
        summaryPage?.let { page ->
            viewStateMap[YouTube.SearchFilter.FILTER_EPISODE.value] = ItemsPage(
                page.summaries.firstOrNull { it.title == "Episodes" }?.items.orEmpty(), null,
            )
        }
    }

    private fun isCurrent(selection: Selection, selectedGeneration: Long): Boolean =
        selectedGeneration == generation && activeSelection == selection && filter.value == selection.filter &&
            YouTube.requestContextRevision.value == selection.revision

    private suspend fun handleFailure(block: suspend () -> Unit) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            currentCoroutineContext().ensureActive()
            reportException(error)
        }
    }
}
