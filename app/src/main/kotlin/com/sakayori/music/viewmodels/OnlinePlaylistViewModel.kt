/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakayori.innertube.YouTube
import com.sakayori.innertube.models.PlaylistItem
import com.sakayori.innertube.models.SongItem
import com.sakayori.innertube.models.filterVideoSongs
import com.sakayori.innertube.pages.PlaylistContinuationPage
import com.sakayori.music.constants.HideVideoSongsKey
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.models.distinctPlaylistOccurrences
import com.sakayori.music.utils.dataStore
import com.sakayori.music.utils.get
import com.sakayori.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger
import com.sakayori.music.constants.SongSortType
import com.sakayori.innertube.models.Artist
import com.sakayori.innertube.models.Album
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val database: MusicDatabase
) : ViewModel() {
    private val playlistId = savedStateHandle.get<String>("playlistId")!!

    // Check if this is a special podcast playlist (with or without VL prefix)
    private val normalizedPlaylistId = playlistId.removePrefix("VL")
    val isPodcastPlaylist = normalizedPlaylistId == "RDPN" || normalizedPlaylistId == "SE"

    val playlist = MutableStateFlow<PlaylistItem?>(null)
    val playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    @Volatile var continuation: String? = null
        private set

    private var proactiveLoadJob: Job? = null
    private var initialLoadJob: Job? = null
    private var manualLoadJob: Job? = null
    private val loadGeneration = AtomicInteger()
    private val continuationMutex = Mutex()
    private val consumedContinuations = mutableSetOf<String>()

    init {
        fetchInitialPlaylistData()
    }

    private fun fetchInitialPlaylistData() {
        val generation = loadGeneration.incrementAndGet()
        initialLoadJob?.cancel()
        proactiveLoadJob?.cancel()
        manualLoadJob?.cancel()
        initialLoadJob = viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _isLoadingMore.value = false
            _error.value = null
            continuationMutex.withLock {
                continuation = null
                consumedContinuations.clear()
            }

            if (isPodcastPlaylist) {
                // Use special podcast playlist APIs
                fetchPodcastPlaylist()
            } else {
                // Use regular playlist API
                fetchRegularPlaylist(generation)
            }
        }
    }

    private suspend fun fetchPodcastPlaylist() {
        when (normalizedPlaylistId) {
            "RDPN" -> {
                YouTube.newEpisodes()
                    .onSuccess { episodes ->
                        playlist.value = PlaylistItem(
                            id = playlistId,
                            title = "New Episodes",
                            author = null,
                            songCountText = "${episodes.size} episodes",
                            thumbnail = episodes.firstOrNull()?.thumbnail ?: "",
                            playEndpoint = null,
                            shuffleEndpoint = null,
                            radioEndpoint = null,
                        )
                        playlistSongs.value = applySongFilters(episodes)
                        _isLoading.value = false
                    }.onFailure { throwable ->
                        _error.value = throwable.message ?: "Failed to load new episodes"
                        _isLoading.value = false
                        reportException(throwable)
                    }
            }
            "SE" -> {
                timber.log.Timber.d("[SE_LOCAL] Fetching SE playlist...")
                val result = YouTube.episodesForLater()
                val episodes = result.getOrNull() ?: emptyList()
                timber.log.Timber.d("[SE_LOCAL] YouTube API result: ${if (result.isSuccess) "success" else "failed"}, ${episodes.size} episodes")

                if (result.isSuccess && episodes.isNotEmpty()) {
                    // Use YouTube episodes
                    playlist.value = PlaylistItem(
                        id = playlistId,
                        title = "Episodes for Later",
                        author = null,
                        songCountText = "${episodes.size} episodes",
                        thumbnail = episodes.firstOrNull()?.thumbnail ?: "",
                        playEndpoint = null,
                        shuffleEndpoint = null,
                        radioEndpoint = null,
                    )
                    playlistSongs.value = applySongFilters(episodes)
                    _isLoading.value = false
                } else {
                    // Fall back to local saved episodes when API fails or returns empty
                    timber.log.Timber.d("[SE_LOCAL] Falling back to local saved episodes")
                    loadLocalSavedEpisodes()
                }
            }
            else -> {
                _error.value = "Unknown podcast playlist"
                _isLoading.value = false
            }
        }
    }

    private suspend fun fetchRegularPlaylist(generation: Int) {
        YouTube.playlist(playlistId)
            .onSuccess { playlistPage ->
                val songs = applySongFilters(playlistPage.songs)
                currentCoroutineContext().ensureActive()
                if (generation != loadGeneration.get()) return@onSuccess
                playlist.value = playlistPage.playlist
                playlistSongs.value = songs
                continuation = playlistPage.songsContinuation
                _isLoading.value = false
                if (continuation != null) {
                    startProactiveBackgroundLoading(generation)
                }
            }.onFailure { throwable ->
                currentCoroutineContext().ensureActive()
                if (generation != loadGeneration.get()) return@onFailure
                _error.value = throwable.message ?: "Failed to load playlist"
                _isLoading.value = false
                reportException(throwable)
            }
    }

    private suspend fun loadLocalSavedEpisodes() {
        timber.log.Timber.d("[SE_LOCAL] loadLocalSavedEpisodes called")
        val savedEpisodes = database.savedPodcastEpisodes(SongSortType.CREATE_DATE, true).firstOrNull() ?: emptyList()
        timber.log.Timber.d("[SE_LOCAL] Found ${savedEpisodes.size} saved episodes")
        savedEpisodes.forEachIndexed { index, ep ->
            timber.log.Timber.d("[SE_LOCAL] Episode $index: id=${ep.song.id}, title=${ep.song.title}, isEpisode=${ep.song.isEpisode}, inLibrary=${ep.song.inLibrary}")
        }
        if (savedEpisodes.isNotEmpty()) {
            // Convert local Song entities to SongItem format
            val songItems = savedEpisodes.map { song ->
                SongItem(
                    id = song.song.id,
                    title = song.song.title,
                    artists = song.artists.map { Artist(name = it.name, id = it.id) },
                    album = song.album?.let { com.sakayori.innertube.models.Album(name = it.title, id = it.id) },
                    duration = song.song.duration,
                    thumbnail = song.song.thumbnailUrl ?: "",
                    explicit = song.song.explicit,
                    endpoint = null,
                )
            }
            timber.log.Timber.d("[SE_LOCAL] Converted to ${songItems.size} SongItems")
            playlist.value = PlaylistItem(
                id = playlistId,
                title = "Episodes for Later",
                author = null,
                songCountText = "${songItems.size} episodes",
                thumbnail = songItems.firstOrNull()?.thumbnail ?: "",
                playEndpoint = null,
                shuffleEndpoint = null,
                radioEndpoint = null,
            )
            val filtered = applySongFilters(songItems)
            timber.log.Timber.d("[SE_LOCAL] After filter: ${filtered.size} episodes, setting playlistSongs")
            playlistSongs.value = filtered
            _isLoading.value = false
            timber.log.Timber.d("[SE_LOCAL] Done, isLoading=false")
        } else {
            timber.log.Timber.d("[SE_LOCAL] No saved episodes found")
            _error.value = "No saved episodes"
            _isLoading.value = false
        }
    }

    private fun startProactiveBackgroundLoading(generation: Int = loadGeneration.get()) {
        proactiveLoadJob?.cancel() // Cancel previous job if any
        proactiveLoadJob = viewModelScope.launch(Dispatchers.IO) {
            var currentProactiveToken = continuation
            while (currentProactiveToken != null && isActive) {
                // If a manual loadMore is happening, pause proactive loading
                if (_isLoadingMore.value) {
                    // Wait until manual load is finished, then re-evaluate
                    // This simple break and restart strategy from loadMoreSongs is preferred
                    break 
                }

                val token = currentProactiveToken
                YouTube.playlistContinuation(token)
                    .onSuccess { playlistContinuationPage ->
                        val accepted = appendContinuationPage(generation, token, playlistContinuationPage)
                        currentProactiveToken = if (accepted) continuation else null
                    }.onFailure { throwable ->
                        reportException(throwable)
                        currentProactiveToken = null // Stop proactive loading on error
                    }
            }
            // If loop finishes because currentProactiveToken is null, all songs are loaded proactively.
        }
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value) return // Already loading more (manually)
        
        val tokenForManualLoad = continuation ?: return // No more songs to load

        proactiveLoadJob?.cancel() // Cancel proactive loading to prioritize manual scroll
        _isLoadingMore.value = true
        val generation = loadGeneration.get()

        manualLoadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                YouTube.playlistContinuation(tokenForManualLoad)
                .onSuccess { playlistContinuationPage ->
                    appendContinuationPage(generation, tokenForManualLoad, playlistContinuationPage)
                }.onFailure { throwable ->
                    reportException(throwable)
                }
            } finally {
                if (generation == loadGeneration.get()) {
                    _isLoadingMore.value = false
                    // Resume proactive loading if there's still a continuation
                    if (continuation != null && isActive) {
                        startProactiveBackgroundLoading(generation)
                    }
                }
            }
        }
    }

    private suspend fun appendContinuationPage(
        generation: Int,
        token: String,
        page: PlaylistContinuationPage,
    ): Boolean = continuationMutex.withLock {
        currentCoroutineContext().ensureActive()
        if (generation != loadGeneration.get() || token != continuation || token in consumedContinuations) return@withLock false
        val songs = applySongFilters(playlistSongs.value + page.songs)
        currentCoroutineContext().ensureActive()
        if (generation != loadGeneration.get()) return@withLock false
        consumedContinuations.add(token)
        playlistSongs.value = songs
        continuation = page.continuation?.takeUnless { it in consumedContinuations }
        true
    }

    fun retry() {
        proactiveLoadJob?.cancel()
        fetchInitialPlaylistData() // This will also restart proactive loading if applicable
    }

    private fun applySongFilters(songs: List<SongItem>): List<SongItem> {
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        return distinctPlaylistOccurrences(songs)
            .filterVideoSongs(hideVideoSongs)
    }

    override fun onCleared() {
        super.onCleared()
        proactiveLoadJob?.cancel()
    }
}
