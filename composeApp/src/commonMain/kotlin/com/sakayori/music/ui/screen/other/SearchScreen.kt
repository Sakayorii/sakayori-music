package com.sakayori.music.ui.screen.other

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sakayori.common.Config
import com.sakayori.music.utils.trackTextInputFocus
import com.sakayori.domain.data.entities.SongEntity
import com.sakayori.domain.data.model.browse.album.Track
import com.sakayori.domain.data.model.searchResult.albums.AlbumsResult
import com.sakayori.domain.data.model.searchResult.artists.ArtistsResult
import com.sakayori.domain.data.model.searchResult.playlists.PlaylistsResult
import com.sakayori.domain.data.model.searchResult.songs.SongsResult
import com.sakayori.domain.data.model.searchResult.videos.VideosResult
import com.sakayori.domain.data.type.SearchResultType
import com.sakayori.domain.mediaservice.handler.PlaylistType
import com.sakayori.domain.mediaservice.handler.QueueData
import com.sakayori.domain.utils.connectArtists
import com.sakayori.domain.utils.toSongEntity
import com.sakayori.domain.utils.toTrack
import com.sakayori.music.extension.getStringBlocking
import com.sakayori.music.ui.component.ArtistFullWidthItems
import com.sakayori.music.ui.component.Chip
import com.sakayori.music.ui.component.EndOfPage
import com.sakayori.music.ui.component.NowPlayingBottomSheet
import com.sakayori.music.ui.component.PlaylistFullWidthItems
import com.sakayori.music.ui.component.ShimmerSearchItem
import com.sakayori.music.ui.component.SakayoriMusicChartButton
import com.sakayori.music.ui.component.SongFullWidthItems
import com.sakayori.music.ui.navigation.destination.list.AlbumDestination
import com.sakayori.music.ui.navigation.destination.list.ArtistDestination
import com.sakayori.music.ui.navigation.destination.list.PlaylistDestination
import com.sakayori.music.ui.navigation.destination.list.PodcastDestination
import com.sakayori.music.ui.theme.typo
import com.sakayori.music.viewModel.SearchScreenUIState
import com.sakayori.music.viewModel.SearchType
import com.sakayori.music.viewModel.SearchViewModel
import com.sakayori.music.viewModel.SharedViewModel
import com.sakayori.music.viewModel.toStringRes
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.albums
import com.sakayori.music.generated.resources.artists
import com.sakayori.music.generated.resources.baseline_arrow_outward_24
import com.sakayori.music.generated.resources.baseline_close_24
import com.sakayori.music.generated.resources.baseline_history_24
import com.sakayori.music.generated.resources.baseline_search_24
import com.sakayori.music.generated.resources.clear_search_history
import com.sakayori.music.generated.resources.recent_searches
import com.sakayori.music.generated.resources.error_occurred
import com.sakayori.music.generated.resources.everything_you_need
import com.sakayori.music.generated.resources.holder
import com.sakayori.music.generated.resources.in_search
import com.sakayori.music.generated.resources.no_results_found
import com.sakayori.music.generated.resources.try_different_search_terms
import com.sakayori.music.generated.resources.playlists
import com.sakayori.music.generated.resources.podcasts
import com.sakayori.music.generated.resources.retry
import com.sakayori.music.generated.resources.search_for
import com.sakayori.music.generated.resources.search_for_songs_artists_albums_playlists_and_more
import com.sakayori.music.generated.resources.song
import com.sakayori.music.generated.resources.videos
import com.sakayori.music.generated.resources.what_do_you_want_to_listen_to

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel = koinInject(),
    sharedViewModel: SharedViewModel = koinInject(),
    navController: NavController,
) {
    val uriHandler = LocalUriHandler.current
    val focusManager = LocalFocusManager.current
    val searchScreenState by searchViewModel.searchScreenState.collectAsStateWithLifecycle()
    val uiState by searchViewModel.searchScreenUIState.collectAsStateWithLifecycle()
    val searchHistory by searchViewModel.searchHistory.collectAsStateWithLifecycle()

    var searchUIType by rememberSaveable { mutableStateOf(SearchUIType.EMPTY) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var isSearchSubmitted by rememberSaveable { mutableStateOf(false) }
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }

    var isFocused by rememberSaveable { mutableStateOf(false) }

    val searchForString = stringResource(Res.string.search_for)
    val songString = stringResource(Res.string.song).lowercase()
    val artistString = stringResource(Res.string.artists).lowercase()
    val albumString = stringResource(Res.string.albums).lowercase()
    val playlistString = stringResource(Res.string.playlists).lowercase()
    val videoString = stringResource(Res.string.videos).lowercase()
    val podcastString = stringResource(Res.string.podcasts).lowercase()

    val placeholderTexts =
        remember {
            listOf(
                "$searchForString $songString...",
                "$searchForString $artistString...",
                "$searchForString $albumString...",
                "$searchForString $playlistString...",
                "$searchForString $videoString...",
                "$searchForString $podcastString...",
            )
        }

    var currentPlaceholderIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(isFocused) {
        while (!isFocused) {
            delay(3000)
            currentPlaceholderIndex = (currentPlaceholderIndex + 1) % placeholderTexts.size
        }
    }

    var sheetSong by remember { mutableStateOf<SongEntity?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }
    val currentVideoId by searchViewModel.nowPlayingVideoId.collectAsStateWithLifecycle()
    val chipRowState = rememberScrollState()
    val pullToRefreshState = rememberPullToRefreshState()

    val onMoreClick: (SongEntity) -> Unit = { song ->
        sheetSong = song
        showBottomSheet = true
    }

    LaunchedEffect(searchText) {
        if (isFocused) {
            isSearchSubmitted = false
            isExpanded = true
        }
        if (searchText.isNotEmpty() && isFocused) {
            kotlinx.coroutines.delay(300)
            searchViewModel.suggestQuery(searchText)
        }
    }

    LaunchedEffect(isSearchSubmitted) {
        if (isSearchSubmitted) {
            isExpanded = false
        }
    }

    LaunchedEffect(isFocused) {
        if (isFocused) {
            isExpanded = true
        }
    }

    LaunchedEffect(isExpanded, searchText, isFocused) {
        searchUIType =
            if (searchText.isNotEmpty() && isExpanded) {
                SearchUIType.SEARCH_SUGGESTIONS
            } else if (isFocused && isExpanded) {
                SearchUIType.SEARCH_HISTORY
            } else if (searchText.isEmpty()) {
                SearchUIType.EMPTY
            } else {
                SearchUIType.SEARCH_RESULTS
            }
    }

    if (showBottomSheet) {
        NowPlayingBottomSheet(
            onDismiss = {
                showBottomSheet = false
                sheetSong = null
            },
            navController = navController,
            song = sheetSong,
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Transparent)
                .padding(vertical = 10.dp),
    ) {
        SearchBar(
            inputField = {
                SearchBarDefaults.InputField(
                    query = searchText,
                    onQueryChange = { newText ->
                        searchText = newText
                    },
                    onSearch = { query ->
                        if (query.isNotEmpty()) {
                            isSearchSubmitted = true
                            focusManager.clearFocus()
                            searchViewModel.insertSearchHistory(query)
                            when (searchScreenState.searchType) {
                                SearchType.ALL -> searchViewModel.searchAll(query)
                                SearchType.SONGS -> searchViewModel.searchSongs(query)
                                SearchType.VIDEOS -> searchViewModel.searchVideos(query)
                                SearchType.ALBUMS -> searchViewModel.searchAlbums(query)
                                SearchType.ARTISTS -> searchViewModel.searchArtists(query)
                                SearchType.PLAYLISTS -> searchViewModel.searchPlaylists(query)
                                SearchType.FEATURED_PLAYLISTS -> searchViewModel.searchFeaturedPlaylist(query)
                                SearchType.PODCASTS -> searchViewModel.searchPodcast(query)
                            }
                        }
                    },
                    expanded = false,
                    onExpandedChange = {},
                    enabled = true,
                    placeholder = {
                        AnimatedContent(
                            targetState = currentPlaceholderIndex,
                            transitionSpec = {
                                (
                                    fadeIn(animationSpec = tween(500)) +
                                        slideInVertically { height -> height }
                                ).togetherWith(
                                    fadeOut(animationSpec = tween(500)) +
                                        slideOutVertically { height -> -height },
                                )
                            },
                            label = "placeholder_animation",
                        ) { index ->
                            Text(
                                text = placeholderTexts[index],
                                style = typo().labelMedium,
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(Res.drawable.baseline_search_24),
                            contentDescription = "Search",
                        )
                    },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(
                                modifier = Modifier.clip(CircleShape),
                                onClick = {
                                    searchText = ""
                                    isSearchSubmitted = false
                                },
                            ) {
                                Icon(
                                    painter = painterResource(Res.drawable.baseline_close_24),
                                    contentDescription = "Clear search",
                                )
                            }
                        }
                    },
                )
            },
            expanded = false,
            onExpandedChange = {},
            modifier =
                Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        isFocused = it.isFocused
                    }
                    .trackTextInputFocus()
                    .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(8.dp),
            content = {},
        )

        Crossfade(targetState = searchUIType) {
            when (it) {
                SearchUIType.SEARCH_SUGGESTIONS -> {
                    LazyColumn(
                        Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 10.dp,
                        ),
                    ) {
                        items(searchScreenState.suggestYTItems) { item ->
                            SuggestItemRow(
                                searchResult = item,
                                onItemClick = { item ->
                                    when (item) {
                                        is SongsResult, is VideosResult -> {
                                            val firstTrack: Track = (item as? SongsResult)?.toTrack()
                                                ?: (item as? VideosResult)?.toTrack()
                                                ?: return@SuggestItemRow
                                            searchViewModel.setQueueData(
                                                QueueData.Data(
                                                    listTracks = arrayListOf(firstTrack),
                                                    firstPlayedTrack = firstTrack,
                                                    playlistId = "RDAMVM${firstTrack.videoId}",
                                                    playlistName = "\"${searchText}\" ${getStringBlocking(Res.string.in_search)}",
                                                    playlistType = PlaylistType.RADIO,
                                                    continuation = null,
                                                ),
                                            )
                                            searchViewModel.loadMediaItem(firstTrack, type = Config.SONG_CLICK)
                                        }

                                        is ArtistsResult -> {
                                            navController.navigate(
                                                ArtistDestination(item.browseId),
                                            )
                                        }

                                        is AlbumsResult -> {
                                            navController.navigate(
                                                AlbumDestination(item.browseId),
                                            )
                                        }

                                        is PlaylistsResult -> {
                                            navController.navigate(
                                                PlaylistDestination(
                                                    item.browseId,
                                                ),
                                            )
                                        }
                                    }
                                },
                            )
                        }
                        items(searchScreenState.suggestQueries) { suggestion ->
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = ripple(),
                                            onClick = {
                                                searchText = suggestion
                                                focusManager.clearFocus()
                                                isSearchSubmitted = true
                                                searchViewModel.insertSearchHistory(suggestion)
                                                when (searchScreenState.searchType) {
                                                    SearchType.ALL -> searchViewModel.searchAll(suggestion)
                                                    SearchType.SONGS -> searchViewModel.searchSongs(suggestion)
                                                    SearchType.VIDEOS -> searchViewModel.searchVideos(suggestion)
                                                    SearchType.ALBUMS -> searchViewModel.searchAlbums(suggestion)
                                                    SearchType.ARTISTS -> searchViewModel.searchArtists(suggestion)
                                                    SearchType.PLAYLISTS -> searchViewModel.searchPlaylists(suggestion)
                                                    SearchType.FEATURED_PLAYLISTS -> searchViewModel.searchFeaturedPlaylist(suggestion)
                                                    SearchType.PODCASTS -> searchViewModel.searchPodcast(suggestion)
                                                }
                                            },
                                        ).padding(horizontal = 12.dp, vertical = 2.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = suggestion,
                                    style = typo().bodyMedium,
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                IconButton(
                                    onClick = {
                                        searchText = suggestion
                                        focusRequester.requestFocus()
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.baseline_arrow_outward_24),
                                        contentDescription = "Search suggestion",
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                        item {
                            EndOfPage(
                                withoutCredit = true,
                            )
                        }
                    }
                }

                SearchUIType.SEARCH_HISTORY -> {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(
                                    horizontal = 16.dp,
                                    vertical = 10.dp,
                                ),
                    ) {
                        if (searchHistory.isNotEmpty()) {
                            Text(
                                text = stringResource(Res.string.recent_searches),
                                style = typo().labelMedium,
                                color = Color(0xFF00BCD4),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                items(searchHistory.take(8)) { item ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(Color.White.copy(alpha = 0.08f))
                                            .clickable {
                                                searchText = item
                                                focusManager.clearFocus()
                                                isSearchSubmitted = true
                                                searchViewModel.insertSearchHistory(item)
                                                when (searchScreenState.searchType) {
                                                    SearchType.ALL -> searchViewModel.searchAll(item)
                                                    SearchType.SONGS -> searchViewModel.searchSongs(item)
                                                    SearchType.VIDEOS -> searchViewModel.searchVideos(item)
                                                    SearchType.ALBUMS -> searchViewModel.searchAlbums(item)
                                                    SearchType.ARTISTS -> searchViewModel.searchArtists(item)
                                                    SearchType.PLAYLISTS -> searchViewModel.searchPlaylists(item)
                                                    SearchType.FEATURED_PLAYLISTS -> searchViewModel.searchFeaturedPlaylist(item)
                                                    SearchType.PODCASTS -> searchViewModel.searchPodcast(item)
                                                }
                                            }
                                            .padding(horizontal = 14.dp, vertical = 8.dp),
                                    ) {
                                        Text(
                                            text = item,
                                            style = typo().bodySmall,
                                            color = Color.White,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        LazyColumn {
                            stickyHeader {
                                Crossfade(
                                    targetState = searchHistory.isNotEmpty(),
                                ) {
                                    if (it) {
                                        Row(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .background(Color.Black),
                                        ) {
                                            TextButton(
                                                onClick = { searchViewModel.deleteSearchHistory() },
                                            ) {
                                                Text(
                                                    text = stringResource(Res.string.clear_search_history),
                                                    color = MaterialTheme.colorScheme.onBackground,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            items(searchHistory) { historyItem ->
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                searchText = historyItem
                                                focusManager.clearFocus()
                                                isSearchSubmitted = true
                                                searchViewModel.insertSearchHistory(historyItem)
                                                when (searchScreenState.searchType) {
                                                    SearchType.ALL -> searchViewModel.searchAll(historyItem)
                                                    SearchType.SONGS -> searchViewModel.searchSongs(historyItem)
                                                    SearchType.VIDEOS -> searchViewModel.searchVideos(historyItem)
                                                    SearchType.ALBUMS -> searchViewModel.searchAlbums(historyItem)
                                                    SearchType.ARTISTS -> searchViewModel.searchArtists(historyItem)
                                                    SearchType.PLAYLISTS -> searchViewModel.searchPlaylists(historyItem)
                                                    SearchType.FEATURED_PLAYLISTS -> searchViewModel.searchFeaturedPlaylist(historyItem)
                                                    SearchType.PODCASTS -> searchViewModel.searchPodcast(historyItem)
                                                }
                                            }.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        painter = painterResource(Res.drawable.baseline_history_24),
                                        contentDescription = "Search history",
                                        modifier = Modifier.size(24.dp),
                                    )
                                    Spacer(modifier = Modifier.padding(horizontal = 12.dp))
                                    Text(
                                        text = historyItem,
                                        style = typo().bodyMedium,
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    IconButton(
                                        onClick = {
                                            searchText = historyItem
                                            focusRequester.requestFocus()
                                        },
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.baseline_arrow_outward_24),
                                            contentDescription = "Search suggestion",
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                            item {
                                EndOfPage(
                                    withoutCredit = true,
                                )
                            }
                        }
                    }
                }

                SearchUIType.EMPTY -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                    ) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = stringResource(Res.string.everything_you_need),
                                style = typo().titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = stringResource(Res.string.search_for_songs_artists_albums_playlists_and_more),
                                style = typo().bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            SakayoriMusicChartButton(
                                modifier =
                                    Modifier
                                        .padding(top = 10.dp),
                            ) {
                                uriHandler.openUri("https://chart.sakayori.dev")
                            }
                        }
                    }
                }

                SearchUIType.SEARCH_RESULTS -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier =
                                Modifier
                                    .horizontalScroll(chipRowState)
                                    .padding(top = 10.dp)
                                    .padding(horizontal = 12.dp),
                        ) {
                            SearchType.entries.forEach { id ->
                                val isSelected = id == searchScreenState.searchType
                                Spacer(modifier = Modifier.width(4.dp))
                                Chip(
                                    isAnimated = uiState is SearchScreenUIState.Loading,
                                    isSelected = isSelected,
                                    text = stringResource(id.toStringRes()),
                                ) {
                                    searchViewModel.setSearchType(id)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                        PullToRefreshBox(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 10.dp),
                            state = pullToRefreshState,
                            onRefresh = {
                                val query = searchText.trim()
                                if (query.isNotEmpty()) {
                                    isSearchSubmitted = true
                                    searchViewModel.insertSearchHistory(query)
                                    when (searchScreenState.searchType) {
                                        SearchType.ALL -> searchViewModel.searchAll(query)
                                        SearchType.SONGS -> searchViewModel.searchSongs(query)
                                        SearchType.VIDEOS -> searchViewModel.searchVideos(query)
                                        SearchType.ALBUMS -> searchViewModel.searchAlbums(query)
                                        SearchType.ARTISTS -> searchViewModel.searchArtists(query)
                                        SearchType.PLAYLISTS -> searchViewModel.searchPlaylists(query)
                                        SearchType.FEATURED_PLAYLISTS -> searchViewModel.searchFeaturedPlaylist(query)
                                        SearchType.PODCASTS -> searchViewModel.searchPodcast(query)
                                    }
                                }
                            },
                            isRefreshing = uiState is SearchScreenUIState.Loading,
                            indicator = {
                                PullToRefreshDefaults.Indicator(
                                    state = pullToRefreshState,
                                    isRefreshing = uiState is SearchScreenUIState.Loading,
                                    modifier = Modifier.align(Alignment.TopCenter),
                                    containerColor = androidx.compose.ui.graphics.Color(0xFF1A1A1A),
                                    color = androidx.compose.ui.graphics.Color(0xFF00BCD4),
                                    maxDistance = PullToRefreshDefaults.PositionalThreshold - 5.dp,
                                )
                            },
                        ) {
                            Crossfade(targetState = uiState) { uiState ->
                                when (uiState) {
                                    is SearchScreenUIState.Loading -> {
                                        LazyColumn {
                                            items(10) {
                                                ShimmerSearchItem()
                                            }
                                        }
                                    }

                                    is SearchScreenUIState.Success -> {
                                        Column(modifier = Modifier.fillMaxSize()) {
                                            val currentResults =
                                                when (searchScreenState.searchType) {
                                                    SearchType.ALL -> searchScreenState.searchAllResult
                                                    SearchType.SONGS -> searchScreenState.searchSongsResult
                                                    SearchType.VIDEOS -> searchScreenState.searchVideosResult
                                                    SearchType.ALBUMS -> searchScreenState.searchAlbumsResult
                                                    SearchType.ARTISTS -> searchScreenState.searchArtistsResult
                                                    SearchType.PLAYLISTS -> searchScreenState.searchPlaylistsResult
                                                    SearchType.FEATURED_PLAYLISTS -> searchScreenState.searchFeaturedPlaylistsResult
                                                    SearchType.PODCASTS -> searchScreenState.searchPodcastsResult
                                                }

                                            Crossfade(targetState = currentResults.isNotEmpty()) {
                                                if (it) {
                                                    LazyColumn(
                                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                                        state = rememberLazyListState(),
                                                    ) {
                                                        items(currentResults) { result ->
                                                            when (result) {
                                                                is SongsResult -> {
                                                                    SongFullWidthItems(
                                                                        track = result.toTrack(),
                                                                        isPlaying = result.videoId == currentVideoId,
                                                                        modifier = Modifier,
                                                                        onMoreClickListener = {
                                                                            onMoreClick(result.toTrack().toSongEntity())
                                                                        },
                                                                        onClickListener = {
                                                                            val firstTrack = result.toTrack()
                                                                            searchViewModel.setQueueData(
                                                                                QueueData.Data(
                                                                                    listTracks = arrayListOf(firstTrack),
                                                                                    firstPlayedTrack = firstTrack,
                                                                                    playlistId = "RDAMVM${result.videoId}",
                                                                                    playlistName =
                                                                                        "\"${searchText}\" ${
                                                                                            getStringBlocking(
                                                                                                Res.string.in_search,
                                                                                            )
                                                                                        }",
                                                                                    playlistType = PlaylistType.RADIO,
                                                                                    continuation = null,
                                                                                ),
                                                                            )
                                                                            searchViewModel.loadMediaItem(firstTrack, Config.SONG_CLICK)
                                                                        },
                                                                        onAddToQueue = {
                                                                            sharedViewModel.addListToQueue(
                                                                                arrayListOf(result.toTrack()),
                                                                            )
                                                                        },
                                                                    )
                                                                }

                                                                is VideosResult -> {
                                                                    SongFullWidthItems(
                                                                        track = result.toTrack(),
                                                                        isPlaying = result.videoId == currentVideoId,
                                                                        modifier = Modifier,
                                                                        onMoreClickListener = {
                                                                            onMoreClick(result.toTrack().toSongEntity())
                                                                        },
                                                                        onClickListener = {
                                                                            val firstTrack = result.toTrack()
                                                                            searchViewModel.setQueueData(
                                                                                QueueData.Data(
                                                                                    listTracks = arrayListOf(firstTrack),
                                                                                    firstPlayedTrack = firstTrack,
                                                                                    playlistId = "RDAMVM${result.videoId}",
                                                                                    playlistName =
                                                                                        "\"${searchText}\" ${
                                                                                            getStringBlocking(
                                                                                                Res.string.in_search,
                                                                                            )
                                                                                        }",
                                                                                    playlistType = PlaylistType.RADIO,
                                                                                    continuation = null,
                                                                                ),
                                                                            )
                                                                            searchViewModel.loadMediaItem(firstTrack, Config.VIDEO_CLICK)
                                                                        },
                                                                        onAddToQueue = {
                                                                            sharedViewModel.addListToQueue(
                                                                                arrayListOf(result.toTrack()),
                                                                            )
                                                                        },
                                                                    )
                                                                }

                                                                is AlbumsResult -> {
                                                                    PlaylistFullWidthItems(
                                                                        data = result,
                                                                        onClickListener = {
                                                                            navController.navigate(
                                                                                AlbumDestination(
                                                                                    result.browseId,
                                                                                ),
                                                                            )
                                                                        },
                                                                    )
                                                                }

                                                                is ArtistsResult -> {
                                                                    ArtistFullWidthItems(
                                                                        data = result,
                                                                        onClickListener = {
                                                                            navController.navigate(
                                                                                ArtistDestination(
                                                                                    result.browseId,
                                                                                ),
                                                                            )
                                                                        },
                                                                    )
                                                                }

                                                                is PlaylistsResult -> {
                                                                    PlaylistFullWidthItems(
                                                                        data = result,
                                                                        onClickListener = {
                                                                            if (result.resultType == "Podcast") {
                                                                                navController.navigate(
                                                                                    PodcastDestination(
                                                                                        result.browseId,
                                                                                    ),
                                                                                )
                                                                            } else {
                                                                                navController.navigate(
                                                                                    PlaylistDestination(
                                                                                        result.browseId,
                                                                                    ),
                                                                                )
                                                                            }
                                                                        },
                                                                    )
                                                                }
                                                            }
                                                        }
                                                        item { Spacer(modifier = Modifier.height(150.dp)) }
                                                    }
                                                } else {
                                                    com.sakayori.music.ui.component.ErrorState(
                                                        title = stringResource(Res.string.no_results_found),
                                                        subtitle = stringResource(Res.string.try_different_search_terms),
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    is SearchScreenUIState.Error -> {
                                        Box {
                                            Column(
                                                modifier = Modifier.align(Alignment.Center),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                            ) {
                                                Text(
                                                    text = stringResource(Res.string.error_occurred),
                                                    style = typo().titleMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.fillMaxWidth(),
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Button(onClick = {
                                                    if (searchText.isNotEmpty()) {
                                                        searchViewModel.searchAll(searchText)
                                                    }
                                                }) {
                                                    Text(text = stringResource(Res.string.retry))
                                                }
                                            }
                                        }
                                    }

                                    SearchScreenUIState.Empty -> {
                                        com.sakayori.music.ui.component.ErrorState(
                                            title = stringResource(Res.string.no_results_found),
                                            subtitle = stringResource(Res.string.try_different_search_terms),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestItemRow(
    searchResult: SearchResultType,
    onItemClick: (SearchResultType) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onItemClick(searchResult) }
                .padding(vertical = 8.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val url =
            when (searchResult) {
                is SongsResult -> {
                    searchResult.thumbnails?.lastOrNull()?.url
                }

                is AlbumsResult -> {
                    searchResult.thumbnails.lastOrNull()?.url
                }

                is ArtistsResult -> {
                    searchResult.thumbnails.lastOrNull()?.url
                }

                is PlaylistsResult -> {
                    searchResult.thumbnails.lastOrNull()?.url
                }

                is VideosResult -> {
                    searchResult.thumbnails?.lastOrNull()?.url
                }

                else -> {
                    null
                }
            }

        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(4.dp)),
        ) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(url)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCacheKey(url)
                        .crossfade(true)
                        .build(),
                placeholder = painterResource(Res.drawable.holder),
                error = painterResource(Res.drawable.holder),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(
                            if (searchResult is ArtistsResult) {
                                CircleShape
                            } else {
                                RoundedCornerShape(4.dp)
                            },
                        ),
            )
        }

        Spacer(modifier = Modifier.padding(horizontal = 12.dp))

        Column(modifier = Modifier.weight(1f)) {
            val title =
                when (searchResult) {
                    is SongsResult -> {
                        searchResult.title
                    }

                    is AlbumsResult -> {
                        searchResult.title
                    }

                    is ArtistsResult -> {
                        searchResult.artist
                    }

                    is PlaylistsResult -> {
                        searchResult.title
                    }

                    is VideosResult -> {
                        searchResult.title
                    }

                    else -> {
                        null
                    }
                } ?: "Unknown"

            Text(
                text = title,
                style = typo().labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))

            val subtitle =
                when (searchResult) {
                    is SongsResult -> searchResult.artists?.map { it.name }?.connectArtists()
                    is AlbumsResult -> searchResult.artists.map { it.name }.connectArtists()
                    is PlaylistsResult -> searchResult.author.ifEmpty { "YouTube Music" }
                    is ArtistsResult -> stringResource(Res.string.artists)
                    is VideosResult -> searchResult.artists?.map { it.name }?.connectArtists()
                    else -> null
                } ?: "Unknown"

            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = typo().bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

enum class SearchUIType {
    EMPTY,
    SEARCH_HISTORY,
    SEARCH_SUGGESTIONS,
    SEARCH_RESULTS,
}
