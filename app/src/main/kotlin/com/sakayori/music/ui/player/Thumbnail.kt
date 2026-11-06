/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.player

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.asPainter
import coil3.compose.rememberAsyncImagePainter
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.toBitmap
import com.sakayori.music.LocalListenTogetherManager
import com.sakayori.music.LocalPlayerConnection
import com.sakayori.music.R
import com.sakayori.music.constants.CropAlbumArtKey
import com.sakayori.music.constants.HidePlayerThumbnailKey
import com.sakayori.music.constants.PlayerBackgroundStyle
import com.sakayori.music.constants.PlayerBackgroundStyleKey
import com.sakayori.music.constants.PlayerHorizontalPadding
import com.sakayori.music.constants.SeekExtraSeconds
import com.sakayori.music.constants.SwipeThumbnailKey
import com.sakayori.music.constants.ThumbnailCornerRadius
import com.sakayori.music.extensions.PLAYLIST_ID_EXTRA
import com.sakayori.music.extensions.PLAYLIST_OCCURRENCE_EXTRA
import com.sakayori.music.extensions.metadata
import com.sakayori.music.listentogether.RoomRole
import com.sakayori.music.ui.component.CastButton
import com.sakayori.music.utils.rememberEnumPreference
import com.sakayori.music.utils.rememberPreference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import coil3.Image as CoilImage

/**
 * Pre-calculated thumbnail dimensions to avoid repeated calculations during recomposition.
 * All values are computed once and cached.
 */
@Immutable
data class ThumbnailDimensions(
    val itemWidth: Dp,
    val containerSize: Dp,
    val thumbnailSize: Dp,
    val cornerRadius: Dp
)

/**
 * Cached media items data to prevent recalculation on every recomposition.
 */
@Immutable
data class MediaItemsData(
    val items: List<MediaItem>,
    val currentIndex: Int
)

private const val ARTWORK_OCCURRENCE_EXTRA = "sakayori.artwork.occurrence"

internal fun playerArtworkKey(item: MediaItem): String {
    val extras = item.mediaMetadata.extras
    extras?.getString(ARTWORK_OCCURRENCE_EXTRA)?.let { return it }
    val occurrence = extras?.getString(PLAYLIST_OCCURRENCE_EXTRA)
    if (occurrence != null) {
        val playlist = extras.getString(PLAYLIST_ID_EXTRA).orEmpty()
        return "playlist:${playlist.length}:$playlist:$occurrence"
    }
    return item.metadata?.setVideoId?.takeIf { it.isNotBlank() }?.let { "set:$it" }
        ?: item.mediaId.ifEmpty { "unknown_${item.hashCode()}" }
}

/**
 * Calculate thumbnail dimensions once based on container size.
 * This function is marked as @Stable to indicate it produces stable results.
 * In landscape mode, uses the smaller dimension (height) to ensure square thumbnail fits.
 */
@Stable
private fun calculateThumbnailDimensions(
    containerWidth: Dp,
    containerHeight: Dp = containerWidth,
    horizontalPadding: Dp = PlayerHorizontalPadding,
    cornerRadius: Dp = ThumbnailCornerRadius,
    isLandscape: Boolean = false
): ThumbnailDimensions {
    // In landscape, use height as the constraining dimension for a square thumbnail
    val effectiveSize = if (isLandscape) {
        minOf(containerWidth, containerHeight) - (horizontalPadding * 2)
    } else {
        containerWidth - (horizontalPadding * 2)
    }
    return ThumbnailDimensions(
        itemWidth = containerWidth,
        containerSize = containerWidth,
        thumbnailSize = effectiveSize,
        cornerRadius = cornerRadius * 2
    )
}

/**
 * Get media items for the thumbnail carousel.
 * Calculates previous, current, and next items based on shuffle mode.
 */
