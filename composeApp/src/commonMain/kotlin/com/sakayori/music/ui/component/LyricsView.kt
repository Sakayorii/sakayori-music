package com.sakayori.music.ui.component

import androidx.compose.animation.Animatable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.sakayori.domain.data.model.streams.TimeLine
import com.sakayori.music.extension.KeepScreenOn
import com.sakayori.music.extension.ParsedRichSyncLine
import com.sakayori.music.extension.animateScrollAndCentralizeItem
import com.sakayori.music.extension.formatDuration
import com.sakayori.music.extension.hsvToColor
import com.sakayori.music.extension.parseRichSyncWords
import com.sakayori.music.ui.navigation.destination.list.ArtistDestination
import com.sakayori.music.ui.theme.typo
import com.sakayori.music.viewModel.NowPlayingScreenData
import com.sakayori.music.viewModel.SharedViewModel
import com.sakayori.music.viewModel.UIEvent
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.baseline_more_vert_24
import com.sakayori.music.generated.resources.crossfading
import com.sakayori.music.generated.resources.unavailable
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TAG = "LyricsView"

@Composable
fun LyricsView(
    lyricsData: NowPlayingScreenData.LyricsData,
    timeLine: StateFlow<TimeLine>,
    onLineClick: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showScrollShadows: Boolean = false,
    backgroundColor: Color = Color(0xFF242424),
    hasBlurBackground: Boolean = false,
) {
    var currentLineHeight by remember {
        mutableIntStateOf(0)
    }
    val listState = rememberLazyListState()
    val current by timeLine.collectAsStateWithLifecycle()
    var currentLineIndex by rememberSaveable {
        mutableIntStateOf(-1)
    }

    LaunchedEffect(key1 = current) {
        val lines = lyricsData.lyrics.lines
        if (current.current > 0L) {
            lines?.indices?.forEach { i ->
                val sentence = lines[i]
                val startTimeMs = sentence.startTimeMs.toLong()

                val endTimeMs =
                    if (i < lines.size - 1) {
                        lines[i + 1].startTimeMs.toLong()
                    } else {
                        startTimeMs + 60000
                    }
                if (current.current in startTimeMs..endTimeMs) {
                    currentLineIndex = i
                }
            }
            if (!lines.isNullOrEmpty() &&
                (
                    current.current in (
                        0..(
                            lines.getOrNull(0)?.startTimeMs
                                ?: "0"
                        ).toLong()
                    )
                )
            ) {
                currentLineIndex = -1
            }
        } else {
            currentLineIndex = -1
        }
    }
    LaunchedEffect(key1 = currentLineIndex, key2 = currentLineHeight, key3 = current) {
        if (currentLineIndex > -1 && currentLineHeight > 0 &&
            (lyricsData.lyrics.syncType == "LINE_SYNCED" || lyricsData.lyrics.syncType == "RICH_SYNCED")
        ) {
            listState.animateScrollAndCentralizeItem(
                index = currentLineIndex,
                this,
            )
        }
    }

    val translatedLinesWithTimes = remember(lyricsData.translatedLyrics?.first?.lines) {
        lyricsData.translatedLyrics?.first?.lines?.map {
            Triple(it.startTimeMs.toLongOrNull() ?: 0L, it.words, it)
        } ?: emptyList()
    }

    val translatedCache = remember(translatedLinesWithTimes) { mutableMapOf<String, String?>() }

    fun findClosestTranslatedLine(originalTimeMs: String): String? {
        if (translatedLinesWithTimes.isEmpty()) return null
        translatedCache[originalTimeMs]?.let { return it }
        val originalTime = originalTimeMs.toLongOrNull() ?: return null
        val result = translatedLinesWithTimes
            .minByOrNull { abs(it.first - originalTime) }
            ?.let {
                if (abs(it.first - originalTime) < 1000L) it.second else null
            }
        translatedCache[originalTimeMs] = result
        return result
    }

    Box(modifier = modifier) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(lyricsData.lyrics.lines?.size ?: 0) { index ->
                val line = lyricsData.lyrics.lines?.getOrNull(index)
                val translatedWords =
                    if (lyricsData.lyrics.syncType == "LINE_SYNCED" || lyricsData.lyrics.syncType == "RICH_SYNCED") {
                        line?.startTimeMs?.let { findClosestTranslatedLine(it) }
                    } else {
                        lyricsData.translatedLyrics
                            ?.first
                            ?.lines
                            ?.getOrNull(index)
                            ?.words
                    }

                line?.words?.let { words ->
                    when {
                        lyricsData.lyrics.syncType == "RICH_SYNCED" -> {
                            val parsedLine =
                                remember(words, line.startTimeMs, line.endTimeMs) {
                                    val result = parseRichSyncWords(words, line.startTimeMs, line.endTimeMs)
                                    result
                                }

                            if (parsedLine != null) {
                                RichSyncLyricsLineItem(
                                    parsedLine = parsedLine,
                                    translatedWords = translatedWords,
                                    currentTimeMs = current.current,
                                    isCurrent = index == currentLineIndex,
                                    modifier =
                                        Modifier
                                            .clickable {
                                                onLineClick(line.startTimeMs.toFloat() * 100 / timeLine.value.total)
                                            }.onGloballyPositioned { c ->
                                                if (c.size.height != currentLineHeight) {
                                                    currentLineHeight = c.size.height
                                                }
                                            },
                                )
                            } else {
                                LyricsLineItem(
                                    originalWords = words,
                                    translatedWords = translatedWords,
                                    isBold = index <= currentLineIndex,
                                    isCurrent = index == currentLineIndex,
                                    modifier =
                                        Modifier
                                            .clickable {
                                                onLineClick(line.startTimeMs.toFloat() * 100 / timeLine.value.total)
                                            }.onGloballyPositioned { c ->
                                                if (c.size.height != currentLineHeight) {
                                                    currentLineHeight = c.size.height
                                                }
                                            },
                                )
                            }
                        }

                        else -> {
                            LyricsLineItem(
                                originalWords = words,
                                translatedWords = translatedWords,
                                isBold = index <= currentLineIndex || lyricsData.lyrics.syncType != "LINE_SYNCED",
                                isCurrent = index == currentLineIndex || lyricsData.lyrics.syncType != "LINE_SYNCED",
                                modifier =
                                    Modifier
                                        .clickable(
                                            enabled = lyricsData.lyrics.syncType == "LINE_SYNCED" ||
                                                lyricsData.lyrics.syncType == "RICH_SYNCED",
                                        ) {
                                            onLineClick(line.startTimeMs.toFloat() * 100 / timeLine.value.total)
                                        }.onGloballyPositioned { c ->
                                            if (c.size.height != currentLineHeight) {
                                                currentLineHeight = c.size.height
                                            }
                                        },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsLineItem(
    originalWords: String,
    translatedWords: String?,
    isBold: Boolean,
    isCurrent: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Crossfade(targetState = isBold) {
        if (it) {
            Column(
                modifier = modifier,
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    modifier =
                        Modifier.then(
                            if (isCurrent) {
                                Modifier
                            } else {
                                Modifier.blur(1.dp)
                            },
                        ),
                    text = originalWords,
                    style = if (isCurrent) {
                        typo().headlineLarge.copy(fontWeight = FontWeight.Bold)
                    } else {
                        typo().headlineLarge
                    },
                    color =
                        if (isCurrent) {
                            Color.White
                        } else {
                            Color.LightGray.copy(
                                alpha = 0.35f,
                            )
                        },
                )
                if (translatedWords != null) {
                    Text(
                        modifier =
                            Modifier.then(
                                if (isCurrent) {
                                    Modifier
                                } else {
                                    Modifier.blur(1.dp)
                                },
                            ),
                        text = translatedWords,
                        style = typo().bodyMedium,
                        color =
                            if (isCurrent) {
                                Color.Yellow
                            } else {
                                Color(0xFF97971A).copy(
                                    alpha = 0.3f,
                                )
                            },
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
    if (!isBold) {
        Column(
            modifier = modifier,
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                modifier = Modifier.blur(1.dp),
                text = originalWords,
                style = typo().headlineMedium,
                color =
                    Color.LightGray.copy(
                        alpha = 0.35f,
                    ),
            )
            if (translatedWords != null) {
                Text(
                    modifier = Modifier.blur(1.dp),
                    text = translatedWords,
                    style = typo().bodyMedium,
                    color =
                        Color(0xFF97971A).copy(
                            alpha = 0.3f,
                        ),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichSyncLyricsLineItem(
    parsedLine: ParsedRichSyncLine,
    translatedWords: String?,
    currentTimeMs: Long,
    isCurrent: Boolean,
    customFontSize: TextUnit? = null,
    customPadding: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    val currentWordIndex by remember(currentTimeMs, parsedLine.words) {
        derivedStateOf {
            if (!isCurrent) return@derivedStateOf -1

            parsedLine.words.indexOfLast { it.startTimeMs <= currentTimeMs }
        }
    }

    Column(
        modifier = modifier,
    ) {
        Spacer(modifier = Modifier.height(customPadding))

        FlowRow(
            modifier =
                Modifier.then(
                    if (isCurrent) {
                        Modifier
                    } else {
                        Modifier.blur(1.dp)
                    },
                ),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            parsedLine.words.forEachIndexed { index, wordTiming ->
                val wordEndTimeMs =
                    if (index < parsedLine.words.size - 1) {
                        parsedLine.words[index + 1].startTimeMs
                    } else if (parsedLine.lineEndTimeMs == Long.MAX_VALUE || parsedLine.lineEndTimeMs <= wordTiming.startTimeMs) {
                        if (index > 0 && parsedLine.words[index - 1].startTimeMs < wordTiming.startTimeMs) {
                            val prevWordDuration = wordTiming.startTimeMs - parsedLine.words[index - 1].startTimeMs
                            wordTiming.startTimeMs + prevWordDuration
                        } else {
                            wordTiming.startTimeMs + 500L
                        }
                    } else {
                        parsedLine.lineEndTimeMs
                    }
                AnimatedWord(
                    word = wordTiming.text,
                    wordIndex = index,
                    wordStartTimeMs = wordTiming.startTimeMs,
                    wordEndTimeMs = wordEndTimeMs,
                    currentTimeMs = currentTimeMs,
                    isActive = isCurrent && index == currentWordIndex,
                    isPast = isCurrent && index < currentWordIndex,
                    isCurrent = isCurrent,
                    customFontSize = customFontSize,
                )
            }
        }

        if (translatedWords != null) {
            Text(
                modifier =
                    Modifier.then(
                        if (isCurrent) {
                            Modifier
                        } else {
                            Modifier.blur(1.dp)
                        },
                    ),
                text = translatedWords,
                style = typo().bodyMedium,
                color =
                    if (isCurrent) {
                        Color.Yellow
                    } else {
                        Color(0xFF97971A).copy(
                            alpha = 0.3f,
                        )
                    },
            )
        }

        Spacer(modifier = Modifier.height(customPadding))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimatedWord(
    word: String,
    wordIndex: Int,
    wordStartTimeMs: Long,
    wordEndTimeMs: Long,
    currentTimeMs: Long,
    isActive: Boolean,
    isPast: Boolean,
    isCurrent: Boolean,
    customFontSize: TextUnit? = null,
) {
    if (!isCurrent) {
        Text(
            text = word,
            style =
                typo().headlineLarge.copy(
                    fontSize = customFontSize ?: typo().headlineLarge.fontSize,
                ),
            color = Color.LightGray.copy(alpha = 0.35f),
        )
        return
    }

    val wordDuration = (wordEndTimeMs - wordStartTimeMs).coerceAtLeast(100L)

    Row(
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        word.forEachIndexed { charIndex, char ->
            val charStartTimeMs = wordStartTimeMs + (wordDuration * charIndex / word.length)
            val charEndTimeMs =
                if (charIndex < word.length - 1) {
                    wordStartTimeMs + (wordDuration * (charIndex + 1) / word.length)
                } else {
                    wordEndTimeMs
                }

            val isPastChar = currentTimeMs >= charEndTimeMs
            val isFutureChar = currentTimeMs <= charStartTimeMs

            val rawProgress =
                remember(currentTimeMs, charStartTimeMs, charEndTimeMs) {
                    when {
                        isPastChar -> {
                            1f
                        }

                        isFutureChar -> {
                            0f
                        }

                        else -> {
                            (
                                (currentTimeMs - charStartTimeMs).toFloat() /
                                    (charEndTimeMs - charStartTimeMs).toFloat()
                            ).coerceIn(0f, 1f)
                        }
                    }
                }

            val animatedCharProgress by animateFloatAsState(
                targetValue = rawProgress,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
                label = "charProgress",
            )

            val charColor =
                when {
                    animatedCharProgress >= 1f -> {
                        Color.White
                    }
                    animatedCharProgress <= 0f -> {
                        Color.LightGray.copy(alpha = 0.6f)
                    }
                    else -> {
                        val t = animatedCharProgress
                        val gray = Color.LightGray.copy(alpha = 0.6f)
                        Color(
                            red = gray.red + (1f - gray.red) * t,
                            green = gray.green + (1f - gray.green) * t,
                            blue = gray.blue + (1f - gray.blue) * t,
                            alpha = gray.alpha + (1f - gray.alpha) * t,
                        )
                    }
                }

            Text(
                text = char.toString(),
                style =
                    typo().headlineLarge.copy(
                        fontSize = customFontSize ?: typo().headlineLarge.fontSize,
                    ),
                color = charColor,
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@ExperimentalMaterial3Api
@ExperimentalFoundationApi
@Composable
fun FullscreenLyricsSheet(
    sharedViewModel: SharedViewModel,
    navController: NavController,
    color: Color = Color(0xFF242424),
    shouldHaze: Boolean,
    onDismiss: () -> Unit,
) {
    val screenDataState by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val timelineState by sharedViewModel.timeline.collectAsStateWithLifecycle()
    val controllerState by sharedViewModel.controllerState.collectAsStateWithLifecycle()

    val sheetState =
        rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        )
    val coroutineScope = rememberCoroutineScope()
    val localDensity = LocalDensity.current
    val windowInsets = WindowInsets.systemBars

    var sliderValue by rememberSaveable {
        mutableFloatStateOf(0f)
    }

    var showControlButtons by rememberSaveable {
        mutableStateOf(true)
    }

    var showNowPlayingSheet by rememberSaveable {
        mutableStateOf(false)
    }

    val startColor = remember { Animatable(color) }
    val midColor1 = remember { Animatable(color.copy(alpha = 0.95f)) }
    val midColor2 = remember { Animatable(color.copy(alpha = 0.85f)) }
    val endColor = remember { Animatable(Color.Black) }

    var gradientAngle by remember { mutableFloatStateOf(0f) }
    var gradientOffsetX by remember { mutableFloatStateOf(0f) }
    var gradientOffsetY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        if (!shouldHaze) {
            var direction = 1f
            var angleDirection = 1f
            while (true) {
                gradientAngle += angleDirection * 0.3f
                if (gradientAngle > 45f || gradientAngle < -45f) {
                    angleDirection *= -1f
                }
                gradientOffsetX += direction * 1.2f
                gradientOffsetY += direction * 0.8f
                if (gradientOffsetX > 1500f || gradientOffsetX < -1500f) {
                    direction *= -1f
                }
                delay(16)
            }
        }
    }

    LaunchedEffect(color) {
        if (!shouldHaze) {
            launch {
                startColor.animateTo(
                    targetValue = color,
                    animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                )
            }
            launch {
                midColor1.animateTo(
                    targetValue = color.copy(alpha = 0.95f),
                    animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                )
            }
            launch {
                midColor2.animateTo(
                    targetValue = color.copy(alpha = 0.85f),
                    animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                )
            }
            launch {
                endColor.animateTo(
                    targetValue = Color.Black,
                    animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                )
            }
        }
    }

    LaunchedEffect(key1 = showControlButtons) {
        if (showControlButtons) {
            delay(4000)
            showControlButtons = false
        }
    }

    LaunchedEffect(key1 = timelineState) {
        sliderValue =
            if (timelineState.total > 0L) {
                timelineState.current.toFloat() * 100 / timelineState.total.toFloat()
            } else {
                0f
            }
    }

    if (screenDataState.lyricsData != null) {
        KeepScreenOn()
    }

    var showQueueBottomSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showInfoBottomSheet by rememberSaveable {
        mutableStateOf(false)
    }

    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
        },
        containerColor = Color.Black,
        contentColor = Color.Transparent,
        dragHandle = {},
        scrimColor = Color.Black.copy(alpha = .5f),
        sheetState = sheetState,
        modifier =
            Modifier
                .fillMaxHeight()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) {
                    showControlButtons = true
                },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        shape = RectangleShape,
    ) {
        val lowResourceMode = com.sakayori.music.extension.LocalLowResourceMode.current
        val sliderTrackColor: Color = if (timelineState.isCrossfading && !lowResourceMode) {
            val infiniteTransition = rememberInfiniteTransition(label = "crossfadeRainbow")
            val rainbowHue by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec =
                    infiniteRepeatable(
                        animation = tween(2000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart,
                    ),
                label = "rainbowHue",
            )
            hsvToColor(rainbowHue, 1f, 1f)
        } else {
            Color.White
        }
        Box(modifier = Modifier.fillMaxSize()) {
            val isBlurEnabled = com.sakayori.music.extension.LocalBlurEnabled.current
            val hazeState = rememberHazeState(blurEnabled = isBlurEnabled)

            if (shouldHaze) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState),
                ) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalPlatformContext.current)
                                .data(screenDataState.thumbnailURL)
                                .crossfade(300)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .diskCacheKey(screenDataState.thumbnailURL)
                                .build(),
                        contentDescription = null,
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            } else {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors =
                                        listOf(
                                            startColor.value,
                                            midColor1.value,
                                            midColor2.value,
                                            endColor.value.copy(alpha = 0.9f),
                                            endColor.value,
                                        ),
                                    start =
                                        Offset(
                                            x = gradientOffsetX + (cos(gradientAngle * PI.toFloat() / 180f) * 800f),
                                            y = gradientOffsetY + (sin(gradientAngle * PI.toFloat() / 180f) * 800f),
                                        ),
                                    end =
                                        Offset(
                                            x = gradientOffsetX + 2500f + (cos((gradientAngle + 180f) * PI.toFloat() / 180f) * 800f),
                                            y = gradientOffsetY + 2500f + (sin((gradientAngle + 180f) * PI.toFloat() / 180f) * 800f),
                                        ),
                                ),
                            ),
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (shouldHaze) {
                                Modifier.hazeEffect(
                                    hazeState,
                                    style = CupertinoMaterials.regular(),
                                ) {
                                    blurEnabled = isBlurEnabled
                                }
                            } else {
                                Modifier
                            },
                        ).padding(
                            bottom =
                                with(localDensity) {
                                    windowInsets.getBottom(localDensity).toDp()
                                },
                            top =
                                with(localDensity) {
                                    windowInsets.getTop(localDensity).toDp()
                                },
                        ),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model =
                            ImageRequest
                                .Builder(LocalPlatformContext.current)
                                .data(screenDataState.thumbnailURL)
                                .crossfade(300)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .diskCacheKey(screenDataState.thumbnailURL)
                                .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(45.dp)
                                .clip(RoundedCornerShape(8.dp)),
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = screenDataState.nowPlayingTitle,
                            style = typo().labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            modifier =
                                Modifier
                                    .basicMarquee(
                                        iterations = Int.MAX_VALUE,
                                        animationMode = MarqueeAnimationMode.Immediately,
                                    ).focusable(),
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier.clickable {
                                    coroutineScope.launch {
                                        val song = sharedViewModel.nowPlayingState.value?.songEntity
                                        (
                                            song?.artistId?.firstOrNull()?.takeIf { it.isNotEmpty() }
                                                ?: screenDataState.songInfoData?.authorId
                                        )?.let { channelId ->
                                            sheetState.hide()
                                            onDismiss()
                                            navController.navigate(
                                                ArtistDestination(
                                                    channelId = channelId,
                                                ),
                                            )
                                        }
                                    }
                                },
                        ) {
                            if (screenDataState.isExplicit) {
                                ExplicitBadge(
                                    modifier =
                                        Modifier
                                            .size(16.dp)
                                            .padding(end = 4.dp),
                                )
                            }
                            Text(
                                text = screenDataState.artistName,
                                style = typo().bodySmall,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                modifier =
                                    Modifier
                                        .basicMarquee(
                                            iterations = Int.MAX_VALUE,
                                            animationMode = MarqueeAnimationMode.Immediately,
                                        ).focusable(),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    HeartCheckBox(
                        checked = controllerState.isLiked,
                        size = 28,
                    ) {
                        sharedViewModel.onUIEvent(UIEvent.ToggleLike)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { showNowPlayingSheet = true },
                    ) {
                        Icon(
                            painter = painterResource(Res.drawable.baseline_more_vert_24),
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }

                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 50.dp),
                ) {
                    Crossfade(
                        targetState = screenDataState.lyricsData != null,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (it) {
                            screenDataState.lyricsData?.let { lyrics ->
                                LyricsView(
                                    lyricsData = lyrics,
                                    timeLine = sharedViewModel.timeline,
                                    onLineClick = { f ->
                                        sharedViewModel.onUIEvent(UIEvent.UpdateProgress(f))
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    showScrollShadows = true,
                                    backgroundColor = startColor.value,
                                    hasBlurBackground = shouldHaze,
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(Res.string.unavailable),
                                    style = typo().bodyMedium,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }

                Column {
                    Box(
                        Modifier
                            .padding(
                                top = 15.dp,
                            ).padding(horizontal = 40.dp),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Crossfade(timelineState.loading) {
                                if (it) {
                                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                        LinearProgressIndicator(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .padding(
                                                        horizontal = 3.dp,
                                                    ).clip(
                                                        RoundedCornerShape(8.dp),
                                                    ),
                                            color = Color.Gray,
                                            trackColor = Color.DarkGray,
                                            strokeCap = StrokeCap.Round,
                                        )
                                    }
                                } else {
                                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                                        LinearProgressIndicator(
                                            progress = { timelineState.bufferedPercent.toFloat() / 100 },
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .height(4.dp)
                                                    .padding(
                                                        horizontal = 3.dp,
                                                    ).clip(
                                                        RoundedCornerShape(8.dp),
                                                    ),
                                            color = Color.Gray,
                                            trackColor = Color.DarkGray,
                                            strokeCap = StrokeCap.Round,
                                            drawStopIndicator = {},
                                        )
                                    }
                                }
                            }
                        }
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                            Slider(
                                value = sliderValue,
                                onValueChange = {
                                    sharedViewModel.onUIEvent(
                                        UIEvent.UpdateProgress(it),
                                    )
                                },
                                valueRange = 0f..100f,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = 3.dp)
                                        .align(
                                            Alignment.TopCenter,
                                        ),
                                track = { sliderState ->
                                    SliderDefaults.Track(
                                        modifier =
                                            Modifier
                                                .height(5.dp),
                                        enabled = true,
                                        sliderState = sliderState,
                                        colors =
                                            SliderDefaults.colors().copy(
                                                thumbColor = sliderTrackColor,
                                                activeTrackColor = sliderTrackColor,
                                                inactiveTrackColor = Color.Transparent,
                                            ),
                                        thumbTrackGapSize = 0.dp,
                                        drawTick = { _, _ -> },
                                        drawStopIndicator = null,
                                    )
                                },
                                thumb = {
                                    SliderDefaults.Thumb(
                                        modifier =
                                            Modifier
                                                .height(18.dp)
                                                .width(8.dp)
                                                .padding(
                                                    vertical = 4.dp,
                                                ),
                                        thumbSize = DpSize(8.dp, 8.dp),
                                        interactionSource =
                                            remember {
                                                MutableInteractionSource()
                                            },
                                        colors =
                                            SliderDefaults.colors().copy(
                                                thumbColor = Color.White,
                                                activeTrackColor = Color.White,
                                                inactiveTrackColor = Color.Transparent,
                                            ),
                                        enabled = true,
                                    )
                                },
                            )
                        }
                    }
                    LazyColumn {
                        item {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 40.dp),
                            ) {
                                Text(
                                    text = formatDuration(timelineState.current),
                                    style = typo().bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Left,
                                )
                                AnimatedVisibility(
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    visible = timelineState.isCrossfading,
                                ) {
                                    Text(
                                        text = stringResource(Res.string.crossfading),
                                        style = typo().bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                Text(
                                    text = formatDuration(timelineState.total),
                                    style = typo().bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Right,
                                )
                            }

                            Spacer(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(5.dp),
                            )
                        }

                        item {
                            AnimatedVisibility(
                                visible = showControlButtons,
                                enter =
                                    expandVertically(
                                        tween(300),
                                    ),
                                exit =
                                    shrinkVertically(
                                        tween(300),
                                    ),
                            ) {
                                PlayerControlLayout(controllerState) {
                                    sharedViewModel.onUIEvent(it)
                                }
                            }
                            AnimatedVisibility(
                                visible = showControlButtons,
                                enter =
                                    expandVertically(
                                        tween(300),
                                    ),
                                exit =
                                    shrinkVertically(
                                        tween(300),
                                    ),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .height(32.dp)
                                            .fillMaxWidth()
                                            .padding(horizontal = 40.dp),
                                ) {
                                    IconButton(
                                        modifier =
                                            Modifier
                                                .size(24.dp)
                                                .aspectRatio(1f)
                                                .align(Alignment.CenterStart)
                                                .clip(
                                                    CircleShape,
                                                ),
                                        onClick = {
                                            showInfoBottomSheet = true
                                            showControlButtons = true
                                        },
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Info, tint = Color.White, contentDescription = null)
                                    }
                                    Row(
                                        Modifier.align(Alignment.CenterEnd),
                                    ) {
                                        Spacer(modifier = Modifier.size(8.dp))
                                        IconButton(
                                            modifier =
                                                Modifier
                                                    .size(24.dp)
                                                    .aspectRatio(1f)
                                                    .clip(
                                                        CircleShape,
                                                    ),
                                            onClick = {
                                                showQueueBottomSheet = true
                                                showControlButtons = true
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Outlined.QueueMusic,
                                                tint = Color.White,
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                            }
                        }
                    }
                }

                if (!showControlButtons) {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
    if (showQueueBottomSheet) {
        QueueBottomSheet(
            onDismiss = {
                showQueueBottomSheet = false
            },
        )
    }
    if (showInfoBottomSheet) {
        InfoPlayerBottomSheet(
            onDismiss = {
                showInfoBottomSheet = false
            },
        )
    }
    if (showNowPlayingSheet) {
        NowPlayingBottomSheet(
            onDismiss = {
                showNowPlayingSheet = false
            },
            navController = navController,
            onNavigateToOtherScreen = {
                onDismiss()
            },
            song = null,
            setSleepTimerEnable = true,
            changeMainLyricsProviderEnable = true,
        )
    }
}
