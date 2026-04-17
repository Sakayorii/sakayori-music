package com.sakayori.music.ui.screen.home.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sakayori.common.Config
import com.sakayori.domain.data.entities.SongEntity
import com.sakayori.domain.mediaservice.handler.PlaylistType
import com.sakayori.domain.mediaservice.handler.QueueData
import com.sakayori.domain.utils.LocalResource
import com.sakayori.domain.utils.connectArtists
import com.sakayori.domain.utils.toArrayListTrack
import com.sakayori.domain.utils.toTrack
import com.sakayori.logger.Logger
import com.sakayori.music.extension.getScreenSizeInfo
import com.sakayori.music.extension.getStringBlocking
import com.sakayori.music.ui.component.BarChart
import com.sakayori.music.ui.component.BarChartEntry
import com.sakayori.music.ui.component.CenterLoadingBox
import com.sakayori.music.ui.component.EndOfPage
import com.sakayori.music.ui.component.FiveImagesComponent
import com.sakayori.music.ui.component.HorizontalBarChart
import com.sakayori.music.ui.component.ImageData
import com.sakayori.music.ui.component.NowPlayingBottomSheet
import com.sakayori.music.ui.component.SongFullWidthItems
import com.sakayori.music.ui.navigation.destination.home.RecentlySongsDestination
import com.sakayori.music.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.sakayori.music.ui.navigation.destination.list.AlbumDestination
import com.sakayori.music.ui.navigation.destination.list.ArtistDestination
import com.sakayori.music.ui.screen.library.LibraryDynamicPlaylistType
import com.sakayori.music.ui.theme.typo
import com.sakayori.music.viewModel.AnalyticsUiState
import com.sakayori.music.viewModel.AnalyticsViewModel
import com.sakayori.music.viewModel.SharedViewModel
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.artists
import com.sakayori.music.generated.resources.date_range
import com.sakayori.music.generated.resources.last_30_days
import com.sakayori.music.generated.resources.last_7_days
import com.sakayori.music.generated.resources.last_90_days
import com.sakayori.music.generated.resources.lower_plays
import com.sakayori.music.generated.resources.more
import com.sakayori.music.generated.resources.no_data_analytics
import com.sakayori.music.generated.resources.seconds
import com.sakayori.music.generated.resources.songs_played
import com.sakayori.music.generated.resources.this_year
import com.sakayori.music.generated.resources.top_song
import com.sakayori.music.generated.resources.total_listened_time
import com.sakayori.music.generated.resources.your_recently_played
import com.sakayori.music.generated.resources.your_top_albums
import com.sakayori.music.generated.resources.your_top_artists
import com.sakayori.music.generated.resources.your_top_tracks

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun AnalyticsScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    analyticsViewModel: AnalyticsViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val density = LocalDensity.current
    val screenSizeInfo = getScreenSizeInfo()
    val uiState by analyticsViewModel.analyticsUIState.collectAsStateWithLifecycle()
    val playingTrack by sharedViewModel.nowPlayingState.map { it?.track?.videoId }.collectAsState(null)

    var currentItem by remember {
        mutableStateOf<SongEntity?>(null)
    }
    var itemBottomSheetShow by remember {
        mutableStateOf(false)
    }

    val onItemMoreClick: (song: SongEntity) -> Unit = {
        currentItem = it
        itemBottomSheetShow = true
    }

    val onArtistClick: (channelId: String) -> Unit = {
        navController.navigate(
            ArtistDestination(
                channelId = it,
            ),
        )
    }

    val onAlbumClick: (browseId: String) -> Unit = {
        navController.navigate(
            AlbumDestination(
                browseId = it,
            ),
        )
    }

    if (itemBottomSheetShow && currentItem != null) {
        val track = currentItem ?: return
        NowPlayingBottomSheet(
            onDismiss = {
                itemBottomSheetShow = false
                currentItem = null
            },
            navController = navController,
            song = track,
        )
    }



    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize(),
        ) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height((screenSizeInfo.hDP / 2.5).dp),
                ) {
                    when (val topTracks = uiState.topTracks) {
                        is LocalResource.Success if (!topTracks.data.isNullOrEmpty()) -> {
                            val topTrack = topTracks.data?.firstOrNull() ?: return@Box
                            AsyncImage(
                                model =
                                    ImageRequest
                                        .Builder(LocalPlatformContext.current)
                                        .data(topTrack.second.thumbnails)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .diskCacheKey(topTrack.second.thumbnails)
                                        .crossfade(550)
                                        .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .fillMaxSize(),
                            )
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush =
                                            Brush.verticalGradient(
                                                colors =
                                                    listOf(
                                                        Color.Transparent,
                                                        Color.Black.copy(
                                                            alpha = 0.8f,
                                                        ),
                                                        Color.Black,
                                                    ),
                                                startY = (screenSizeInfo.hPX / 2.5f) * 3 / 4,
                                            ),
                                    ),
                            )
                            Column(
                                modifier =
                                    Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.top_song),
                                    style = typo().titleLarge,
                                    color = Color.White,
                                    maxLines = 1,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            topTrack.second.title,
                                            style = typo().labelMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                        )
                                        Text(
                                            topTrack.second.artistName?.connectArtists() ?: "",
                                            style = typo().bodyMedium,
                                            maxLines = 1,
                                        )
                                    }
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Text(
                                            "Listened time",
                                            style = typo().bodyMedium,
                                            color = Color.White,
                                            maxLines = 1,
                                        )
                                        Text(
                                            "${topTrack.first.totalListeningTime} seconds",
                                            style = typo().bodyLarge,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }
                        }

                        is LocalResource.Loading -> {
                            CenterLoadingBox(
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        else -> {
                            Box(Modifier.fillMaxSize()) {
                                Text(
                                    stringResource(Res.string.no_data_analytics),
                                    modifier = Modifier.align(Alignment.Center),
                                    style = typo().bodyLarge,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    when (val scrobblesCount = uiState.scrobblesCount) {
                        is LocalResource.Success -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(Res.string.songs_played),
                                    style = typo().bodyMedium,
                                    textDecoration = TextDecoration.Underline,
                                    color = Color.White,
                                    maxLines = 1,
                                )
                                Text(
                                    "${scrobblesCount.data ?: 0}",
                                    style = typo().bodyLarge,
                                    maxLines = 1,
                                )
                            }
                        }

                        else -> {}
                    }

                    when (val artistCount = uiState.artistCount) {
                        is LocalResource.Success -> {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(Res.string.artists),
                                    style = typo().bodyMedium,
                                    textDecoration = TextDecoration.Underline,
                                    color = Color.White,
                                    maxLines = 1,
                                )
                                Text(
                                    "${artistCount.data ?: 0}",
                                    style = typo().bodyLarge,
                                    maxLines = 1,
                                )
                            }
                        }

                        else -> {}
                    }

                    when (val totalPlayedTime = uiState.totalListenTimeInSeconds) {
                        is LocalResource.Success -> {
                            val totalSec = totalPlayedTime.data ?: 0L
                            val hrs = totalSec / 3600
                            val mins = (totalSec % 3600) / 60
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    stringResource(Res.string.total_listened_time),
                                    style = typo().bodyMedium,
                                    textDecoration = TextDecoration.Underline,
                                    color = Color.White,
                                    maxLines = 1,
                                )
                                Text(
                                    "${hrs}h ${mins}m",
                                    style = typo().bodyLarge,
                                    maxLines = 1,
                                )
                            }
                        }

                        else -> {}
                    }
                }
            }

            item {
                when (val recentlyRecord = uiState.recentlyRecord) {
                    is LocalResource.Success if (!recentlyRecord.data.isNullOrEmpty()) -> {
                        val records = recentlyRecord.data ?: return@item
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 24.dp)
                                        .padding(top = 12.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.your_recently_played),
                                    style = typo().labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        navController.navigate(RecentlySongsDestination)
                                    },
                                    colors =
                                        ButtonDefaults
                                            .textButtonColors()
                                            .copy(
                                                contentColor = Color.White,
                                            ),
                                ) {
                                    Text(stringResource(Res.string.more), style = typo().bodySmall)
                                }
                            }

                            records.forEach { pair ->
                                val song = pair.second.toTrack()
                                SongFullWidthItems(
                                    track = song,
                                    isPlaying = song.videoId == playingTrack,
                                    modifier = Modifier.fillMaxWidth(),
                                    onMoreClickListener = {
                                        onItemMoreClick(pair.second)
                                    },
                                    onClickListener = {
                                        val targetList = records.map { it.second }
                                        val playTrack = pair.second
                                        with(sharedViewModel) {
                                            setQueueData(
                                                QueueData.Data(
                                                    listTracks = targetList.toArrayListTrack(),
                                                    firstPlayedTrack = playTrack.toTrack(),
                                                    playlistId = null,
                                                    playlistName = getStringBlocking(Res.string.your_recently_played),
                                                    playlistType = PlaylistType.RADIO,
                                                    continuation = null,
                                                ),
                                            )
                                            loadMediaItem(
                                                playTrack.toTrack(),
                                                Config.PLAYLIST_CLICK,
                                                targetList.indexOf(playTrack).coerceAtLeast(0),
                                            )
                                        }
                                    },
                                    onAddToQueue = {
                                        sharedViewModel.addListToQueue(
                                            arrayListOf(song),
                                        )
                                    },
                                    rightView = {
                                        Text(
                                            text =
                                                pair.first.timestamp.format(
                                                    LocalDateTime.Format {
                                                        hour()
                                                        char(':')
                                                        minute()
                                                        chars(" - ")
                                                        day()
                                                        char(' ')
                                                        monthName(
                                                            MonthNames.ENGLISH_FULL,
                                                        )
                                                        char(' ')
                                                        year()
                                                    },
                                                ),
                                            style = typo().bodySmall,
                                        )
                                    },
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }

            item {
                when (uiState.topArtists) {
                    is LocalResource.Success if (!uiState.topArtists.data.isNullOrEmpty()) -> {
                        val artists =
                            uiState.topArtists.data?.let {
                                if (it.size > 5) it.subList(0, 5) else it
                            } ?: return@item
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 24.dp)
                                        .padding(top = 12.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.your_top_artists),
                                    style = typo().labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        navController.navigate(
                                            LibraryDynamicPlaylistDestination(
                                                type = LibraryDynamicPlaylistType.TopArtists.toStringParams(),
                                            ),
                                        )
                                    },
                                    colors =
                                        ButtonDefaults
                                            .textButtonColors()
                                            .copy(
                                                contentColor = Color.White,
                                            ),
                                ) {
                                    Text(stringResource(Res.string.more), style = typo().bodySmall)
                                }
                            }
                            FiveImagesComponent(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                images =
                                    artists.map {
                                        ImageData(
                                            imageUrl = it.second.thumbnails ?: "",
                                            title = it.second.name,
                                            subtitle = "${it.first.playCount} ${stringResource(Res.string.lower_plays)}",
                                            onClick = {
                                                onArtistClick(it.second.channelId)
                                            },
                                        )
                                    },
                            )
                        }
                    }

                    else -> {}
                }
            }

            item {
                when (uiState.topAlbums) {
                    is LocalResource.Success if (!uiState.topAlbums.data.isNullOrEmpty()) -> {
                        val albums =
                            uiState.topAlbums.data?.let {
                                if (it.size > 5) it.subList(0, 5) else it
                            } ?: return@item
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 24.dp)
                                        .padding(top = 12.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.your_top_albums),
                                    style = typo().labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        navController.navigate(
                                            LibraryDynamicPlaylistDestination(
                                                type = LibraryDynamicPlaylistType.TopAlbums.toStringParams(),
                                            ),
                                        )
                                    },
                                    colors =
                                        ButtonDefaults
                                            .textButtonColors()
                                            .copy(
                                                contentColor = Color.White,
                                            ),
                                ) {
                                    Text(stringResource(Res.string.more), style = typo().bodySmall)
                                }
                            }
                            FiveImagesComponent(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp),
                                images =
                                    albums.map {
                                        ImageData(
                                            imageUrl = it.second.thumbnails ?: "",
                                            title = it.second.title,
                                            subtitle = it.second.artistName?.connectArtists() ?: "",
                                            thirdTitle = "${it.first.playCount} ${stringResource(Res.string.lower_plays)}",
                                            onClick = {
                                                onAlbumClick(it.second.browseId)
                                            },
                                        )
                                    },
                            )
                        }
                    }

                    else -> {}
                }
            }

            item {
                when (val topTracks = uiState.topTracks) {
                    is LocalResource.Success if (!topTracks.data.isNullOrEmpty()) -> {
                        val tracks =
                            topTracks.data?.let {
                                if (it.size > 5) it.subList(0, 5) else it
                            } ?: return@item
                        if (tracks.isEmpty()) return@item
                        val maxPlays = (tracks.maxOfOrNull { it.first.playCount } ?: 1).coerceAtLeast(1)
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier =
                                    Modifier
                                        .padding(horizontal = 24.dp)
                                        .padding(top = 12.dp),
                            ) {
                                Text(
                                    text = stringResource(Res.string.your_top_tracks),
                                    style = typo().labelMedium,
                                    color = Color.White,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(
                                    onClick = {
                                        navController.navigate(
                                            LibraryDynamicPlaylistDestination(
                                                type = LibraryDynamicPlaylistType.TopTracks.toStringParams(),
                                            ),
                                        )
                                    },
                                    colors =
                                        ButtonDefaults
                                            .textButtonColors()
                                            .copy(
                                                contentColor = Color.White,
                                            ),
                                ) {
                                    Text(stringResource(Res.string.more), style = typo().bodySmall)
                                }
                            }

                            tracks.forEach { pair ->
                                val song = pair.second.toTrack()
                                SongFullWidthItems(
                                    track = song,
                                    isPlaying = song.videoId == playingTrack,
                                    modifier = Modifier.fillMaxWidth(),
                                    onMoreClickListener = {
                                        onItemMoreClick(pair.second)
                                    },
                                    onClickListener = {
                                        val targetList = tracks.map { it.second }
                                        val playTrack = pair.second
                                        with(sharedViewModel) {
                                            setQueueData(
                                                QueueData.Data(
                                                    listTracks = targetList.toArrayListTrack(),
                                                    firstPlayedTrack = playTrack.toTrack(),
                                                    playlistId = null,
                                                    playlistName = getStringBlocking(Res.string.your_top_tracks),
                                                    playlistType = PlaylistType.RADIO,
                                                    continuation = null,
                                                ),
                                            )
                                            loadMediaItem(
                                                playTrack.toTrack(),
                                                Config.PLAYLIST_CLICK,
                                                targetList.indexOf(playTrack).coerceAtLeast(0),
                                            )
                                        }
                                    },
                                    onAddToQueue = {
                                        sharedViewModel.addListToQueue(
                                            arrayListOf(song),
                                        )
                                    },
                                    rightView = {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(0.4f),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Text(
                                                text = "${pair.first.totalListeningTime} ${stringResource(Res.string.seconds)}",
                                                style = typo().bodySmall,
                                            )
                                            Box(Modifier.fillMaxWidth()) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .wrapContentHeight()
                                                            .fillMaxWidth(pair.first.playCount.toFloat() / maxPlays)
                                                            .clip(CircleShape)
                                                            .background(Color.DarkGray),
                                                ) {
                                                    Text(
                                                        text = "",
                                                        style = typo().bodySmall,
                                                        modifier =
                                                            Modifier
                                                                .align(Alignment.CenterStart)
                                                                .padding(horizontal = 4.dp),
                                                    )
                                                }
                                                Text(
                                                    text = "${pair.first.playCount} ${stringResource(Res.string.lower_plays)}",
                                                    style = typo().bodySmall,
                                                    modifier =
                                                        Modifier
                                                            .align(Alignment.CenterStart)
                                                            .padding(horizontal = 4.dp),
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }

            item {
                when (uiState.scrobblesLineChart) {
                    is LocalResource.Success if (!uiState.scrobblesLineChart.data.isNullOrEmpty()) -> {
                        val data = uiState.scrobblesLineChart.data ?: return@item
                        if (data.isEmpty()) return@item
                        val isMonthView = data.firstOrNull()?.first is AnalyticsUiState.ChartType.Month
                        Column(
                            modifier =
                                Modifier
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 12.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.date_range),
                                style = typo().labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            if (isMonthView) {
                                HorizontalBarChart(
                                    entries = data.map { (chartType, count) ->
                                        val label = when (chartType) {
                                            is AnalyticsUiState.ChartType.Month ->
                                                "${chartType.month.name.take(3)} ${chartType.year}"
                                            is AnalyticsUiState.ChartType.Day ->
                                                chartType.day.day.toString()
                                        }
                                        BarChartEntry(label, count)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            } else {
                                val displayData = data.reversed()
                                val maxLabels = if (displayData.size > 14) 10 else displayData.size
                                val step = (displayData.size.toFloat() / maxLabels).toInt().coerceAtLeast(1)
                                BarChart(
                                    entries = displayData.mapIndexed { index, (chartType, count) ->
                                        val label = when (chartType) {
                                            is AnalyticsUiState.ChartType.Day -> {
                                                if (index % step == 0 || index == displayData.lastIndex) {
                                                    "${chartType.day.day}/${chartType.day.monthNumber}"
                                                } else ""
                                            }
                                            is AnalyticsUiState.ChartType.Month ->
                                                chartType.month.name.take(3)
                                        }
                                        BarChartEntry(label, count)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }

            item {
                EndOfPage()
            }
        }

        var dayRangeMenuExpanded by rememberSaveable { mutableStateOf(false) }
        TopAppBar(
            modifier =
                Modifier
                    .align(Alignment.TopCenter),
            title = {},
            navigationIcon = {
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .wrapContentSize()
                            .align(Alignment.TopStart)
                            .padding(
                                12.dp,
                            ),
                ) {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        colors =
                            IconButtonDefaults.iconButtonColors().copy(
                                containerColor =
                                    Color.DarkGray.copy(
                                        alpha = 0.8f,
                                    ),
                                contentColor =
                                    Color.White.copy(
                                        alpha = 0.6f,
                                    ),
                            ),
                    ) {
                        Icon(Icons.Default.ArrowBackIosNew, "Back")
                    }
                }
            },
            actions = {
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .wrapContentSize()
                            .padding(
                                12.dp,
                            ),
                ) {
                    IconButton(
                        onClick = {
                            dayRangeMenuExpanded = !dayRangeMenuExpanded
                        },
                        colors =
                            IconButtonDefaults.iconButtonColors().copy(
                                containerColor =
                                    Color.DarkGray.copy(
                                        alpha = 0.8f,
                                    ),
                                contentColor =
                                    Color.White.copy(
                                        alpha = 0.6f,
                                    ),
                            ),
                    ) {
                        Box {
                            Icon(Icons.Rounded.CalendarToday, "Analytics", tint = Color.White)
                            Text(
                                when (uiState.dayRange) {
                                    AnalyticsUiState.DayRange.LAST_7_DAYS -> "7d"
                                    AnalyticsUiState.DayRange.LAST_30_DAYS -> "30d"
                                    AnalyticsUiState.DayRange.LAST_90_DAYS -> "90d"
                                    AnalyticsUiState.DayRange.THIS_YEAR -> "1y"
                                },
                                style = typo().bodySmall.copy(fontSize = 8.sp),
                                color = Color.White,
                                modifier = Modifier.align(Alignment.Center),
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = dayRangeMenuExpanded,
                        onDismissRequest = { dayRangeMenuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.last_7_days), style = typo().labelSmall) },
                            onClick = {
                                analyticsViewModel.setDayRange(AnalyticsUiState.DayRange.LAST_7_DAYS)
                                dayRangeMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.last_30_days), style = typo().labelSmall) },
                            onClick = {
                                analyticsViewModel.setDayRange(AnalyticsUiState.DayRange.LAST_30_DAYS)
                                dayRangeMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.last_90_days), style = typo().labelSmall) },
                            onClick = {
                                analyticsViewModel.setDayRange(AnalyticsUiState.DayRange.LAST_90_DAYS)
                                dayRangeMenuExpanded = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.this_year), style = typo().labelSmall) },
                            onClick = {
                                analyticsViewModel.setDayRange(AnalyticsUiState.DayRange.THIS_YEAR)
                                dayRangeMenuExpanded = false
                            },
                        )
                    }
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
        )
    }
}