@Stable
internal fun getMediaItems(
    player: Player,
    swipeThumbnail: Boolean,
    artworkKeys: ArtworkItemKeys = ArtworkItemKeys(),
): MediaItemsData {
    val timeline = player.currentTimeline
    val currentIndex = player.currentMediaItemIndex
    val shuffleModeEnabled = player.shuffleModeEnabled
    
    val currentMediaItem = try {
        player.currentMediaItem
    } catch (e: Exception) { null }
    
    val previousIndex = if (swipeThumbnail && !timeline.isEmpty) {
        timeline.getPreviousWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
    } else C.INDEX_UNSET
    val previousMediaItem = if (swipeThumbnail && !timeline.isEmpty) {
        if (previousIndex != C.INDEX_UNSET) {
            try { player.getMediaItemAt(previousIndex) } catch (e: Exception) { null }
        } else null
    } else null

    val nextIndex = if (swipeThumbnail && !timeline.isEmpty) {
        timeline.getNextWindowIndex(
            currentIndex,
            Player.REPEAT_MODE_OFF,
            shuffleModeEnabled
        )
    } else C.INDEX_UNSET
    val nextMediaItem = if (swipeThumbnail && !timeline.isEmpty) {
        if (nextIndex != C.INDEX_UNSET) {
            try { player.getMediaItemAt(nextIndex) } catch (e: Exception) { null }
        } else null
    } else null

    val indexedItems = listOfNotNull(
        previousMediaItem?.let { previousIndex to it },
        currentMediaItem?.let { currentIndex to it },
        nextMediaItem?.let { nextIndex to it },
    )
    val items = if (indexedItems.all { it.first in 0 until timeline.windowCount }) {
        val keys = artworkKeys.resolve(indexedItems.map { (index, item) ->
            timeline.getWindow(index, Timeline.Window()).uid to playerArtworkKey(item)
        })
        indexedItems.mapIndexed { index, (_, item) ->
            if (keys[index] == playerArtworkKey(item)) item else {
                // This is display-only metadata. The real player/queue is not modified.
                val extras = item.mediaMetadata.extras?.let(::Bundle) ?: Bundle()
                extras.putString(ARTWORK_OCCURRENCE_EXTRA, keys[index])
                item.buildUpon().setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build()).build()
            }
        }
    } else indexedItems.map { it.second }
    val currentMediaIndex = when {
        currentMediaItem == null -> -1
        previousMediaItem != null -> 1
        else -> 0
    }
    
    return MediaItemsData(items, currentMediaIndex)
}

/**
 * Get text color based on player background style.
 * Computed once per background style change.
 */
