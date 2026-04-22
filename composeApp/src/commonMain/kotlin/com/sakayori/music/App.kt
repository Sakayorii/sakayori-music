package com.sakayori.music

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.zIndex
import com.sakayori.music.ui.component.OfflineBanner
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.window.core.layout.WindowSizeClass.Companion.WIDTH_DP_MEDIUM_LOWER_BOUND
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.toUri
import com.sakayori.domain.data.player.GenericMediaItem
import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.domain.manager.DataStoreManager.Values.TRUE
import com.sakayori.logger.Logger
import com.sakayori.music.expect.Orientation
import com.sakayori.music.expect.currentOrientation
import com.sakayori.music.expect.deletePendingUpdate
import com.sakayori.music.expect.installUpdateAsset
import com.sakayori.music.expect.isValidPendingUpdate
import com.sakayori.music.expect.openUrl
import com.sakayori.music.expect.pickUpdateAssetName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.sakayori.music.update.UpdateDownloadManager
import com.sakayori.music.update.UpdateDownloadState
import androidx.compose.material3.LinearProgressIndicator
import org.koin.compose.koinInject
import com.sakayori.music.expect.ui.layerBackdrop
import com.sakayori.music.expect.ui.rememberBackdrop
import com.sakayori.music.extension.copy
import com.sakayori.music.ui.component.AppBottomNavigationBar
import com.sakayori.music.ui.component.AppNavigationRail
import com.sakayori.music.ui.component.LiquidGlassAppBottomNavigationBar
import com.sakayori.music.ui.navigation.destination.home.NotificationDestination
import com.sakayori.music.ui.navigation.destination.list.AlbumDestination
import com.sakayori.music.ui.navigation.destination.list.ArtistDestination
import com.sakayori.music.ui.navigation.destination.list.PlaylistDestination
import com.sakayori.music.ui.navigation.destination.player.FullscreenDestination
import com.sakayori.music.ui.navigation.graph.AppNavigationGraph
import com.sakayori.music.ui.screen.MiniPlayer
import com.sakayori.music.ui.screen.player.NowPlayingScreen
import com.sakayori.music.ui.screen.player.NowPlayingScreenContent
import com.sakayori.music.ui.theme.AppTheme
import com.sakayori.music.ui.theme.fontFamily
import com.sakayori.music.ui.theme.typo
import com.sakayori.music.utils.VersionManager
import com.sakayori.music.viewModel.SharedViewModel
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.format.char
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.cancel
import com.sakayori.music.generated.resources.download
import com.sakayori.music.generated.resources.install
import com.sakayori.music.generated.resources.later
import com.sakayori.music.generated.resources.download_complete
import com.sakayori.music.generated.resources.installing_message
import com.sakayori.music.generated.resources.update_size_label
import com.sakayori.music.generated.resources.no_matching_asset
import com.sakayori.music.generated.resources.good_night
import com.sakayori.music.generated.resources.sleep_timer_off
import com.sakayori.music.generated.resources.this_link_is_not_supported
import com.sakayori.music.generated.resources.unknown
import com.sakayori.music.generated.resources.update_available
import com.sakayori.music.generated.resources.update_message
import com.sakayori.music.generated.resources.version_format
import com.sakayori.music.generated.resources.yes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalTime::class, ExperimentalFoundationApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun App(viewModel: SharedViewModel = koinInject()) {
    val windowSize = currentWindowAdaptiveInfo().windowSizeClass
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val isAtRoot = currentBackStack?.destination?.route?.contains("Home") == true
    com.sakayori.music.expect.PlatformBackHandler(enabled = isAtRoot) {
        com.sakayori.music.expect.moveTaskToBack()
    }

    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val nowPlayingData by viewModel.nowPlayingState.collectAsStateWithLifecycle()
    val updateData by viewModel.updateResponse.collectAsStateWithLifecycle()
    val intent by viewModel.intent.collectAsStateWithLifecycle()
    val isOnline by com.sakayori.music.expect.networkStatusFlow().collectAsStateWithLifecycle(initialValue = true)

    val isTranslucentBottomBar by viewModel.getTranslucentBottomBar().collectAsStateWithLifecycle(DataStoreManager.FALSE)
    val isLiquidGlassEnabled by viewModel.getEnableLiquidGlass().collectAsStateWithLifecycle(DataStoreManager.FALSE)
    val isLowResourceMode by viewModel.getLowResourceMode().collectAsStateWithLifecycle(DataStoreManager.FALSE)
    val isLowEndDevice = remember { com.sakayori.music.utils.DeviceCapability.isLowEndDevice() }
    val blurEnabledGlobal = isLiquidGlassEnabled == DataStoreManager.TRUE &&
        !isLowEndDevice &&
        isLowResourceMode != DataStoreManager.TRUE
    var isShowMiniPlayer by rememberSaveable {
        mutableStateOf(true)
    }

    var isShowNowPlaylistScreen by rememberSaveable {
        mutableStateOf(false)
    }

    var isInFullscreen by rememberSaveable {
        mutableStateOf(false)
    }

    var isNavBarVisible by rememberSaveable {
        mutableStateOf(true)
    }

    var shouldShowUpdateDialog by rememberSaveable {
        mutableStateOf(false)
    }

    val hazeState =
        rememberHazeState(
            blurEnabled = blurEnabledGlobal,
        )

    LaunchedEffect(nowPlayingData) {
        isShowMiniPlayer = !(nowPlayingData?.mediaItem == null || nowPlayingData?.mediaItem == GenericMediaItem.EMPTY)
    }

    val updateDownloadManager: UpdateDownloadManager = koinInject()
    LaunchedEffect(Unit) {
        val pendingPath = viewModel.getPendingUpdateFile().first()
        val pendingTag = viewModel.getPendingUpdateTag().first()
        if (pendingPath.isNotEmpty() && pendingTag.isNotEmpty()) {
            val stillNeeded = pendingTag != "v${VersionManager.getVersionName()}"
            if (stillNeeded && isValidPendingUpdate(pendingPath)) {
                installUpdateAsset(pendingPath)
                viewModel.clearPendingUpdate()
            } else {
                deletePendingUpdate(pendingPath)
                viewModel.clearPendingUpdate()
            }
        }
        updateDownloadManager.cleanupStalePartials()
    }

    val autoUpdateEnabled by viewModel.getAutoUpdateOnRestart().collectAsStateWithLifecycle(DataStoreManager.FALSE)
    LaunchedEffect(updateData, autoUpdateEnabled) {
        val data = updateData ?: return@LaunchedEffect
        if (autoUpdateEnabled != TRUE) return@LaunchedEffect
        val picked = data.pickAssetFor(pickUpdateAssetName(data.tagName)) ?: return@LaunchedEffect
        val currentState = updateDownloadManager.state.value
        if (currentState is UpdateDownloadState.Ready && currentState.tag == data.tagName) return@LaunchedEffect
        if (currentState is UpdateDownloadState.Downloading) return@LaunchedEffect
        updateDownloadManager.start(
            url = picked.downloadUrl,
            fileName = picked.name,
            expectedSize = picked.sizeBytes,
            tag = data.tagName,
            onComplete = { path ->
                viewModel.setPendingUpdate(path, data.tagName)
            },
        )
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(30_000)
        try {
            withContext(Dispatchers.IO) {
                com.sakayori.music.utils.CacheCleaner.cleanupOldFiles()
            }
        } catch (_: Throwable) {
        }
    }

    val coilPlatformContext = LocalPlatformContext.current
    LaunchedEffect(Unit) {
        var idleMinutes = 0
        var lastTrackId = ""
        while (true) {
            kotlinx.coroutines.delay(30_000)
            val isPlaying = viewModel.controllerState.value.isPlaying
            val currentTrackId = viewModel.nowPlayingState.value?.mediaItem?.mediaId ?: ""
            if (currentTrackId.isNotEmpty() && currentTrackId != lastTrackId) {
                lastTrackId = currentTrackId
                try {
                    SingletonImageLoader.get(coilPlatformContext).memoryCache?.let { cache ->
                        val target = (cache.maxSize * 0.5).toLong()
                        cache.trimToSize(target)
                    }
                } catch (_: Throwable) {
                }
                com.sakayori.music.expect.platformRequestGc()
            }
            if (!isPlaying) {
                idleMinutes++
                if (idleMinutes >= 3) {
                    try {
                        com.sakayori.music.expect.platformRequestGc()
                    } catch (_: Throwable) {
                    }
                    idleMinutes = 0
                }
            } else {
                idleMinutes = 0
            }
        }
    }

    LaunchedEffect(intent) {
        val intent = intent ?: return@LaunchedEffect
        val data = intent.data
        Logger.d("MainActivity", "onCreate: $data")
        if (data != null) {
            if (data == "SakayoriMusic://notification".toUri()) {
                viewModel.setIntent(null)
                navController.navigate(
                    NotificationDestination,
                )
            } else if (data.host == "music.sakayori.dev") {
                val segments = data.pathSegments
                Logger.d("MainActivity", "music.sakayori.dev deep link, segments: $segments")
                viewModel.setIntent(null)
                when (segments.firstOrNull()) {
                    "play" -> {
                        segments.getOrNull(1)?.let { videoId ->
                            viewModel.loadSharedMediaItem(videoId)
                        }
                    }

                    "playlist" -> {
                        segments.getOrNull(1)?.let { playlistId ->
                            if (playlistId.startsWith("OLAK5uy_")) {
                                navController.navigate(AlbumDestination(browseId = playlistId))
                            } else if (playlistId.startsWith("VL")) {
                                navController.navigate(PlaylistDestination(playlistId = playlistId))
                            } else {
                                navController.navigate(PlaylistDestination(playlistId = "VL$playlistId"))
                            }
                        }
                    }

                    else -> {
                        viewModel.makeToast(getString(Res.string.this_link_is_not_supported))
                    }
                }
            } else if (data.host == "SakayoriMusic.org" || data.scheme == "SakayoriMusic") {
                val segments = data.pathSegments
                val appPath = if (data.scheme == "SakayoriMusic") {
                    data.host
                } else {
                    segments.getOrNull(1)
                }
                Logger.d("MainActivity", "SakayoriMusic.org deep link, appPath: $appPath")
                viewModel.setIntent(null)
                when (appPath) {
                    "watch" -> {
                        data.getQueryParameter("v")?.let { videoId ->
                            viewModel.loadSharedMediaItem(videoId)
                        }
                    }

                    "playlist" -> {
                        data.getQueryParameter("list")?.let { playlistId ->
                            if (playlistId.startsWith("OLAK5uy_")) {
                                navController.navigate(AlbumDestination(browseId = playlistId))
                            } else if (playlistId.startsWith("VL")) {
                                navController.navigate(PlaylistDestination(playlistId = playlistId))
                            } else {
                                navController.navigate(PlaylistDestination(playlistId = "VL$playlistId"))
                            }
                        }
                    }

                    "channel", "c" -> {
                        val artistId = if (data.scheme == "SakayoriMusic") {
                            segments.firstOrNull()
                        } else {
                            segments.getOrNull(2)
                        }
                        artistId?.let {
                            if (it.startsWith("UC")) {
                                navController.navigate(ArtistDestination(channelId = it))
                            } else {
                                viewModel.makeToast(getString(Res.string.this_link_is_not_supported))
                            }
                        }
                    }

                    "album" -> {
                        data.getQueryParameter("id")?.let { albumId ->
                            navController.navigate(AlbumDestination(browseId = albumId))
                        }
                    }

                    else -> {
                        viewModel.makeToast(getString(Res.string.this_link_is_not_supported))
                    }
                }
            } else {
                Logger.d("MainActivity", "onCreate: $data")
                when (val path = data.pathSegments.firstOrNull()) {
                    "playlist" ->
                        data
                            .getQueryParameter("list")
                            ?.let { playlistId ->
                                viewModel.setIntent(null)
                                if (playlistId.startsWith("OLAK5uy_")) {
                                    navController.navigate(
                                        AlbumDestination(
                                            browseId = playlistId,
                                        ),
                                    )
                                } else if (playlistId.startsWith("VL")) {
                                    navController.navigate(
                                        PlaylistDestination(
                                            playlistId = playlistId,
                                        ),
                                    )
                                } else {
                                    navController.navigate(
                                        PlaylistDestination(
                                            playlistId = "VL$playlistId",
                                        ),
                                    )
                                }
                            }

                    "channel", "c" ->
                        data.lastPathSegment?.let { artistId ->
                            if (artistId.startsWith("UC")) {
                                viewModel.setIntent(null)
                                navController.navigate(
                                    ArtistDestination(
                                        channelId = artistId,
                                    ),
                                )
                            } else {
                                viewModel.makeToast(
                                    getString(
                                        Res.string.this_link_is_not_supported,
                                    ),
                                )
                            }
                        }

                    else ->
                        when {
                            path == "watch" -> data.getQueryParameter("v")
                            data.host == "youtu.be" -> path
                            else -> null
                        }?.let { videoId ->
                            viewModel.loadSharedMediaItem(videoId)
                        }
                }
            }
        }
    }

    LaunchedEffect(updateData) {
        val response = updateData ?: return@LaunchedEffect
        if (viewModel.showedUpdateDialog &&
            response.tagName != getString(Res.string.version_format, VersionManager.getVersionName())
        ) {
            shouldShowUpdateDialog = true
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    LaunchedEffect(navBackStackEntry) {
        Logger.d("MainActivity", "Current destination: ${navBackStackEntry?.destination?.route}")
        if (navBackStackEntry?.destination?.route?.contains("FullscreenDestination") == true) {
            isShowNowPlaylistScreen = false
        }
        isInFullscreen = navBackStackEntry?.destination?.hierarchy?.any {
            it.hasRoute(FullscreenDestination::class)
        } == true
    }
    var isScrolledToTop by rememberSaveable {
        mutableStateOf(false)
    }
    val isTablet = windowSize.isWidthAtLeastBreakpoint(WIDTH_DP_MEDIUM_LOWER_BOUND)
    val isTabletLandscape = isTablet && currentOrientation() == Orientation.LANDSCAPE

    val backdrop = rememberBackdrop()

    CompositionLocalProvider(
        com.sakayori.music.extension.LocalBlurEnabled provides blurEnabledGlobal,
        com.sakayori.music.extension.LocalLowResourceMode provides (isLowResourceMode == DataStoreManager.TRUE),
    ) {
    AppTheme {
        Scaffold(
            bottomBar = {
                if (!isTablet) {
                    AnimatedVisibility(
                        isNavBarVisible,
                        enter = fadeIn() + slideInHorizontally(),
                        exit = fadeOut(),
                    ) {
                        Column {
                            AnimatedVisibility(
                                isShowMiniPlayer && isLiquidGlassEnabled == DataStoreManager.FALSE,
                                enter = fadeIn() + androidx.compose.animation.expandVertically(),
                                exit = fadeOut() + androidx.compose.animation.shrinkVertically(),
                            ) {
                                MiniPlayer(
                                    Modifier
                                        .height(56.dp)
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = 12.dp,
                                        ).padding(
                                            bottom = 4.dp,
                                        ),
                                    backdrop = backdrop,
                                    onClick = {
                                        isShowNowPlaylistScreen = true
                                    },
                                    onClose = {
                                        viewModel.stopPlayer()
                                        viewModel.isServiceRunning = false
                                    },
                                )
                            }
                            if (isLiquidGlassEnabled == TRUE) {
                                LiquidGlassAppBottomNavigationBar(
                                    navController = navController,
                                    backdrop = backdrop,
                                    viewModel = viewModel,
                                    onOpenNowPlaying = { isShowNowPlaylistScreen = true },
                                    isScrolledToTop = isScrolledToTop,
                                ) { klass ->
                                    viewModel.reloadDestination(klass)
                                }
                            } else {
                                AppBottomNavigationBar(
                                    navController = navController,
                                    isTranslucentBackground = isTranslucentBottomBar == TRUE,
                                ) { klass ->
                                    viewModel.reloadDestination(klass)
                                }
                            }
                        }
                    }
                }
            },
            content = { innerPadding ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (isLiquidGlassEnabled == TRUE && !isTablet) {
                                Modifier.layerBackdrop(backdrop)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Row(
                        Modifier.fillMaxSize(),
                    ) {
                        if (isTablet && !isInFullscreen) {
                            AppNavigationRail(
                                navController = navController,
                            ) { klass ->
                                viewModel.reloadDestination(klass)
                            }
                        }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .weight(1f),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (isLiquidGlassEnabled == TRUE && isTablet && !isInFullscreen) {
                                            Modifier.layerBackdrop(backdrop)
                                        } else {
                                            Modifier
                                        },
                                    ).hazeSource(hazeState),
                            ) {
                                OfflineBanner(
                                    isOnline = isOnline,
                                    modifier = Modifier
                                        .align(androidx.compose.ui.Alignment.TopCenter)
                                        .zIndex(10f),
                                )
                                AppNavigationGraph(
                                    innerPadding = innerPadding,
                                    navController = navController,
                                    hideNavBar = {
                                        isNavBarVisible = false
                                    },
                                    showNavBar = {
                                        isNavBarVisible = true
                                    },
                                    showNowPlayingSheet = {
                                        isShowNowPlaylistScreen = true
                                    },
                                    onScrolling = {
                                        isScrolledToTop = it
                                    },
                                )
                            }
                            this@Row.AnimatedVisibility(
                                modifier =
                                    Modifier
                                        .padding(innerPadding)
                                        .align(Alignment.BottomCenter),
                                visible = isShowMiniPlayer && isTablet && !isInFullscreen,
                                enter = fadeIn() + slideInHorizontally(),
                                exit = fadeOut(),
                            ) {
                                MiniPlayer(
                                    if (getPlatform() == Platform.Android) {
                                        Modifier
                                            .height(56.dp)
                                            .fillMaxWidth(0.8f)
                                            .padding(
                                                horizontal = 12.dp,
                                            ).padding(
                                                bottom = 4.dp,
                                            )
                                    } else {
                                        Modifier
                                            .fillMaxWidth()
                                            .height(84.dp)
                                            .background(Color.Transparent)
                                            .hazeEffect(hazeState, style = HazeMaterials.ultraThin()) {
                                                blurEnabled = blurEnabledGlobal
                                            }
                                    },
                                    backdrop = backdrop,
                                    onClick = {
                                        isShowNowPlaylistScreen = true
                                    },
                                    onClose = {
                                        viewModel.stopPlayer()
                                        viewModel.isServiceRunning = false
                                    },
                                )
                            }
                        }
                        if (isTablet && isTabletLandscape && !isInFullscreen) {
                            AnimatedVisibility(
                                isShowNowPlaylistScreen,
                                enter = expandHorizontally() + fadeIn(),
                                exit = fadeOut() + shrinkHorizontally(),
                            ) {
                                Row(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(0.35f),
                                ) {
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        Modifier
                                            .padding(
                                                innerPadding.copy(
                                                    start = 0.dp,
                                                    top = 0.dp,
                                                    bottom = 0.dp,
                                                ),
                                            ).clip(
                                                RoundedCornerShape(12.dp),
                                            ),
                                    ) {
                                        NowPlayingScreenContent(
                                            navController = navController,
                                            sharedViewModel = viewModel,
                                            isExpanded = true,
                                            dismissIcon = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                            onSwipeEnabledChange = {},
                                        ) {
                                            isShowNowPlaylistScreen = false
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (isShowNowPlaylistScreen && !isTabletLandscape) {
                    NowPlayingScreen(
                        navController = navController,
                    ) {
                        isShowNowPlaylistScreen = false
                    }
                }

                if (sleepTimerState.isDone) {
                    Logger.w("MainActivity", "Sleep Timer Done: $sleepTimerState")
                    AlertDialog(
                        properties =
                            DialogProperties(
                                dismissOnBackPress = false,
                                dismissOnClickOutside = false,
                            ),
                        onDismissRequest = {
                            viewModel.stopSleepTimer()
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                viewModel.stopSleepTimer()
                            }) {
                                Text(
                                    stringResource(Res.string.yes),
                                    style = typo().bodySmall,
                                )
                            }
                        },
                        text = {
                            Text(
                                stringResource(Res.string.sleep_timer_off),
                                style = typo().labelSmall,
                            )
                        },
                        title = {
                            Text(
                                stringResource(Res.string.good_night),
                                style = typo().bodySmall,
                            )
                        },
                    )
                }

                if (shouldShowUpdateDialog) {
                    val response = updateData ?: return@Scaffold
                    val downloadManager: UpdateDownloadManager = koinInject()
                    val downloadState by downloadManager.state.collectAsStateWithLifecycle()
                    val pickedAsset = remember(response.tagName, response.assets) {
                        response.pickAssetFor(pickUpdateAssetName(response.tagName))
                    }
                    var installTriggered by remember { mutableStateOf(false) }
                    val isDownloading = downloadState is UpdateDownloadState.Downloading
                    val isReady = downloadState is UpdateDownloadState.Ready
                    val isBusy = isDownloading || installTriggered
                    AlertDialog(
                        properties =
                            DialogProperties(
                                dismissOnBackPress = !isBusy,
                                dismissOnClickOutside = !isBusy,
                            ),
                        onDismissRequest = {
                            if (!isBusy) {
                                shouldShowUpdateDialog = false
                                viewModel.showedUpdateDialog = false
                            }
                        },
                        confirmButton = {
                            when {
                                installTriggered -> Unit
                                isReady -> {
                                    TextButton(
                                        onClick = {
                                            val ready = downloadState as UpdateDownloadState.Ready
                                            installTriggered = true
                                            installUpdateAsset(ready.filePath)
                                        },
                                    ) {
                                        Text(
                                            stringResource(Res.string.install),
                                            style = typo().bodySmall,
                                            color = Color(0xFF00BCD4),
                                        )
                                    }
                                }
                                isDownloading -> {
                                    TextButton(onClick = { downloadManager.cancel() }) {
                                        Text(
                                            stringResource(Res.string.cancel),
                                            style = typo().bodySmall,
                                        )
                                    }
                                }
                                else -> {
                                    TextButton(
                                        onClick = {
                                            if (pickedAsset != null) {
                                                downloadManager.start(
                                                    url = pickedAsset.downloadUrl,
                                                    fileName = pickedAsset.name,
                                                    expectedSize = pickedAsset.sizeBytes,
                                                    tag = response.tagName,
                                                )
                                            } else {
                                                shouldShowUpdateDialog = false
                                                viewModel.showedUpdateDialog = false
                                                openUrl("https://music.sakayori.dev/download")
                                            }
                                        },
                                    ) {
                                        Text(
                                            stringResource(Res.string.download),
                                            style = typo().bodySmall,
                                            color = Color(0xFF00BCD4),
                                        )
                                    }
                                }
                            }
                        },
                        dismissButton = {
                            if (!isBusy && !isReady) {
                                TextButton(
                                    onClick = {
                                        shouldShowUpdateDialog = false
                                        viewModel.showedUpdateDialog = false
                                    },
                                ) {
                                    Text(
                                        stringResource(Res.string.later),
                                        style = typo().bodySmall,
                                    )
                                }
                            }
                        },
                        title = {
                            Text(
                                stringResource(Res.string.update_available),
                                style = typo().labelSmall,
                            )
                        },
                        text = {
                            val formatted =
                                response.releaseTime?.let { input ->
                                    try {
                                        val instant = kotlin.time.Instant.parse(input)
                                        val dateTime = instant.toLocalDateTime(TimeZone.currentSystemDefault())
                                        dateTime.format(
                                            LocalDateTime.Format {
                                                day()
                                                char(' ')
                                                monthName(MonthNames.ENGLISH_ABBREVIATED)
                                                char(' ')
                                                year()
                                                char(' ')
                                                hour()
                                                char(':')
                                                minute()
                                                char(':')
                                                second()
                                            },
                                        )
                                    } catch (e: Exception) {
                                        stringResource(Res.string.unknown)
                                    }
                                } ?: stringResource(Res.string.unknown)

                            val updateMessage = stringResource(
                                Res.string.update_message,
                                response.tagName,
                                formatted,
                            )
                            Column(
                                Modifier
                                    .heightIn(
                                        max = 150.dp,
                                    ).verticalScroll(
                                        rememberScrollState(),
                                    ),
                            ) {
                                Text(
                                    text = updateMessage,
                                    style = typo().labelMedium,
                                    modifier =
                                        Modifier.padding(
                                            vertical = 8.dp,
                                        ),
                                )
                                if (installTriggered) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color(0xFF00BCD4),
                                    )
                                    Text(
                                        text = stringResource(Res.string.installing_message),
                                        style = typo().labelMedium,
                                        color = Color(0xFF00BCD4),
                                        modifier = Modifier.padding(top = 6.dp),
                                    )
                                } else {
                                    when (val st = downloadState) {
                                        is UpdateDownloadState.Downloading -> {
                                            val progress = if (st.totalBytes > 0L) {
                                                st.bytesDownloaded.toFloat() / st.totalBytes.toFloat()
                                            } else 0f
                                            Spacer(Modifier.height(8.dp))
                                            LinearProgressIndicator(
                                                progress = { progress.coerceIn(0f, 1f) },
                                                modifier = Modifier.fillMaxWidth(),
                                                color = Color(0xFF00BCD4),
                                            )
                                            Text(
                                                text = "${(progress * 100).toInt()}%  ·  ${st.bytesDownloaded / 1024 / 1024}MB / ${st.totalBytes / 1024 / 1024}MB",
                                                style = typo().bodySmall,
                                                modifier = Modifier.padding(top = 6.dp),
                                            )
                                        }
                                        is UpdateDownloadState.Ready -> {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = stringResource(Res.string.download_complete),
                                                style = typo().labelMedium,
                                                color = Color(0xFF00BCD4),
                                            )
                                        }
                                        is UpdateDownloadState.Failed -> {
                                            Spacer(Modifier.height(8.dp))
                                            Text(
                                                text = st.reason,
                                                style = typo().bodySmall,
                                                color = Color(0xFFE57373),
                                            )
                                        }
                                        UpdateDownloadState.Idle -> {
                                            if (pickedAsset == null) {
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = stringResource(Res.string.no_matching_asset),
                                                    style = typo().bodySmall,
                                                    color = Color(0xFFFFAB40),
                                                )
                                            } else {
                                                Spacer(Modifier.height(4.dp))
                                                Text(
                                                    text = "${stringResource(Res.string.update_size_label)}: ${pickedAsset.sizeBytes / 1024 / 1024} MB  ·  ${pickedAsset.name}",
                                                    style = typo().bodySmall,
                                                    color = Color.White.copy(alpha = 0.55f),
                                                )
                                            }
                                        }
                                    }
                                }
                                Markdown(
                                    response.body,
                                    typography =
                                        markdownTypography(
                                            h1 = typo().labelLarge,
                                            h2 = typo().labelMedium,
                                            h3 = typo().labelSmall,
                                            text = typo().bodySmall,
                                            bullet = typo().bodySmall,
                                            paragraph = typo().bodySmall,
                                            textLink =
                                                TextLinkStyles(
                                                    SpanStyle(
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Normal,
                                                        fontFamily = fontFamily(),
                                                        textDecoration = TextDecoration.Underline,
                                                    ),
                                                ),
                                        ),
                                )
                            }
                        },
                    )
                }
            },
        )
    }
    }
}