@Stable
@Composable
private fun getTextColor(playerBackground: PlayerBackgroundStyle): Color {
    return when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
        PlayerBackgroundStyle.BLUR -> Color.White
        PlayerBackgroundStyle.GRADIENT -> Color.White
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    isPlayerExpanded: () -> Boolean = { true },
    isLandscape: Boolean = false,
    isListenTogetherGuest: Boolean = false,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    if (com.sakayori.music.BuildConfig.PLAYER_TRACE_ENABLED) android.os.Trace.beginSection("Sakayori.Player.thumbnail")
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current

    // Collect states
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val error by playerConnection.error.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val currentMediaItemIndex by playerConnection.currentMediaItemIndex.collectAsStateWithLifecycle()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()

    // Preferences - computed once
    // Disable swipe for Listen Together guests
    val swipeThumbnailPref by rememberPreference(SwipeThumbnailKey, true)
    val swipeThumbnail = swipeThumbnailPref && !isListenTogetherGuest
    val hidePlayerThumbnail by rememberPreference(HidePlayerThumbnailKey, false)
    val cropAlbumArt by rememberPreference(CropAlbumArtKey, false)
    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )
    
    // Pre-calculate text color based on background style
    val textBackgroundColor = getTextColor(playerBackground)
    
    // Grid state
    val thumbnailLazyGridState = rememberLazyGridState()
    val artworkKeys = remember(playerConnection) { ArtworkItemKeys() }
    
    // Calculate media items data - memoized
    val mediaItemsData by remember(
        currentMediaItemIndex,
        shuffleModeEnabled,
        queueWindows,
        swipeThumbnail,
        mediaMetadata
    ) {
        derivedStateOf {
            getMediaItems(playerConnection.player, swipeThumbnail, artworkKeys)
        }
    }
    
    val mediaItems = mediaItemsData.items
    val currentMediaIndex = mediaItemsData.currentIndex

    // Snap behavior - created once per grid state
    val thumbnailSnapLayoutInfoProvider = remember(thumbnailLazyGridState) {
        ThumbnailSnapLayoutInfoProvider(
            lazyGridState = thumbnailLazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                (layoutSize / 2f - itemSize / 2f)
            },
            velocityThreshold = 500f
        )
    }

    // Seek effect state
    var showSeekEffect by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .graphicsLayer {
                // Use hardware layer for entire Thumbnail to ensure smooth 120Hz animations
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        // Error view
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .padding(32.dp)
                .align(Alignment.Center),
        ) {
            error?.let { playbackError ->
                PlaybackError(
                    error = playbackError,
                    retry = playerConnection.player::prepare,
                )
            }
        }

        // Main thumbnail view
        AnimatedVisibility(
            visible = error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .fillMaxSize()
                .then(if (!isLandscape) Modifier.statusBarsPadding() else Modifier),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = if (isLandscape) Arrangement.Center else Arrangement.Top
            ) {
                // Now Playing header - hide in landscape mode
                if (!isLandscape) {
                    ThumbnailHeader(
                        queueTitle = queueTitle,
                        albumTitle = mediaMetadata?.album?.title,
                        textColor = textBackgroundColor
                    )
                }
                
                // Thumbnail content
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = if (isLandscape) {
                        Modifier.weight(1f, false)
                    } else {
                        Modifier.fillMaxSize()
                    }
                ) {
                    // Calculate dimensions once per size change, considering landscape mode
                    val dimensions = remember(maxWidth, maxHeight, isLandscape) {
                        calculateThumbnailDimensions(
                            containerWidth = maxWidth,
                            containerHeight = maxHeight,
                            isLandscape = isLandscape
                        )
                    }

                    val imageSizePx = with(LocalDensity.current) {
                        dimensions.thumbnailSize.roundToPx().coerceAtLeast(1)
                    }
                    val incomingItem = mediaItems.getOrNull(currentMediaIndex)
                    val incomingUri = if (incomingItem?.mediaId == mediaMetadata?.id &&
                        !mediaMetadata?.thumbnailUrl.isNullOrBlank()
                    ) {
                        mediaMetadata?.thumbnailUrl
                    } else {
                        incomingItem?.mediaMetadata?.artworkUri?.toString()
                    }
                    val imageContentScale = if (cropAlbumArt) ContentScale.Crop else ContentScale.Fit
                    val incoming = if (!hidePlayerThumbnail && incomingItem != null) {
                        // A new request owns its own painter state. An obsolete success cannot
                        // be mistaken for the replacement image during a rapid track change.
                        key(playerArtworkKey(incomingItem), incomingUri, imageSizePx, cropAlbumArt, context) {
                            val request = remember {
                                ImageRequest.Builder(context)
                                    .data(incomingUri)
                                    .size(imageSizePx, imageSizePx)
                                    .crossfade(false)
                                    .memoryCachePolicy(CachePolicy.ENABLED)
                                    .diskCachePolicy(CachePolicy.ENABLED)
                                    .networkCachePolicy(CachePolicy.ENABLED)
                                    .build()
                            }
                            val painter = rememberAsyncImagePainter(
                                model = request,
                                imageLoader = context.imageLoader,
                                contentScale = imageContentScale,
                            )
                            // The collector must share the request's key as well. Otherwise
                            // its remembered State could briefly retain the prior success.
                            painter to painter.state.collectAsStateWithLifecycle()
                        }
                    } else null
                    val incomingPainter = incoming?.first
                    val incomingState = incoming?.second?.value
                    val preloadedImages = remember { mutableStateMapOf<String, CoilImage>() }
                    var presentation by remember { mutableStateOf<PlayerArtworkPresentation?>(null) }

                    LaunchedEffect(mediaItemsData, imageSizePx, hidePlayerThumbnail, context) {
                        if (hidePlayerThumbnail) return@LaunchedEffect
                        withContext(Dispatchers.IO) {
                            for (candidate in mediaItemsData.items) {
                                val uri = candidate.mediaMetadata.artworkUri?.toString()
                                    ?: candidate.metadata?.thumbnailUrl
                                if (uri.isNullOrBlank() || preloadedImages.containsKey(uri)) continue
                                runCatching {
                                    val req = ImageRequest.Builder(context)
                                        .data(uri)
                                        .size(imageSizePx, imageSizePx)
                                        .crossfade(false)
                                        .memoryCachePolicy(CachePolicy.ENABLED)
                                        .diskCachePolicy(CachePolicy.ENABLED)
                                        .networkCachePolicy(CachePolicy.ENABLED)
                                        .build()
                                    val res = context.imageLoader.execute(req)
                                    val img = res.image
                                    if (img != null) {
                                        runCatching { img.toBitmap().prepareToDraw() }
                                        withContext(Dispatchers.Main) {
                                            preloadedImages[uri] = img
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val readyIncomingImage = (incomingState as? AsyncImagePainter.State.Success)?.result?.image
                        ?: incomingUri?.let { preloadedImages[it] }

                    val imageUnavailable = incomingState is AsyncImagePainter.State.Error || incomingUri.isNullOrBlank()
                    LaunchedEffect(mediaItemsData, incomingPainter, readyIncomingImage, imageUnavailable, hidePlayerThumbnail) {
                        if (hidePlayerThumbnail || incomingItem == null) {
                            presentation = null
                        } else {
                            presentation = updatePlayerArtworkPresentation(
                                previous = presentation,
                                mediaItems = mediaItemsData,
                                incoming = incomingItem,
                                incomingUri = incomingUri,
                                readyImage = readyIncomingImage,
                                imageUnavailable = imageUnavailable,
                            )
                        }
                        // A pending load retains the incumbent. Terminal failure advances
                        // to the failed track's own tile so it cannot pin an old window.
                    }

                    val displayedData = presentation?.mediaItems ?: mediaItemsData
                    val displayedCurrentId = presentation?.current?.mediaId ?: incomingItem?.mediaId
                    val displayedCurrentUri = presentation?.current?.uri ?: incomingUri
                    val latestDisplayedData by rememberUpdatedState(displayedData)
                    val latestSwipeThumbnail by rememberUpdatedState(swipeThumbnail)
                    val latestCanSkipNext by rememberUpdatedState(canSkipNext)
                    val latestCanSkipPrevious by rememberUpdatedState(canSkipPrevious)
                    val swipeState = remember(thumbnailLazyGridState) { ArtworkSwipeState() }

                    fun swipeContext(): ArtworkSwipeContext {
                        val player = playerConnection.player
                        val timeline = player.currentTimeline
                        val index = player.currentMediaItemIndex
                        return ArtworkSwipeContext(
                            player = player,
                            playingUid = if (index in 0 until timeline.windowCount) {
                                timeline.getWindow(index, Timeline.Window()).uid
                            } else null,
                            displayedKeys = latestDisplayedData.items.map(::playerArtworkKey),
                            displayedIndex = latestDisplayedData.currentIndex,
                        )
                    }

                    // Only a real drag can own a skip. Its fling retains that ownership,
                    // while native positioning and cancelled gestures cannot issue commands.
                    LaunchedEffect(thumbnailLazyGridState) {
                        launch {
                            thumbnailLazyGridState.interactionSource.interactions.collect { interaction ->
                                when (interaction) {
                                    is DragInteraction.Start -> {
                                        val layout = thumbnailLazyGridState.layoutInfo
                                        val anchor = layout.visibleItemsInfo.minByOrNull {
                                            abs(calculateDistanceToDesiredSnapPosition(layout, it) { size, item ->
                                                size / 2f - item / 2f
                                            })
                                        }?.index ?: thumbnailLazyGridState.firstVisibleItemIndex
                                        swipeState.start(interaction, swipeContext(), anchor)
                                    }
                                    is DragInteraction.Cancel -> swipeState.cancel(interaction.start)
                                    else -> Unit
                                }
                            }
                        }
                        snapshotFlow {
                            Triple(
                                thumbnailLazyGridState.firstVisibleItemIndex,
                                thumbnailLazyGridState.firstVisibleItemScrollOffset,
                                thumbnailLazyGridState.isScrollInProgress,
                            )
                        }.collect { (index, offset, scrolling) ->
                                if (offset != 0) return@collect
                                when (swipeState.onScroll(
                                    context = swipeContext(),
                                    index = index,
                                    offset = offset,
                                    scrolling = scrolling,
                                    enabled = latestSwipeThumbnail,
                                    canSkipNext = latestCanSkipNext,
                                    canSkipPrevious = latestCanSkipPrevious,
                                )) {
                                    ArtworkSwipeCommand.NEXT -> playerConnection.player.seekToNext()
                                    ArtworkSwipeCommand.PREVIOUS -> playerConnection.player.seekToPreviousMediaItem()
                                    null -> Unit
                                }
                            }
                    }

                    var initiallyPositioned by remember { mutableStateOf(false) }
                    LaunchedEffect(displayedData, presentation?.current?.key, canSkipPrevious, canSkipNext) {
                        val index = maxOf(0, displayedData.currentIndex)
                        if (index !in displayedData.items.indices) return@LaunchedEffect
                        // Let the grid apply its new keyed window before reading its scroll
                        // position. Otherwise an early same-index check can defeat the slide.
                        snapshotFlow {
                            val layout = thumbnailLazyGridState.layoutInfo
                            layout.totalItemsCount == displayedData.items.size &&
                                layout.visibleItemsInfo.isNotEmpty() &&
                                layout.visibleItemsInfo.all { info ->
                                    displayedData.items.getOrNull(info.index)?.let(::playerArtworkKey) == info.key
                                }
                        }.first { it }
                        val alreadyPositioned = thumbnailLazyGridState.firstVisibleItemIndex == index &&
                            thumbnailLazyGridState.firstVisibleItemScrollOffset == 0
                        val incumbentInWindow = presentation?.previous?.key?.let { oldKey ->
                            displayedData.items.any { playerArtworkKey(it) == oldKey }
                        } == true
                        if (!initiallyPositioned || !incumbentInWindow) {
                            initiallyPositioned = true
                            // Preserve initial positioning and nonanimated/disjoint windows.
                            if (!alreadyPositioned) {
                                swipeState.cancel()
                                thumbnailLazyGridState.scrollToItem(index)
                            }
                        } else if (!alreadyPositioned) {
                            swipeState.cancel()
                            val systemScale = currentCoroutineContext()[MotionDurationScale]
                            val artworkScale = object : MotionDurationScale {
                                override val scaleFactor: Float
                                    get() = (systemScale?.scaleFactor ?: 1f) * 1.1f
                            }
                            try {
                                // Keep the native scroll spring. Only this scroll's clock is
                                // 10% longer; user flings and other animations are untouched.
                                withContext(artworkScale) {
                                    thumbnailLazyGridState.animateScrollToItem(index)
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                thumbnailLazyGridState.scrollToItem(index)
                            }
                        }
                    }

                    // Remember the onSeek callback to prevent recomposition
                    val onSeekCallback = remember {
                        { direction: String, showEffect: Boolean ->
                            seekDirection = direction
                            showSeekEffect = showEffect
                        }
                    }
                    
                    // Derive scroll enabled state to prevent unnecessary recomposition
                    val isScrollEnabled by remember(swipeThumbnail) {
                        derivedStateOf { swipeThumbnail && isPlayerExpanded() }
                    }
                    
                    LazyHorizontalGrid(
                        state = thumbnailLazyGridState,
                        rows = GridCells.Fixed(1),
                        flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                        userScrollEnabled = isScrollEnabled,
                        modifier = if (isLandscape) {
                            Modifier.size(dimensions.thumbnailSize + (PlayerHorizontalPadding * 2))
                        } else {
                            Modifier.fillMaxSize()
                        }
                    ) {
                        items(
                            items = displayedData.items,
                            key = ::playerArtworkKey,
                        ) { item ->
                            ThumbnailItem(
                                item = item,
                                dimensions = dimensions,
                                hidePlayerThumbnail = hidePlayerThumbnail,
                                cropAlbumArt = cropAlbumArt,
                                textBackgroundColor = textBackgroundColor,
                                layoutDirection = layoutDirection,
                                onSeek = onSeekCallback,
                                playerConnection = playerConnection,
                                context = context,
                                isLandscape = isLandscape,
                                isListenTogetherGuest = isListenTogetherGuest,
                                currentMediaId = displayedCurrentId,
                                currentMediaThumbnail = displayedCurrentUri,
                                readyImage = presentation?.imageFor(item)
                                    ?: item.mediaMetadata.artworkUri?.toString()?.let { preloadedImages[it] },
                                incomingPainter = incomingPainter.takeIf {
                                    presentation == null && item.mediaId == incomingItem?.mediaId
                                },
                            )
                        }
                    }
                }
            }
        }

        // Seek effect
        LaunchedEffect(showSeekEffect) {
            if (showSeekEffect) {
                delay(1000)
                showSeekEffect = false
            }
        }

        AnimatedVisibility(
            visible = showSeekEffect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            SeekEffectOverlay(seekDirection = seekDirection)
        }
    }
    if (com.sakayori.music.BuildConfig.PLAYER_TRACE_ENABLED) android.os.Trace.endSection()
}

/**
 * Header component showing "Now Playing" and queue/album title.
 */
@Composable
private fun ThumbnailHeader(
    queueTitle: String?,
    albumTitle: String?,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    val listenTogetherManager = LocalListenTogetherManager.current
    val listenTogetherRoleState = listenTogetherManager?.role?.collectAsStateWithLifecycle(initialValue = RoomRole.NONE)
    val isListenTogetherGuest = listenTogetherRoleState?.value == RoomRole.GUEST
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 48.dp)
        ) {
            // Listen Together indicator
            if (listenTogetherRoleState?.value != RoomRole.NONE) {
                Text(
                    text = if (listenTogetherRoleState?.value == RoomRole.HOST) "Hosting Listen Together" else "Listening Together",
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
            } else {
                Text(
                    text = stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor
                )
            }
            val playingFrom = queueTitle ?: albumTitle
            if (!playingFrom.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = playingFrom,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor.copy(alpha = 0.8f),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
        }
    }
}

/**
 * Individual thumbnail item in the carousel.
 */
@Composable
private fun ThumbnailItem(
    item: MediaItem,
    dimensions: ThumbnailDimensions,
    hidePlayerThumbnail: Boolean,
    cropAlbumArt: Boolean,
    textBackgroundColor: Color,
    layoutDirection: LayoutDirection,
    onSeek: (String, Boolean) -> Unit,
    playerConnection: com.sakayori.music.playback.PlayerConnection,
    context: android.content.Context,
    isLandscape: Boolean = false,
    isListenTogetherGuest: Boolean = false,
    currentMediaId: String? = null,
    currentMediaThumbnail: String? = null,
    readyImage: CoilImage? = null,
    incomingPainter: Painter? = null,
    modifier: Modifier = Modifier,
) {
    val incrementalSeekSkipEnabled by rememberPreference(SeekExtraSeconds, defaultValue = false)
    var skipMultiplier by remember { mutableIntStateOf(1) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    Box(
        modifier = modifier
            .then(
                if (isLandscape) {
                    Modifier.size(dimensions.thumbnailSize + (PlayerHorizontalPadding * 2))
                } else {
                    Modifier
                        .width(dimensions.itemWidth)
                        .fillMaxSize()
                }
            )
            .padding(horizontal = PlayerHorizontalPadding)
            .graphicsLayer {
                // Render entire thumbnail item on separate hardware layer for smooth animations
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (isListenTogetherGuest) return@detectTapGestures

                        val currentPosition = playerConnection.player.currentPosition
                        val duration = playerConnection.player.duration

                        val now = System.currentTimeMillis()
                        if (incrementalSeekSkipEnabled && now - lastTapTime < 1000) {
                            skipMultiplier++
                        } else {
                            skipMultiplier = 1
                        }
                        lastTapTime = now

                        val skipAmount = 5000 * skipMultiplier

                        val isLeftSide = (layoutDirection == LayoutDirection.Ltr && offset.x < size.width / 2) ||
                                (layoutDirection == LayoutDirection.Rtl && offset.x > size.width / 2)

                        if (isLeftSide) {
                            playerConnection.player.seekTo((currentPosition - skipAmount).coerceAtLeast(0))
                            onSeek(context.getString(R.string.seek_backward_dynamic, skipAmount / 1000), true)
                        } else {
                            playerConnection.player.seekTo((currentPosition + skipAmount).coerceAtMost(duration))
                            onSeek(context.getString(R.string.seek_forward_dynamic, skipAmount / 1000), true)
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(dimensions.thumbnailSize)
                .clip(RoundedCornerShape(dimensions.cornerRadius))
        ) {
            if (hidePlayerThumbnail) {
                HiddenThumbnailPlaceholder(textBackgroundColor = textBackgroundColor)
            } else {
                val artworkUriToUse = if (item.mediaId == currentMediaId && !currentMediaThumbnail.isNullOrBlank()) {
                    currentMediaThumbnail
                } else {
                    item.mediaMetadata.artworkUri?.toString()
                }

                ThumbnailImage(
                    artworkUri = artworkUriToUse,
                    cropArtwork = cropAlbumArt,
                    readyImage = readyImage,
                    incomingPainter = incomingPainter,
                )
            }
            
            // Cast button at top-right corner of thumbnail
            CastButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                tintColor = textBackgroundColor
            )
        }
    }
}

/**
 * Placeholder shown when thumbnail is hidden.
 */
@Composable
private fun HiddenThumbnailPlaceholder(
    textBackgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.small_icon),
            contentDescription = stringResource(R.string.hide_player_thumbnail),
            modifier = Modifier.size(120.dp)
        )
    }
}

/**
 * Actual thumbnail image with caching and hardware layer rendering.
 */
@Composable
private fun ThumbnailImage(
    artworkUri: String?,
    cropArtwork: Boolean,
    readyImage: CoilImage? = null,
    incomingPainter: Painter? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val painter = if (readyImage != null) {
        remember(context, readyImage) { readyImage.asPainter(context) }
    } else incomingPainter
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                // Use offscreen compositing for hardware acceleration during animations
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = if (cropArtwork) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            val request = remember(context, artworkUri) {
                ImageRequest.Builder(context)
                    .data(artworkUri)
                    .crossfade(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = if (cropArtwork) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Seek effect overlay showing seek direction.
 */
@Composable
private fun SeekEffectOverlay(
    seekDirection: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = seekDirection,
        color = Color.White,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    )
}
