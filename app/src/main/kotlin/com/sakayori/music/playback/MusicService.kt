/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("DEPRECATION")

package com.sakayori.music.playback

import android.app.ForegroundServiceStartNotAllowedException
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ComponentCallbacks2
import android.content.pm.ServiceInfo
import android.database.SQLException
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import com.sakayori.music.playback.audio.VolumeNormalizationAudioProcessor
import com.sakayori.music.utils.safeDataStoreEdit
import android.net.ConnectivityManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.datastore.preferences.core.Preferences
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.MoreExecutors
import com.sakayori.innertube.YouTube
import com.sakayori.innertube.strategy.ContentHints
import com.sakayori.innertube.models.SongItem
import com.sakayori.innertube.models.WatchEndpoint
import com.sakayori.lastfm.LastFM
import com.sakayori.music.MainActivity
import com.sakayori.music.App
import com.sakayori.music.R
import com.sakayori.music.constants.AndroidAutoTargetPlaylistKey
import com.sakayori.music.constants.AudioNormalizationKey
import com.sakayori.music.constants.AudioOffload
import com.sakayori.music.constants.AudioQualityKey
import com.sakayori.music.constants.PreferLosslessKey
import com.sakayori.music.constants.LosslessApiUrlKey
import com.sakayori.music.constants.AudioTrackPlaybackParamsKey
import com.sakayori.music.constants.AutoDownloadOnLikeKey
import com.sakayori.music.constants.AutoLoadMoreKey
import com.sakayori.music.constants.AutoSkipNextOnErrorKey
import com.sakayori.music.constants.StreamSourceAndroidVRKey
import com.sakayori.music.constants.StreamSourceTVHTML5Key
import com.sakayori.music.constants.StreamSourceVisionOSKey
import com.sakayori.music.constants.StreamSourceWebCreatorKey
import com.sakayori.music.constants.StreamSourceWebRemixKey
import com.sakayori.music.constants.AutoplayKey
import com.sakayori.music.constants.CrossfadeDurationKey
import com.sakayori.music.constants.CrossfadeEnabledKey
import com.sakayori.music.constants.CrossfadeGaplessKey
import com.sakayori.music.constants.DisableLoadMoreWhenRepeatAllKey
import com.sakayori.music.constants.DiscordActivityNameKey
import com.sakayori.music.constants.DiscordActivityTypeKey
import com.sakayori.music.constants.DiscordAdvancedModeKey
import com.sakayori.music.constants.DiscordButton1EnabledKey
import com.sakayori.music.constants.DiscordButton1LabelKey
import com.sakayori.music.constants.DiscordButton1UrlKey
import com.sakayori.music.constants.DiscordButton2EnabledKey
import com.sakayori.music.constants.DiscordButton2LabelKey
import com.sakayori.music.constants.DiscordButton2UrlKey
import com.sakayori.music.constants.DiscordDetailsTemplateKey
import com.sakayori.music.constants.DiscordStateTemplateKey
import com.sakayori.music.constants.DiscordUserStatusKey
import com.sakayori.music.constants.EnableDiscordRPCKey
import com.sakayori.music.discord.DiscordActivity
import com.sakayori.music.discord.DiscordDefaults
import com.sakayori.music.discord.DiscordRpcManager
import com.sakayori.music.discord.DiscordActivityBuilder
import com.sakayori.music.discord.discordAlbumName
import com.sakayori.music.discord.DiscordTemplateRenderer
import com.sakayori.music.discord.PresenceStatus
import com.sakayori.music.constants.EnableLastFMScrobblingKey
import com.sakayori.music.constants.EnableSongCacheKey
import com.sakayori.music.constants.MaxSongCacheSizeKey
import com.sakayori.music.constants.HideExplicitKey
import com.sakayori.music.constants.HideVideoSongsKey
import com.sakayori.music.constants.HistoryDuration
import com.sakayori.music.constants.LastFMUseNowPlaying
import com.sakayori.music.constants.MediaSessionConstants
import com.sakayori.music.constants.MediaSessionConstants.CommandAddToTargetPlaylist
import com.sakayori.music.constants.MediaSessionConstants.CommandToggleLike
import com.sakayori.music.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.sakayori.music.constants.MediaSessionConstants.CommandToggleShuffle
import com.sakayori.music.constants.MediaSessionConstants.CommandToggleStartRadio
import com.sakayori.music.constants.PauseListenHistoryKey
import com.sakayori.music.constants.PauseOnMute
import com.sakayori.music.constants.PersistentQueueKey
import com.sakayori.music.constants.PersistentShuffleAcrossQueuesKey
import com.sakayori.music.constants.PlayerVolumeKey
import com.sakayori.music.constants.PreventDuplicateTracksInQueueKey
import com.sakayori.music.constants.RememberShuffleAndRepeatKey
import com.sakayori.music.constants.RepeatModeKey
import com.sakayori.music.constants.ResumeOnBluetoothConnectKey
import com.sakayori.music.constants.ScrobbleDelayPercentKey
import com.sakayori.music.constants.ScrobbleDelaySecondsKey
import com.sakayori.music.constants.ScrobbleMinSongDurationKey
import com.sakayori.music.constants.ShowLyricsKey
import com.sakayori.music.constants.ShuffleModeKey
import com.sakayori.music.constants.ShufflePlaylistFirstKey
import com.sakayori.music.constants.SimilarContent
import com.sakayori.music.constants.SkipSilenceInstantKey
import com.sakayori.music.constants.SkipSilenceKey
import com.sakayori.music.constants.StopMusicOnTaskClearKey
import com.sakayori.music.db.MusicDatabase
import com.sakayori.music.db.entities.Event
import com.sakayori.music.db.entities.LyricsEntity
import com.sakayori.music.db.entities.PlaylistEntity
import com.sakayori.music.db.entities.RelatedSongMap
import com.sakayori.music.db.entities.Song
import com.sakayori.music.di.DownloadCache
import com.sakayori.music.di.PlayerCache
import com.sakayori.music.di.ApplicationScope
import com.sakayori.music.eq.EqualizerService
import com.sakayori.music.eq.audio.CustomEqualizerAudioProcessor
import com.sakayori.music.eq.data.EQProfileRepository
import com.sakayori.music.extensions.SilentHandler
import com.sakayori.music.extensions.collect
import com.sakayori.music.extensions.collectLatest
import com.sakayori.music.extensions.currentMetadata
import com.sakayori.music.extensions.findNextMediaItemById
import com.sakayori.music.extensions.mediaItems
import com.sakayori.music.extensions.metadata
import com.sakayori.music.extensions.setOffloadEnabled
import com.sakayori.music.extensions.toEnum
import com.sakayori.music.extensions.toMediaItem
import com.sakayori.music.extensions.toPersistQueue
import com.sakayori.music.extensions.toQueue
import com.sakayori.music.lyrics.LyricsHelper
import com.sakayori.music.models.PersistPlayerState
import com.sakayori.music.models.PersistQueue
import com.sakayori.music.models.toMediaMetadata
import com.sakayori.music.playback.flac.isLosslessStreamFailure
import com.sakayori.music.playback.flac.FlacResource
import com.sakayori.music.playback.alarm.MusicAlarmScheduler
import com.sakayori.music.playback.alarm.MusicAlarmStore
import com.sakayori.music.playback.audio.SilenceDetectorAudioProcessor
import com.sakayori.music.playback.queues.EmptyQueue
import com.sakayori.music.playback.queues.ListQueue
import com.sakayori.music.playback.queues.Queue
import com.sakayori.music.playback.queues.YouTubeQueue
import com.sakayori.music.playback.queues.YouTubePlaylistQueue
import com.sakayori.music.playback.queues.filterExplicit
import com.sakayori.music.playback.queues.filterVideoSongs
import com.sakayori.music.constants.LoudnessLevel
import com.sakayori.music.constants.LoudnessLevelKey
import com.sakayori.music.utils.CoilBitmapLoader
import com.sakayori.music.utils.NetworkConnectivityObserver
import com.sakayori.music.utils.ScrobbleManager
import com.sakayori.music.utils.SyncUtils
import com.sakayori.music.utils.getArtistSeparator
import com.sakayori.music.utils.joinToArtistString
import com.sakayori.music.utils.YTPlayerUtils
import com.sakayori.music.utils.cipher.CipherDeobfuscator
import com.sakayori.music.utils.dataStore
import com.sakayori.music.utils.get
import com.sakayori.music.utils.reportException
import com.sakayori.music.widget.SakayoriWidgetManager
import com.sakayori.music.widget.MusicWidgetReceiver
import com.sakayori.music.widget.PlaylistWidgetReceiver
import com.sakayori.music.ui.utils.resize
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.random.Random
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

private const val INSTANT_SILENCE_SKIP_STEP_MS = 15_000L
private const val INSTANT_SILENCE_SKIP_SETTLE_MS = 350L

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var renditionStore: AudioRenditionStore

    @Inject
    lateinit var streamResolver: PlaybackStreamResolver

    @Inject
    lateinit var renditionSources: RenditionDataSources

    @Inject
    lateinit var queueStore: PlaybackQueueStore

    @Inject
    lateinit var losslessResolver: LosslessResolver

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    lateinit var equalizerService: EqualizerService

    @Inject
    lateinit var eqProfileRepository: EQProfileRepository

    @Inject
    lateinit var widgetManager: SakayoriWidgetManager

    @Inject
    lateinit var listenTogetherManager: com.sakayori.music.listentogether.ListenTogetherManager

    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var lastAudioFocusState = AudioManager.AUDIOFOCUS_NONE
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false
    private var reentrantFocusGain = false
    private var wasPlayingBeforeVolumeMute = false
    private var isPausedByVolumeMute = false

    private var crossfadeEnabled = false
    private var crossfadeDuration = 5000f
    private var crossfadeGapless = true
    private var crossfadeMessage: PlayerMessage? = null

    private val secondaryPlayerListener =
        object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Timber.tag(TAG).e(error, "Secondary player error")
                crossfadePreparationJob?.cancel()
                secondaryPlayer?.let { failedPlayer ->
                    failedPlayer.removeListener(this)
                    failedPlayer.stop()
                    failedPlayer.clearMediaItems()
                    releaseExoPlayer(failedPlayer)
                }
                secondaryPlayer = null
            }
        }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)
    val currentStreamClient = MutableStateFlow<String?>(null)
    val currentRendition = MutableStateFlow<AudioRendition?>(null)
    internal var losslessFallbackInProgress = false
        private set

    @Volatile private var audioQuality = com.sakayori.music.constants.AudioQuality.AUTO
    @Volatile private var preferLossless = false
    private var configuredLosslessApi = ""
    @Volatile private var audioSelectionVersion = 0L
    @Volatile private var cachedSongCacheEnabled = true
    @Volatile private var activeRenditionKey: String? = null
    @Volatile private var activeRenditionDescriptor: AudioRendition? = null
    @Volatile private var persistedRenditionKey: String? = null
    private var formatPublishJob: Job? = null
    private var formatPublishKey: String? = null
    private val formatPublicationMutex = Mutex()
    private val sourceOwners = mutableMapOf<Player, Any>()
    @Volatile private var activeSourceOwner: Any? = null
    private val sourceSelections = mutableMapOf<Any, LinkedHashMap<String, SelectedAudio>>()
    private val qualityLookups = ConcurrentHashMap<Any, Pair<String, LosslessLookup>>()
    private val forcedRenditions = ConcurrentHashMap<Any, FlacResource>()
    private val renditionPreparationBudgets = ConcurrentHashMap<Any, PreloadReadBudget>()
    private var renditionLookupJob: Job? = null
    private var renditionChangeJob: Job? = null
    private var renditionFallbackJob: Job? = null
    private var renditionAttempt = 0L
    private var qualityCandidate: ExoPlayer? = null
    private var normalFallbackPlayer: ExoPlayer? = null
    private var qualityFading = false
    private class Audibility(var silent: Boolean) {
        var playing = false
        var everAudible = false
        var changedAtMs = SystemClock.elapsedRealtime()
        var excludedPlayTimeMs = 0L
        fun update(silent: Boolean = this.silent, playing: Boolean = this.playing) {
            val now = SystemClock.elapsedRealtime()
            if (this.silent && this.playing) excludedPlayTimeMs += now - changedAtMs
            changedAtMs = now
            this.silent = silent
            this.playing = playing
            if (!silent && playing) everAudible = true
        }
    }
    private val playerAudibility = mutableMapOf<ExoPlayer, Audibility>()
    private val preloadControllers = mutableMapOf<ExoPlayer, NextItemPreloadController>()
    private var preloadNetworkAllowed = false
    private var preloadMemoryConstrained = false
    private var preloadCacheAvailable = true
    private var preloadRoomActive = false
    private var losslessWarmTarget: String? = null
    private val cacheClearTasks = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<Unit>>()

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null
    private var playQueueGeneration = 0
    private var isLoadingMore = false
    private var queueLoadJob: Job? = null
    private var queuePageJob: Job? = null
    // Existing native/controller queues have no pending initial load. Explicit
    // playQueue requests replace this with their uncommitted generation first.
    private val _queueLoadState = MutableStateFlow(QueueLoadState(committed = true))
    internal val queueLoadState = _queueLoadState.asStateFlow()
    @Volatile internal var isSplicingInitialQueue = false
    private var automixRequestSequence = 0L
    private var automixJob: Job? = null
    private data class AutomixRequestOwner(val queueGeneration: Int, val sequence: Long)

    val currentMediaMetadata = MutableStateFlow<com.sakayori.music.models.MediaMetadata?>(null)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            combine(database.format(mediaMetadata?.id), currentRendition) { saved, active ->
                if (active?.mediaId == mediaMetadata?.id && active != null) active.toPlaybackFormat() else saved?.toPlaybackFormat()
            }
        }

    lateinit var playerVolume: MutableStateFlow<Float>
    val isMuted = MutableStateFlow(false)
    private val sleepTimerVolumeMultiplier = MutableStateFlow(1f)
    private val audioFocusVolumeMultiplier = MutableStateFlow(1f)

    fun toggleMute() {
        val newMutedState = !isMuted.value
        isMuted.value = newMutedState
        applyEffectiveVolume()
    }

    fun setMuted(muted: Boolean) {
        isMuted.value = muted
        applyEffectiveVolume()
    }

    private fun calculateEffectiveVolume(
        volume: Float = playerVolume.value,
        muted: Boolean = isMuted.value,
        sleepTimerMultiplier: Float = sleepTimerVolumeMultiplier.value,
        focusMultiplier: Float = audioFocusVolumeMultiplier.value,
    ): Float {
        if (muted) return 0f
        return (volume * sleepTimerMultiplier * focusMultiplier).coerceIn(0f, 1f)
    }

    private fun applyEffectiveVolume() {
        if (!::player.isInitialized || isCrossfading || qualityFading) return
        player.volume = calculateEffectiveVolume()
    }

    var sleepTimer: SleepTimer? = null

    @Inject
    @PlayerCache
    lateinit var playerCache: Cache

    @Inject
    @DownloadCache
    lateinit var downloadCache: Cache

    lateinit var player: ExoPlayer
        private set
    private var secondaryPlayer: ExoPlayer? = null
    private var fadingPlayer: ExoPlayer? = null
    private var isCrossfading = false
    private var crossfadeJob: Job? = null
    private var crossfadePreparationJob: Job? = null
    private var crossfadeOutgoingMediaId: String? = null
    private var crossfadeOutgoingCompleted = false
    private val isRunning: Boolean
        get() = runningInstance === this
    private var mediaSession: MediaLibrarySession? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    private val playerInitialized = MutableStateFlow(false)
    val isPlayerReady: kotlinx.coroutines.flow.StateFlow<Boolean> = playerInitialized.asStateFlow()

    // Single batch-read of all DataStore preferences needed during onCreate().
    // Populated once at the very top of onCreate() to replace 15+ individual
    // runBlocking reads that were each blocking the main thread.
    @Volatile
    private var startupPrefs: Preferences? = null

    internal val playbackPreferences: Preferences?
        get() = startupPrefs

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow = _playerFlow.asStateFlow()

    private val playerSilenceProcessors = HashMap<Player, SilenceDetectorAudioProcessor>()

    private val instantSilenceSkipEnabled = MutableStateFlow(false)

    private var isAudioEffectSessionOpened = false
    private var openedAudioEffectSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private val playerNormalizationProcessors = HashMap<Player, VolumeNormalizationAudioProcessor>()
    private val playerEqualizerProcessors = HashMap<Player, CustomEqualizerAudioProcessor>()

    private var loudnessSetupJob: Job? = null
    private var loudnessSetupGeneration: Long = 0L

    @Volatile
    private var normalizationEnabledCached: Boolean = false

    @Volatile
    private var loudnessLevelCached: LoudnessLevel = LoudnessLevel.BALANCED

    private var cachedNormalizationGainMb: Int? = null
    private var cachedNormalizationEnabled: Boolean = false

    @Volatile private var discordRpcEnabled = false
    @Volatile private var lastDiscordReconnectAttemptAtMs: Long = 0L
    @Volatile private var discordIntentionalDisconnect = false
    @Volatile private var isScreenOff = false
    private val screenOffHandler = Handler(Looper.getMainLooper())
    private val screenOffTimeout = Runnable {
        Timber.tag("DiscordSvc").i("screenOffTimeout: isPlaying=%s, isReady=%s",
            player.isPlaying, DiscordRpcManager.isReady())
        if (!player.isPlaying && DiscordRpcManager.isReady()) {
            Timber.tag("DiscordSvc").i("screenOffTimeout: disconnecting after long idle")
            discordIntentionalDisconnect = true
            DiscordRpcManager.disconnect()
        }
    }
    private val pauseTimeout = Runnable {
        Timber.tag("DiscordSvc").i("pauseTimeout: isPlaying=%s, isReady=%s",
            player.isPlaying, DiscordRpcManager.isReady())
        if (!player.isPlaying && DiscordRpcManager.isReady()) {
            Timber.tag("DiscordSvc").i("pauseTimeout: disconnecting after 1m paused")
            discordIntentionalDisconnect = true
            DiscordRpcManager.disconnect()
        }
    }
    private var lastPlaybackSpeed = 1.0f

    @Volatile
    private var latestMediaNotification: Notification? = null

    private var scrobbleManager: ScrobbleManager? = null

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    // Tracks the original queue size to distinguish original items from auto-added ones
    private var originalQueueSize: Int = 0

    private var consecutivePlaybackErr = 0
    private var retryJob: Job? = null
    private var retryCount = 0
    private var initialBufferRecoveryJob: Job? = null
    private var initialBufferRecoveryAttemptedMediaId: String? = null
    // True only when stopOnError() paused playback purely because of a network outage
    // (waitOnNetworkError exhausting its attempts). Lets triggerRetry() know it's safe —
    // and necessary — to explicitly resume playback once connectivity returns, rather than
    // leaving the player "prepared but paused" forever.
    private var pausedDueToNetworkError = false
    private var silenceSkipJob: Job? = null

    // Cached preferences to avoid runBlocking DataStore reads in hot paths
    @Volatile
    private var cachedPersistentQueue = true
    @Volatile
    private var cachedAutoplay = true
    @Volatile
    private var cachedDisableLoadMoreWhenRepeatAll = false
    @Volatile
    private var cachedHideExplicit = false
    @Volatile
    private var cachedHideVideoSongs = false
    @Volatile
    private var cachedShufflePlaylistFirst = false
    @Volatile
    private var cachedAutoLoadMore = true

    // Preserve the recovery call sites while sharing URL resolution with downloads.
    private val songUrlCache: PlaybackStreamResolver get() = streamResolver

    // Tracks mediaIds for which a recoverSong() coroutine is currently in flight.
    //
    // resolveDataSpec() (createDataSourceFactory()) calls recoverSong() on every
    // dataSpec/chunk resolution, not just once per song. For a song cached a long
    // time ago the cache index can be fragmented into many small CacheSpans
    // (interrupted downloads, LeastRecentlyUsedCacheEvictor reclaiming arbitrary
    // spans), so a single playback can re-resolve dozens or hundreds of times in a
    // very short window. Without this guard, every single resolve would launch its
    // own coroutine doing a Room read + a hop to Dispatchers.Main + a Room
    // transaction — all redundant, since they all converge on the same mediaId and
    // mostly no-op. If those launches outpace how fast they can drain (e.g. the
    // Main thread is busy with playback/UI work), dozens of them pile up in memory
    // at once, which is enough to blow past this app's heap limit on low-RAM
    // devices and surface as an OutOfMemoryError that looks like a leak.
    //
    // This set makes recoverSong() effectively a no-op while a call for the same
    // mediaId is already running, so at most one is ever in flight per song.
    private val recoveringSongs = Collections.synchronizedSet(mutableSetOf<String>())

    private val sessionKey
        get() = YouTube.dataSyncId.takeIf { !it.isNullOrBlank() }
            ?: YouTube.visitorData.takeIf { !it.isNullOrBlank() }
            ?: ""

    private fun cacheKey(mediaId: String) = "${sessionKey}:$mediaId"

    private val playbackUrlCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(0, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean {
                return size > 500
            }
        }
    )

    // Tokens prevent an obsolete source callback from consuming a newer recovery request.
    private val freshAudioSelections = ConcurrentHashMap<String, Long>()
    private var nextFreshAudioSelectionToken = 0L

    private var currentMediaIdRetryCount = mutableMapOf<String, Int>()
    private val MAX_RETRY_PER_SONG = 3
    private val RETRY_DELAY_MS = 1000L

    var castConnectionHandler: CastConnectionHandler? = null
        private set

    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(
                context: Context,
                intent: Intent,
            ) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> {
                        isScreenOff = true
                        Timber.tag("DiscordSvc").i("SCREEN_OFF: cancelling pause timeout, delaying disconnect 10m")
                        screenOffHandler.removeCallbacks(pauseTimeout)
                        screenOffHandler.postDelayed(screenOffTimeout, 600_000)
                    }

                    Intent.ACTION_SCREEN_ON -> {
                        isScreenOff = false
                        Timber.tag("DiscordSvc").i("SCREEN_ON: removing disconnect delay")
                        screenOffHandler.removeCallbacks(screenOffTimeout)
                        screenOffHandler.removeCallbacks(pauseTimeout)
                        discordIntentionalDisconnect = false
                        syncDiscordState()
                    }
                }
            }
        }

    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                super.onAudioDevicesAdded(addedDevices)
                val hasBluetooth =
                    addedDevices?.any {
                        it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                    } == true

                if (hasBluetooth) {
                    if (startupPrefs?.get(ResumeOnBluetoothConnectKey) == true) {
                        if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                            player.play()
                        }
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
        shutdownDeferred = kotlinx.coroutines.CompletableDeferred<Unit>()

        setListener(
            object : MediaSessionService.Listener {
                override fun onForegroundServiceStartNotAllowedException() {
                    handleForegroundServiceStartNotAllowed(null)
                }
            },
        )

        playerInitialized.value = false

        // Call startForeground() as early as possible to satisfy the
        // 5-second timeout imposed by Context.startForegroundService().
        // On some OEMs (e.g. MIUI), even a DataStore read can be slow
        // enough to miss the window, so we promote before any I/O.
        ensureForegroundChannelExists()
        if (!ensureStartedAsForegroundOrStop()) {
            return
        }

        // Read ALL startup preferences in one shot so that subsequent code
        // never calls dataStore.get() (which does runBlocking internally).
        // This consolidates ~15 main-thread-blocking DataStore reads into 1.
        startupPrefs = (application as? App)?.playbackPreferences
            ?: runBlocking(Dispatchers.IO) { dataStore.data.first() }
        cachedPersistentQueue = startupPrefs!![PersistentQueueKey] ?: true
        cachedHideExplicit = startupPrefs!![HideExplicitKey] ?: false
        cachedHideVideoSongs = startupPrefs!![HideVideoSongsKey] ?: false
        cachedShufflePlaylistFirst = startupPrefs!![ShufflePlaylistFirstKey] ?: false
        cachedAutoLoadMore = startupPrefs!![AutoLoadMoreKey] ?: true
        cachedAutoplay = startupPrefs!![AutoplayKey] ?: true
        cachedDisableLoadMoreWhenRepeatAll = startupPrefs!![DisableLoadMoreWhenRepeatAllKey] ?: false
        queueStore.enabled = cachedPersistentQueue
        scope.launch { dataStore.data.collect { startupPrefs = it } }
        preloadMemoryConstrained = getSystemService<ActivityManager>()?.let { it.isLowRamDevice || it.memoryClass < 128 } ?: true
        preloadCacheAvailable = startupPrefs!![MaxSongCacheSizeKey] != 0
        crossfadeEnabled = startupPrefs!![CrossfadeEnabledKey] ?: false

        // 3. Connect the processor to the service
        // handled in createExoPlayer

        seedLoudnessCacheFromPrefs()

        val defaultMediaNotificationProvider =
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player,
            ).apply {
                setSmallIcon(R.drawable.small_icon)
            }

        setMediaNotificationProvider(
            object : MediaNotification.Provider {
                override fun createNotification(
                    mediaSession: MediaSession,
                    mediaButtonPreferences: ImmutableList<CommandButton>,
                    actionFactory: MediaNotification.ActionFactory,
                    onNotificationChangedCallback: MediaNotification.Provider.Callback,
                ): MediaNotification {
                    val trackingCallback =
                        MediaNotification.Provider.Callback { notification ->
                            latestMediaNotification = notification.notification
                            onNotificationChangedCallback.onNotificationChanged(notification)
                        }

                    return defaultMediaNotificationProvider
                        .createNotification(
                            mediaSession,
                            mediaButtonPreferences,
                            actionFactory,
                            trackingCallback,
                        ).also { mediaNotification ->
                            latestMediaNotification = mediaNotification.notification
                        }
                }

                override fun handleCustomCommand(
                    session: MediaSession,
                    action: String,
                    extras: Bundle,
                ): Boolean = defaultMediaNotificationProvider.handleCustomCommand(session, action, extras)

                override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo =
                    defaultMediaNotificationProvider.notificationChannelInfo
            },
        )
        player = createExoPlayer(prefs = startupPrefs!!)
        activeSourceOwner = sourceOwners[player]
        player.addListener(this@MusicService)
        sleepTimer =
            SleepTimer(scope, player) { multiplier ->
                sleepTimerVolumeMultiplier.value = multiplier
            }
        player.addListener(sleepTimer!!)

        playerInitialized.value = true
        _playerFlow.value = player
        Timber.tag(TAG).d("Player successfully initialized")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setupAudioFocusRequest()

        mediaLibrarySessionCallback.apply {
            service = this@MusicService
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
            addToTargetPlaylist = ::addToTargetPlaylist
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        player.repeatMode = startupPrefs!![RepeatModeKey] ?: REPEAT_MODE_OFF

        if (startupPrefs!![RememberShuffleAndRepeatKey] ?: true) {
            player.shuffleModeEnabled = startupPrefs!![ShuffleModeKey] ?: false
        }

        // Keep a connected controller so that notification works
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                controllerFuture?.get()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to initialize MediaController")
                controllerFuture = null
                stopSelf()
            }
        }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        val screenStateFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
        registerReceiver(screenStateReceiver, screenStateFilter)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        audioQuality = startupPrefs!![AudioQualityKey]?.let { value ->
            if (value == "VERY_HIGH") com.sakayori.music.constants.AudioQuality.HIGH
            else com.sakayori.music.constants.AudioQuality.entries.find { it.name == value }
        } ?: com.sakayori.music.constants.AudioQuality.AUTO
        cachedSongCacheEnabled = startupPrefs!![EnableSongCacheKey] ?: true
        preferLossless = startupPrefs!![PreferLosslessKey] ?: false
        configuredLosslessApi = startupPrefs!![LosslessApiUrlKey].orEmpty()
        playerVolume = MutableStateFlow((startupPrefs!![PlayerVolumeKey] ?: 1f).coerceIn(0f, 1f))

        initializeCast()

        // Collecting this flow activates the internal map that updates lyricsProviders in LyricsHelper
        lyricsHelper.preferred.collectLatest(scope) {}

        // 4. Watch for EQ profile changes
        scope.launch {
            eqProfileRepository.activeProfile.collect { profile ->
                if (profile != null) {
                    val result = equalizerService.applyProfile(profile)
                    if (result.isSuccess && player.playbackState == Player.STATE_READY && player.isPlaying) {
                        // Instant update: flush buffers and seek slightly to re-process audio
                        player.seekTo(player.currentPosition)
                    }
                } else {
                    equalizerService.disable()
                    if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                        player.seekTo(player.currentPosition)
                    }
                }
            }
        }

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                preloadNetworkAllowed = isConnected && !connectivityManager.isActiveNetworkMetered &&
                    connectivityManager.restrictBackgroundStatus != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
                updateNativePreloading()
                if (isConnected && waitingForNetworkConnection.value) {
                    triggerRetry()
                }
                if (isConnected && DiscordRpcManager.isReady()) {
                    Timber.tag("DiscordSvc").i("Network reconnected, syncing RPC")
                    syncDiscordState()
                }
            }
        }

        // Watch for audio quality setting changes
        scope.launch {
            dataStore.data.map { (it[PreferLosslessKey] ?: false) to it[LosslessApiUrlKey].orEmpty() }
                .distinctUntilChanged().collect { (preferred, api) ->
                val wasPreferred = preferLossless
                if (preferred == preferLossless && api == configuredLosslessApi) return@collect
                preferLossless = preferred
                configuredLosslessApi = api
                if (!wasPreferred && !preferred) return@collect
                if (!preferred) losslessResolver.cancelPending()
                if (!preferred && normalFallbackPlayer != null) {
                    player.currentMediaItem?.mediaId?.let(::returnToNormalPlayer)
                }
                cancelRenditionChanges()
                updateNativePreloading()
                if (preferred && currentRendition.value?.provider != AudioRendition.DIRECT_FLAC) {
                    player.currentMediaItem?.mediaId?.let { startRenditionLookup(it) }
                }
            }
        }
        var isFirstQualityEmit = true
        scope.launch {
            dataStore.data
                .map {
                    it[AudioQualityKey]?.let { value ->
                        if (value == "VERY_HIGH") com.sakayori.music.constants.AudioQuality.HIGH
                        else com.sakayori.music.constants.AudioQuality.entries.find { it.name == value }
                    } ?: com.sakayori.music.constants.AudioQuality.AUTO
                }.distinctUntilChanged()
                .collect { newQuality ->
                    val oldQuality = audioQuality
                    audioQuality = newQuality
                    if (oldQuality != newQuality) audioSelectionVersion++
                    updateNativePreloading()

                    if (isFirstQualityEmit) {
                        isFirstQualityEmit = false
                        Timber.tag(TAG).i("QUALITY INIT: $newQuality")
                        return@collect
                    }

                    Timber.tag(TAG).i("QUALITY CHANGED: $oldQuality -> $newQuality")

                    val mediaId = player.currentMediaItem?.mediaId ?: return@collect
                    val currentPosition = player.currentPosition
                    val wasPlaying = player.playWhenReady
                    val currentIndex = player.currentMediaItemIndex

                    Timber.tag(TAG).i("RELOADING STREAM: $mediaId at position ${currentPosition}ms")

                    // A new rendition gets a distinct byte key. Keep downloaded originals and
                    // cached alternatives; there is no synchronous disk deletion on this tap.
                    freshAudioSelections[mediaId] = ++nextFreshAudioSelectionToken
                    Timber.tag(TAG).d("Set bypass cache flag for $mediaId")

                    cancelCrossfadeForReload()
                    player.stop()
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    if (wasPlaying) {
                        player.play()
                    }
                }
        }

        combine(
            playerVolume,
            isMuted,
            sleepTimerVolumeMultiplier,
            audioFocusVolumeMultiplier,
        ) { volume, muted, timerMultiplier, focusMultiplier ->
            calculateEffectiveVolume(
                volume = volume,
                muted = muted,
                sleepTimerMultiplier = timerMultiplier,
                focusMultiplier = focusMultiplier,
            )
        }.collectLatest(scope) {
            if (!isCrossfading && !qualityFading) {
                player.volume = it
            }
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            safeDataStoreEdit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
            updateWidgetUI(player.isPlaying)
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { it[ShowLyricsKey] ?: false }.distinctUntilChanged(),
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database
                    .lyrics(mediaMetadata.id)
                    .first() == null
            ) {
                val lyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = lyricsWithProvider.lyrics,
                            provider = lyricsWithProvider.provider,
                        ),
                    )
                }
            }
        }

        dataStore.data
            .map { (it[SkipSilenceKey] ?: false) to (it[SkipSilenceInstantKey] ?: false) }
            .distinctUntilChanged()
            .collectLatest(scope) { (skipSilence, instantSkip) ->
                player.skipSilenceEnabled = skipSilence
                secondaryPlayer?.skipSilenceEnabled = skipSilence
                normalFallbackPlayer?.skipSilenceEnabled = skipSilence

                val enableInstant = skipSilence && instantSkip
                instantSilenceSkipEnabled.value = enableInstant

                playerSilenceProcessors.values.forEach { processor ->
                    processor.instantModeEnabled = enableInstant
                    if (!enableInstant) {
                        processor.resetTracking()
                    }
                }

                if (!enableInstant) {
                    silenceSkipJob?.cancel()
                }
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
            dataStore.data
                .map { prefs -> prefs[LoudnessLevelKey].toEnum(LoudnessLevel.BALANCED) }
                .distinctUntilChanged(),
        ) { format, normalizeAudio, loudnessLevel ->
            Triple(format, normalizeAudio, loudnessLevel)
        }.collectLatest(scope) { (format, normalizeAudio, loudnessLevel) ->
            normalizationEnabledCached = normalizeAudio
            loudnessLevelCached = loudnessLevel
            setupAudioNormalization()
        }

        combine(
            dataStore.data.map { it[AudioOffload] ?: false },
            dataStore.data.map { it[CrossfadeEnabledKey] ?: false },
        ) { offloadPref, crossfadeEnabled ->
            // Force disable offload if crossfade is enabled to prevent volume ramp issues
            if (crossfadeEnabled) false else offloadPref
        }.distinctUntilChanged()
            .collectLatest(scope) { useOffload ->
                player.setOffloadEnabled(useOffload)
                secondaryPlayer?.setOffloadEnabled(useOffload)
            }

        var isFirstAudioTrackParamsEmit = true
        dataStore.data
            .map { it[AudioTrackPlaybackParamsKey] ?: true }
            .distinctUntilChanged()
            .collectLatest(scope) { useAudioTrackParams ->
                if (isFirstAudioTrackParamsEmit) {
                    isFirstAudioTrackParamsEmit = false
                    return@collectLatest
                }

                Timber.tag("MusicService").i("AudioTrackPlaybackParams changed to: $useAudioTrackParams")

                cancelCrossfadeForReload()
                val currentIndex = player.currentMediaItemIndex
                val currentPosition = player.currentPosition
                val playWhenReady = player.playWhenReady
                val repeatMode = player.repeatMode
                val shuffleModeEnabled = player.shuffleModeEnabled
                val playbackParameters = player.playbackParameters
                val volume = player.volume
                val mediaItems = List(player.mediaItemCount) { index ->
                    player.getMediaItemAt(index)
                }

                player.removeListener(this)
                sleepTimer?.let { player.removeListener(it) }
                releaseExoPlayer(player)

                val newPlayer = createExoPlayer(useAudioTrackPlaybackParamsOverride = useAudioTrackParams)
                // setMediaItems can notify listeners synchronously. Publish the actual owner
                // before attaching callbacks, so they never read the released previous player.
                player = newPlayer
                activeSourceOwner = sourceOwners[newPlayer]
                _playerFlow.value = newPlayer
                newPlayer.addListener(this@MusicService)
                sleepTimer?.let { newPlayer.addListener(it) }

                sleepTimer?.player = newPlayer

                try {
                    mediaSession?.let { (it as MediaSession).player = newPlayer }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Failed to swap player in MediaSession")
                }

                newPlayer.setMediaItems(mediaItems, currentIndex, currentPosition)
                newPlayer.repeatMode = repeatMode
                newPlayer.shuffleModeEnabled = shuffleModeEnabled
                newPlayer.playbackParameters = playbackParameters
                newPlayer.volume = volume
                newPlayer.playWhenReady = playWhenReady
                newPlayer.prepare()

                updateNativePreloading()

                Timber.tag("MusicService").i("Player recreated with AudioTrackPlaybackParams: $useAudioTrackParams")
            }

        // Initialize Discord RPC manager (rehydrates token, reconnects gateway)
        if (!DiscordRpcManager.isInitialized()) {
            DiscordRpcManager.init(this@MusicService)
        }

        dataStore.data
            .map { it[EnableDiscordRPCKey] ?: true }
            .debounce(300)
            .distinctUntilChanged()
            .collect(scope) { enabled ->
                Timber.tag("DiscordSvc").i("RPC toggle: enabled=%s, isReady=%s, hasToken=%s",
                    enabled, DiscordRpcManager.isReady(), DiscordRpcManager.getAccessToken() != null)
                discordRpcEnabled = enabled
                if (enabled) {
                    discordIntentionalDisconnect = false
                    if (DiscordRpcManager.isReady()) {
                        Timber.tag("DiscordSvc").i("RPC toggle: already ready, syncing RPC")
                        syncDiscordState()
                    } else if (DiscordRpcManager.getAccessToken() != null) {
                        Timber.tag("DiscordSvc").i("RPC toggle: not ready but has token, reconnecting")
                        scope.launch(Dispatchers.IO) {
                            if (!DiscordRpcManager.isInitialized()) {
                                Timber.tag("DiscordSvc").i("RPC toggle: initializing")
                                DiscordRpcManager.init(this@MusicService)
                            }
                            DiscordRpcManager.reconnectWithToken(DiscordRpcManager.getAccessToken()!!)
                        }
                    } else {
                        Timber.tag("DiscordSvc").w("RPC toggle: enabled but no token and not ready")
                    }
                } else if (DiscordRpcManager.isReady()) {
                    Timber.tag("DiscordSvc").i("RPC toggle: disabled, disconnecting")
                    scope.launch(Dispatchers.IO) {
                        DiscordRpcManager.disconnect()
                    }
                }
            }

        DiscordRpcManager.accessTokenFlow.collectLatest(scope) { token ->
                Timber.tag("DiscordSvc").i("Token change: hasToken=%s, initialized=%s, authorized=%s, enabled=%s",
                    token != null, DiscordRpcManager.isInitialized(), DiscordRpcManager.isAuthorized(), discordRpcEnabled)
                if (token == null) {
                    if (DiscordRpcManager.isReady()) {
                        Timber.tag("DiscordSvc").i("Token change: empty token, disconnecting")
                        DiscordRpcManager.disconnect()
                    }
                    return@collectLatest
                }
                if (!discordRpcEnabled) {
                    Timber.tag("DiscordSvc").i("Token change: RPC disabled, skipping reconnect")
                    return@collectLatest
                }
                if (!DiscordRpcManager.isInitialized()) {
                    Timber.tag("DiscordSvc").i("Token change: initializing")
                    DiscordRpcManager.init(this@MusicService)
                }
                if (!DiscordRpcManager.isAuthorized()) {
                    Timber.tag("DiscordSvc").i("Token change: reconnecting with token")
                    DiscordRpcManager.reconnectWithToken(token)
                } else {
                    Timber.tag("DiscordSvc").i("Token change: already authorized, skipping reconnect")
                }
            }

        scope.launch {
            DiscordRpcManager.connectionStatus.collect { status ->
                Timber.tag("DiscordSvc").i("Status change: %s (discordRpcEnabled=%s, playing=%s)",
                    status, discordRpcEnabled, player.isPlaying)
                if (status == DiscordRpcManager.Status.Connected && discordRpcEnabled) {
                    Timber.tag("DiscordSvc").i("Status change: connected, syncing RPC")
                    syncDiscordState()
                }
            }
        }

        scope.launch {
            DiscordRpcManager.settingsChanged.collect {
                if (discordRpcEnabled && DiscordRpcManager.isReady()) {
                    Timber.tag("DiscordSvc").i("Settings changed, syncing RPC")
                    syncDiscordState()
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(5_000)
                Timber.tag("DiscordSvc").v("polling: periodic syncDiscordState tick")
                syncDiscordState()
            }
        }

        dataStore.data
            .map { it[EnableLastFMScrobblingKey] ?: false }
            .debounce(300)
            .distinctUntilChanged()
            .collect(scope) { enabled ->
                if (enabled && scrobbleManager == null) {
                    val delayPercent = dataStore.get(ScrobbleDelayPercentKey, LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT)
                    val minSongDuration =
                        dataStore.get(ScrobbleMinSongDurationKey, LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION)
                    val delaySeconds = dataStore.get(ScrobbleDelaySecondsKey, LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS)
                    scrobbleManager =
                        ScrobbleManager(
                            scope,
                            minSongDuration = minSongDuration,
                            scrobbleDelayPercent = delayPercent,
                            scrobbleDelaySeconds = delaySeconds,
                        )
                    scrobbleManager?.useNowPlaying = dataStore.get(LastFMUseNowPlaying, false)
                } else if (!enabled && scrobbleManager != null) {
                    scrobbleManager?.destroy()
                    scrobbleManager = null
                }
            }

        dataStore.data
            .map { it[LastFMUseNowPlaying] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                scrobbleManager?.useNowPlaying = it
            }

        dataStore.data
            .map { prefs ->
                Triple(
                    prefs[ScrobbleDelayPercentKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT,
                    prefs[ScrobbleMinSongDurationKey] ?: LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION,
                    prefs[ScrobbleDelaySecondsKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS,
                )
            }.distinctUntilChanged()
            .collect(scope) { (delayPercent, minSongDuration, delaySeconds) ->
                scrobbleManager?.let {
                    it.scrobbleDelayPercent = delayPercent
                    it.minSongDuration = minSongDuration
                    it.scrobbleDelaySeconds = delaySeconds
                }
            }

        combine(
            dataStore.data.map { prefs ->
                Triple(
                    prefs[CrossfadeEnabledKey] ?: false,
                    prefs[CrossfadeDurationKey] ?: 5f,
                    prefs[CrossfadeGaplessKey] ?: true,
                )
            },
            listenTogetherManager.roomState,
        ) { (enabled, duration, gapless), roomState ->
            Triple(enabled && roomState == null, duration, gapless)
        }.distinctUntilChanged()
            .collect(scope) { (enabled, duration, gapless) ->
                crossfadeEnabled = enabled
                crossfadeDuration = duration * 1000f // Convert to ms
                crossfadeGapless = gapless
                updateNativePreloading()
            }

        scope.launch {
            listenTogetherManager.roomState.map { it != null }.distinctUntilChanged().collect {
                preloadRoomActive = it
                if (it) cancelRenditionChanges()
                updateNativePreloading()
            }
        }
        scope.launch {
            YouTube.requestContextRevision.collect { updateNativePreloading() }
        }

        // Observe and cache common preferences to avoid runBlocking reads in playback callbacks
        scope.launch {
            dataStore.data.map { (it[EnableSongCacheKey] ?: true) to (it[MaxSongCacheSizeKey] != 0) }
                .distinctUntilChanged().collect { (enabled, available) ->
                    cachedSongCacheEnabled = enabled
                    preloadCacheAvailable = available
                    updateNativePreloading()
                }
        }
        scope.launch {
            dataStore.data.map { it[PersistentQueueKey] ?: true }.distinctUntilChanged().collect {
                cachedPersistentQueue = it
                queueStore.enabled = it
            }
        }
        scope.launch {
            dataStore.data.map { it[AutoplayKey] ?: true }.distinctUntilChanged().collect { cachedAutoplay = it }
        }
        scope.launch {
            dataStore.data.map { it[DisableLoadMoreWhenRepeatAllKey] ?: false }.distinctUntilChanged().collect { cachedDisableLoadMoreWhenRepeatAll = it }
        }
        scope.launch {
            dataStore.data.map { it[HideExplicitKey] ?: false }.distinctUntilChanged().collect { cachedHideExplicit = it }
        }
        scope.launch {
            dataStore.data.map { it[HideVideoSongsKey] ?: false }.distinctUntilChanged().collect { cachedHideVideoSongs = it }
        }
        scope.launch {
            dataStore.data.map { it[ShufflePlaylistFirstKey] ?: false }.distinctUntilChanged().collect { cachedShufflePlaylistFirst = it }
        }
        scope.launch {
            dataStore.data.map { it[AutoLoadMoreKey] ?: true }.distinctUntilChanged().collect { cachedAutoLoadMore = it }
        }
        // Keep YTPlayerUtils in sync with the stream source toggles (Settings → Stream sources).
        // Map to the derived set + distinctUntilChanged so an unrelated preference write doesn't
        // rebuild the set and rewrite the @Volatile field on every DataStore emission.
        scope.launch {
            dataStore.data
                .map { prefs ->
                    buildSet {
                        if (prefs[StreamSourceWebRemixKey] == false) add("WEB_REMIX")
                        if (prefs[StreamSourceTVHTML5Key] == false) add("TVHTML5")
                        if (prefs[StreamSourceAndroidVRKey] == false) add("ANDROID_VR")
                        if (prefs[StreamSourceVisionOSKey] == false) add("VISIONOS")
                        if (prefs[StreamSourceWebCreatorKey] == false) add("WEB_CREATOR")
                    }
                }
                .distinctUntilChanged()
                .collect {
                    YTPlayerUtils.disabledStreamClients = it
                    updateNativePreloading()
                }
        }

        if (cachedPersistentQueue) {
            val restoreGeneration = playQueueGeneration
            val initialVolume = playerVolume.value
            scope.launch(SilentHandler) {
                val saved = queueStore.load()
                val restored = withContext(Dispatchers.Default) {
                    val queue = saved.queue?.takeIf { it.items.isNotEmpty() }?.let {
                        it.copy(
                            mediaItemIndex = it.mediaItemIndex.coerceIn(0, it.items.lastIndex),
                            position = it.position.coerceAtLeast(0L),
                        ).toQueue()
                    }
                    queue to saved.automix?.items?.map { it.toMediaItem() }.orEmpty()
                }
                if (!isActive || !cachedPersistentQueue || playQueueGeneration != restoreGeneration || player.mediaItemCount != 0) {
                    return@launch
                }
                automixItems.value = restored.second
                if (playerVolume.value == initialVolume) {
                    saved.player?.volume?.takeIf { it.isFinite() }?.let { playerVolume.value = it.coerceIn(0f, 1f) }
                }
                // The queue file carries its own index/position. A delayed player-state seek
                // could otherwise overwrite a new user selection or a newer queue snapshot.
                restored.first?.let { playQueue(it, playWhenReady = false, restoringQueue = true) }
            }
        }

        // Save queue periodically to prevent queue loss from crash or force kill
        scope.launch {
            while (isActive) {
                delay(15.seconds)
                if (cachedPersistentQueue) {
                    saveQueueToDisk()
                }
                val currentMetadata = player.currentMediaItem?.metadata
                if (currentMetadata?.isEpisode == true && player.isPlaying && player.currentPosition > 0) {
                    previousEpisodePosition = player.currentPosition
                    saveEpisodePosition(currentMetadata.id, player.currentPosition)
                }
            }
        }

        scope.launch {
            while (isActive) {
                delay(10.seconds)
                if (cachedPersistentQueue && player.isPlaying) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun createExoPlayer(
        prefs: Preferences = checkNotNull(startupPrefs),
        useAudioTrackPlaybackParamsOverride: Boolean? = null,
        forcedResource: FlacResource? = null,
        preparationBudget: PreloadReadBudget? = null,
    ): ExoPlayer {
        val sourceOwner = Any()
        forcedResource?.let { forcedRenditions[sourceOwner] = it }
        preparationBudget?.let { renditionPreparationBudgets[sourceOwner] = it }
        val preloadController = NextItemPreloadController()
        val normalizationProcessor = VolumeNormalizationAudioProcessor().also {
            it.enabled = forcedResource == null && cachedNormalizationEnabled
            if (forcedResource == null) cachedNormalizationGainMb?.let { gain -> it.setTargetGain(gain) }
        }
        val eqProcessor = CustomEqualizerAudioProcessor()

        val silenceProcessor = SilenceDetectorAudioProcessor {
            scope.launch { if (activeSourceOwner === sourceOwner) handleLongSilenceDetected(sourceOwner) }
        }

        val skipSilence = prefs[SkipSilenceKey] ?: false
        val instantSkip = prefs[SkipSilenceInstantKey] ?: false
        silenceProcessor.instantModeEnabled = skipSilence && instantSkip
        val useAudioTrackPlaybackParams = useAudioTrackPlaybackParamsOverride ?: (prefs[AudioTrackPlaybackParamsKey] ?: true)

        val player = try {
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(createMediaSourceFactory(sourceOwner, preloadController))
                .setRenderersFactory(createRenderersFactory(normalizationProcessor, eqProcessor, silenceProcessor, useAudioTrackPlaybackParams))
                .setLoadControl(
                    // Start playback once ~750ms is buffered (media3's default is 1000ms) so first
                    // audio is audible a touch sooner. min/max/after-rebuffer match the media3 1.x
                    // defaults (50s / 50s / 2000ms) so buffering and post-stall recovery are unchanged.
                    preloadController.wrap(DefaultLoadControl
                        .Builder()
                        .setBufferDurationsMs(50_000, 50_000, 750, 2_000)
                        .build()),
                )
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_NETWORK)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    false,
                ).setSeekBackIncrementMs(5000)
                .setSeekForwardIncrementMs(5000)
                .setDeviceVolumeControlEnabled(true)
                .build()
        } catch (error: Exception) {
            forcedRenditions.remove(sourceOwner)
            renditionPreparationBudgets.remove(sourceOwner)?.cancel()
            throw error
        }

        playerNormalizationProcessors[player] = normalizationProcessor
        sourceOwners[player] = sourceOwner
        preloadControllers[player] = preloadController
        playerSilenceProcessors[player] = silenceProcessor
        playerEqualizerProcessors[player] = eqProcessor
        equalizerService.addAudioProcessor(eqProcessor)

        val offload = prefs[AudioOffload] ?: false
        val crossfade = prefs[CrossfadeEnabledKey] ?: false
        player.setOffloadEnabled(if (crossfade) false else offload)
        player.skipSilenceEnabled = skipSilence
        val audibility = Audibility(silent = forcedResource != null)
        playerAudibility[player] = audibility
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { audibility.update(playing = isPlaying) }
        })
        player.addAnalyticsListener(PlaybackStatsListener(false, object : PlaybackStatsListener.Callback {
            override fun onPlaybackStatsReady(eventTime: AnalyticsListener.EventTime, playbackStats: PlaybackStats) {
                audibility.update()
                if (audibility.everAudible) recordPlaybackStats(eventTime,
                    (playbackStats.totalPlayTimeMs - audibility.excludedPlayTimeMs).coerceAtLeast(0L))
                audibility.excludedPlayTimeMs = 0L
            }
        }))

        // Factories also create silent crossfade players. Only explicit active-player handoffs
        // publish playerFlow, never construction of a provisional player.
        return player
    }

    private fun setupAudioFocusRequest() {
        audioFocusRequest =
            AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes
                        .Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                ).setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }.setAcceptsDelayedFocusGain(true)
                .build()
    }

    private fun handleAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN,
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT,
            -> {
                hasAudioFocus = true
                audioFocusVolumeMultiplier.value = 1f

                if (wasPlayingBeforeAudioFocusLoss && !player.isPlaying && !reentrantFocusGain) {
                    reentrantFocusGain = true
                    scope.launch {
                        delay(300)
                        if (hasAudioFocus && wasPlayingBeforeAudioFocusLoss && !player.isPlaying) {
                            if (castConnectionHandler?.isCasting?.value != true) {
                                player.play()
                            }
                            wasPlayingBeforeAudioFocusLoss = false
                        }
                        reentrantFocusGain = false
                    }
                }

                applyEffectiveVolume()
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                audioFocusVolumeMultiplier.value = 1f
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                }
                abandonAudioFocus()
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                audioFocusVolumeMultiplier.value = 1f
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    player.pause()
                }
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                audioFocusVolumeMultiplier.value = 0.2f
                wasPlayingBeforeAudioFocusLoss = player.isPlaying
                if (player.isPlaying) {
                    applyEffectiveVolume()
                }
                lastAudioFocusState = focusChange
            }

            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                hasAudioFocus = true
                audioFocusVolumeMultiplier.value = 1f
                applyEffectiveVolume()
                lastAudioFocusState = focusChange
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true

        audioFocusRequest?.let { request ->
            val result = audioManager.requestAudioFocus(request)
            hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            return hasAudioFocus
        }
        return false
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioFocusRequest?.let { request ->
                audioManager.abandonAudioFocusRequest(request)
                hasAudioFocus = false
            }
        }
    }

    fun hasAudioFocusForPlayback(): Boolean = hasAudioFocus

    private fun waitOnNetworkError() {
        if (waitingForNetworkConnection.value) return

        // Always arm the reconnect listener (connectivityObserver.networkStatus.collect)
        // before anything else can return early. Previously, hitting MAX_RETRY_COUNT while
        // still offline called stopOnError() and returned WITHOUT setting
        // waitingForNetworkConnection = true. That meant the "isConnected && waitingFor...
        // -> triggerRetry()" listener never fired once the network actually came back —
        // the player was left paused in a post-error state, and since ExoPlayer requires an
        // explicit prepare() after a fatal error before play() does anything, no song would
        // play again until the app was killed and relaunched (which recreates the player).
        waitingForNetworkConnection.value = true
        pausedDueToNetworkError = false

        if (retryCount >= MAX_RETRY_COUNT) {
            Timber.tag(TAG).w("Max retry count ($MAX_RETRY_COUNT) reached, pausing until network returns")
            pausedDueToNetworkError = true
            stopOnError()
            retryCount = 0
            // Don't schedule another backoff job — we're out of attempts for now — but stay
            // "waiting" so the connectivity listener can still auto-resume on reconnect.
            retryJob?.cancel()
            return
        }

        retryJob?.cancel()
        retryJob =
            scope.launch {
                // Exponential backoff: 3s, 6s, 12s, 24s... max 30s
                val delayMs = minOf(3000L * (1 shl retryCount), 30000L)
                Timber.tag(TAG).d("Waiting ${delayMs}ms before retry attempt ${retryCount + 1}/$MAX_RETRY_COUNT")
                delay(delayMs)

                if (isNetworkConnected.value && waitingForNetworkConnection.value) {
                    retryCount++
                    triggerRetry()
                }
                // If still offline when the timer fires, just let the job end — we stay
                // "waiting" and the connectivityObserver listener (not this job) is what
                // will catch the eventual reconnection and call triggerRetry().
            }
    }

    private fun triggerRetry() {
        waitingForNetworkConnection.value = false
        retryJob?.cancel()
        val shouldResumePlayback = pausedDueToNetworkError
        pausedDueToNetworkError = false

        if (player.currentMediaItem != null) {
            // After 3+ failed retries, try to refresh the stream URL by seeking to current position
            // This forces ExoPlayer to re-resolve the data source and get a fresh URL
            if (retryCount > 3) {
                Timber.tag(TAG).d("Retry count > 3, attempting to refresh stream URL")
                val currentPosition = player.currentPosition
                player.seekTo(player.currentMediaItemIndex, currentPosition)
            }
            player.prepare()
            if (shouldResumePlayback) {
                // We explicitly paused this ourselves (stopOnError) purely because of the
                // network outage — playWhenReady is now false, so prepare() alone would just
                // sit there "ready but paused" until the user manually pressed play again on
                // this exact item. Resume explicitly so reconnecting actually resumes audio.
                player.playWhenReady = true
            }
            // Otherwise (we never force-paused), leave playWhenReady as-is and let the
            // player auto-resume on its own — this avoids stealing audio focus on ordinary
            // mid-stream retries where the user never lost the "should be playing" intent.
        }
    }

    private fun skipOnError() {
        /**
         * Auto skip to the next media item on error.
         *
         * To prevent a "runaway diesel engine" scenario, force the user to take action after
         * too many errors come up too quickly. Pause to show player "stopped" state
         */
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            if (castConnectionHandler?.isCasting?.value != true) {
                player.play()
            }
            return
        }

        player.pause()
        consecutivePlaybackErr = 0
    }

    private fun stopOnError() {
        player.pause()
    }

    private fun updateNotification(isLiked: Boolean? = currentSong.value?.song?.let { if (it.isEpisode) it.inLibrary != null else it.liked }) {
        mediaSession?.setCustomLayout(
            listOf(
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            if (isLiked == true) {
                                R.string.action_remove_like
                            } else {
                                R.string.action_like
                            },
                        ),
                    ).setIconResId(if (isLiked == true) R.drawable.ic_heart else R.drawable.ic_heart_outline)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> throw IllegalStateException()
                            },
                        ),
                    ).setIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_OFF -> R.drawable.repeat
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> throw IllegalStateException()
                        },
                    ).setSessionCommand(CommandToggleRepeatMode)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                    .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setSessionCommand(CommandToggleShuffle)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(R.string.start_radio))
                    .setIconResId(R.drawable.radio)
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton
                    .Builder()
                    .setDisplayName(getString(R.string.android_auto_target_playlist))
                    .setIconResId(R.drawable.playlist_add)
                    .setSessionCommand(CommandAddToTargetPlaylist)
                    .setEnabled(currentSong.value != null)
                    .build(),
            ),
        )
    }

    /**
     * Registers / refreshes song metadata (title, duration, isVideo, related songs)
     * for [mediaId]. Pure metadata bookkeeping only — does NOT touch [dateDownload].
     *
     * Looks across player, secondaryPlayer and fadingPlayer so metadata is still
     * found correctly while a crossfade swap is in progress.
     *
     * Safe to call frequently (e.g. on every dataSpec resolution) since it no longer
     * has any side effect tied to caching completeness.
     */
    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null,
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata =
            withContext(Dispatchers.Main) {
                player.findNextMediaItemById(mediaId)?.metadata
                    ?: secondaryPlayer?.findNextMediaItemById(mediaId)?.metadata
                    ?: fadingPlayer?.findNextMediaItemById(mediaId)?.metadata
            }

        if (mediaMetadata == null && song == null) return

        val duration =
            song?.song?.duration?.takeIf { it != -1 }
                ?: mediaMetadata?.duration?.takeIf { it != -1 }
                ?: (
                    playbackData?.videoDetails ?: YTPlayerUtils
                        .playerResponseForMetadata(mediaId)
                        .getOrNull()
                        ?.videoDetails
                )?.lengthSeconds?.toInt()
                ?: -1

        database.query {
            if (song == null && mediaMetadata != null) {
                insert(mediaMetadata.copy(duration = duration))
            } else if (song != null) {
                var updatedSong = song.song
                if (song.song.duration == -1) {
                    updatedSong = updatedSong.copy(duration = duration)
                }
                if (mediaMetadata != null && song.song.isVideo != mediaMetadata.isVideoSong) {
                    updatedSong = updatedSong.copy(isVideo = mediaMetadata.isVideoSong)
                }
                if (updatedSong != song.song) {
                    update(updatedSong)
                }
            }
        }

        if (!database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id,
                        )
                    }.forEach(::insert)
            }
        }
    }

    /**
     * Launches [recoverSong] for [mediaId] unless a call for the same mediaId is
     * already in flight, in which case this is a no-op.
     *
     * recoverSong() is called from resolveDataSpec() on every dataSpec/chunk
     * resolution rather than once per song, so without this guard a heavily
     * fragmented (e.g. long-cached) file can fan out dozens of redundant,
     * concurrent recoverSong() coroutines — each doing a Room read, a hop to
     * Dispatchers.Main, and a Room transaction — for work that's already done
     * after the first one completes. Always call this instead of launching
     * recoverSong() directly.
     */
    private fun recoverSongDeduped(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null,
    ) {
        if (!recoveringSongs.add(mediaId)) return
        scope.launch(Dispatchers.IO) {
            try {
                recoverSong(mediaId, playbackData)
            } finally {
                recoveringSongs.remove(mediaId)
            }
        }
    }

    /**
     * Marks [mediaId] as belonging to the Cache Playlist by setting [dateDownload],
     * but ONLY if the full file (byte 0 through contentLength) is actually present
     * in playerCache. This must only be called from a genuine "track finished
     * naturally" signal (see onMediaItemTransition's AUTO-reason handling) —
     * never from raw dataSpec/chunk resolution, since the player's background
     * prefetch can finish downloading a short file in seconds, long before the
     * user has actually listened to it (or even if they skipped away early).
     *
     * The rendition store can establish completeness without a YouTube format row.
     */
    private suspend fun markCachedIfFullyDownloaded(mediaId: String) {
        val song = database.song(mediaId).first() ?: return
        if (song.song.dateDownload != null || song.song.isDownloaded) return
        val contentLength = song.format?.contentLength
        if (!renditionStore.isFullyCached(mediaId, contentLength)) return
        database.markPlaybackCached(mediaId, java.time.LocalDateTime.now())
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
        restoringQueue: Boolean = false,
    ): Int? {
        if (!scope.isActive) return null
        val wasCommitted = _queueLoadState.value.committed
        val generation = ++playQueueGeneration
        _queueLoadState.value = QueueLoadState(generation)
        invalidateAutomixRequests()
        queueLoadJob?.cancel()
        queuePageJob?.cancel()
        isLoadingMore = false
        if (!playerInitialized.value) {
            Timber.tag(TAG).w("playQueue called before player initialization, queuing request")
            scope.launch {
                playerInitialized.first { it }
                if (generation == playQueueGeneration) startQueue(queue, playWhenReady, restoringQueue, generation, false)
            }
        } else {
            startQueue(queue, playWhenReady, restoringQueue, generation, wasCommitted)
        }
        return generation
    }

    private fun matchesLoadedListQueue(items: List<MediaItem>): Boolean {
        if (items.isEmpty() || player.mediaItemCount != items.size) return false
        val timeline = player.currentTimeline
        if (timeline.windowCount != items.size) return false
        for (i in items.indices) {
            if (player.getMediaItemAt(i).mediaId != items[i].mediaId) return false
        }
        return true
    }

    private fun startQueue(
        queue: Queue,
        playWhenReady: Boolean,
        restoringQueue: Boolean,
        generation: Int,
        wasCommitted: Boolean = false,
    ) {
        if (generation != playQueueGeneration || !playerInitialized.value) return
        cancelCrossfadeForReload()
        if (generation != playQueueGeneration) return
        val persistShuffleAcrossQueues = startupPrefs?.get(PersistentShuffleAcrossQueuesKey) ?: false
        if (!persistShuffleAcrossQueues && !restoringQueue) {
            player.shuffleModeEnabled = false
        }
        if (generation != playQueueGeneration) return
        if (wasCommitted && !restoringQueue && !player.shuffleModeEnabled &&
            player.playbackState != STATE_IDLE && queue is ListQueue &&
            !cachedHideExplicit && !cachedHideVideoSongs && matchesLoadedListQueue(queue.items)
        ) {
            currentQueue = queue
            queueTitle = queue.title
            originalQueueSize = queue.items.size
            val targetIndex = queue.startIndex.coerceIn(0, queue.items.lastIndex)
            val targetPosition = queue.preloadPosition.coerceAtLeast(0L)
            player.seekTo(targetIndex, targetPosition)
            if (generation != playQueueGeneration || currentQueue !== queue) return
            player.playWhenReady = playWhenReady
            if (generation != playQueueGeneration || currentQueue !== queue) return
            _queueLoadState.value = QueueLoadState(generation, committed = true)
            queueLoadJob = scope.launch(SilentHandler) {
                if (generation == playQueueGeneration && currentQueue === queue && cachedPersistentQueue) {
                    saveQueueToDisk()
                }
            }
            return
        }
        currentQueue = queue
        queueTitle = null
        if (generation != playQueueGeneration || currentQueue !== queue) return
        originalQueueSize = 0
        val preload = queue.preloadItem?.takeUnless {
            (cachedHideExplicit && it.explicit) || (cachedHideVideoSongs && it.isVideoSong)
        }?.let { metadata ->
            if (queue is ListQueue) queue.items.getOrNull(queue.startIndex) else metadata.toMediaItem()
        }
        if (preload != null) {
            player.setMediaItem(preload, queue.preloadPosition.coerceAtLeast(0L))
            if (generation != playQueueGeneration || currentQueue !== queue) return
            player.prepare()
            if (generation != playQueueGeneration || currentQueue !== queue) return
            player.playWhenReady = playWhenReady
        }
        if (generation != playQueueGeneration || currentQueue !== queue) return
        val preloadPlayer = player
        val preloadUid = if (preload != null && player.currentMediaItemIndex in 0 until player.currentTimeline.windowCount) {
            player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window()).uid
        } else null
        queueLoadJob = scope.launch(SilentHandler) {
            val initialStatus =
                withContext(Dispatchers.IO) {
                    queue
                        .getInitialStatus()
                        .filterExplicit(cachedHideExplicit)
                        .filterVideoSongs(cachedHideVideoSongs)
                }
            if (generation != playQueueGeneration || currentQueue !== queue) return@launch
            if (preload != null && (player.playbackState == STATE_IDLE || player.mediaItemCount != 1 ||
                player.currentMediaItem?.mediaId != preload.mediaId)) return@launch
            if (preloadUid != null && player === preloadPlayer &&
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window()).uid != preloadUid) return@launch
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch
            // Track original queue size for shuffle playlist first feature
            originalQueueSize = initialStatus.items.size
            val initialIndex = initialStatus.mediaItemIndex.coerceIn(0, initialStatus.items.lastIndex)
            if (preload != null && initialStatus.items[initialIndex].mediaId == preload.mediaId) {
                val hasPrefix = initialIndex > 0
                val hasSuffix = initialIndex + 1 < initialStatus.items.size
                if (hasPrefix) {
                    isSplicingInitialQueue = hasSuffix
                    try {
                        player.addMediaItems(
                            0,
                            initialStatus.items.subList(0, initialIndex),
                        )
                    } finally {
                        isSplicingInitialQueue = false
                    }
                    if (generation != playQueueGeneration || currentQueue !== queue) return@launch
                }
                if (hasSuffix) {
                    player.addMediaItems(
                        initialStatus.items.subList(
                            initialIndex + 1,
                            initialStatus.items.size,
                        ),
                    )
                    if (generation != playQueueGeneration || currentQueue !== queue) return@launch
                }
            } else {
                player.setMediaItems(
                    initialStatus.items,
                    initialIndex,
                    initialStatus.position,
                )
                if (generation != playQueueGeneration || currentQueue !== queue) return@launch
                player.prepare()
                if (generation != playQueueGeneration || currentQueue !== queue) return@launch
                player.playWhenReady = playWhenReady
            }

            if (player.shuffleModeEnabled) {
                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, cachedShufflePlaylistFirst)
            }
            if (generation != playQueueGeneration || currentQueue !== queue) return@launch
            // Prefix/suffix, metadata and shuffle must all be installed before
            // automatic promotion sees a committed queue. Failed/stale loads
            // never grant that authority.
            _queueLoadState.value = QueueLoadState(generation, committed = true)
            if (cachedPersistentQueue) saveQueueToDisk()
        }
    }

    fun adoptQueue(queue: Queue, title: String? = null, initialQueueSize: Int = 0) {
        val generation = ++playQueueGeneration
        invalidateAutomixRequests()
        queueLoadJob?.cancel()
        queuePageJob?.cancel()
        isLoadingMore = false
        currentQueue = queue
        queueTitle = title
        originalQueueSize = initialQueueSize
        _queueLoadState.value = QueueLoadState(generation, committed = true)
    }

    fun startRadioSeamlessly() {
        if (!playerInitialized.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player initialization")
            return
        }

        val currentMediaMetadata = player.currentMetadata ?: return

        val currentMediaId = currentMediaMetadata.id

        scope.launch(SilentHandler) {
            // Use simple videoId to let YouTube personalize recommendations
            val radioQueue =
                YouTubeQueue(
                    endpoint =
                        WatchEndpoint(
                            videoId = currentMediaId,
                        ),
                )

            try {
                val initialStatus =
                    withContext(Dispatchers.IO) {
                        radioQueue
                            .getInitialStatus()
                            .filterExplicit(dataStore.get(HideExplicitKey, false))
                            .filterVideoSongs(dataStore.get(HideVideoSongsKey, false))
                    }

                if (initialStatus.title != null) {
                    queueTitle = initialStatus.title
                }

                val radioItems =
                    initialStatus.items.filter { item ->
                        item.mediaId != currentMediaId
                    }

                if (radioItems.isNotEmpty()) {
                    val liveIndex = player.currentMediaItemIndex
                    if (player.currentMediaItem?.mediaId != currentMediaId || liveIndex == C.INDEX_UNSET) return@launch
                    val itemCount = player.mediaItemCount

                    if (itemCount > liveIndex + 1) {
                        player.removeMediaItems(liveIndex + 1, itemCount)
                    }

                    player.addMediaItems(liveIndex + 1, radioItems)
                    if (player.shuffleModeEnabled) {
                        val shufflePlaylistFirst = cachedShufflePlaylistFirst
                        applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    }
                }

                currentQueue = radioQueue
            } catch (e: Exception) {
                try {
                    val nextResult =
                        withContext(Dispatchers.IO) {
                            YouTube.next(WatchEndpoint(videoId = currentMediaId)).getOrNull()
                        }
                    nextResult?.relatedEndpoint?.let { relatedEndpoint ->
                        val relatedPage =
                            withContext(Dispatchers.IO) {
                                YouTube.related(relatedEndpoint).getOrNull()
                            }
                        relatedPage?.songs?.let { songs ->
                            val radioItems =
                                songs
                                    .filter { it.id != currentMediaId }
                                    .map { it.toMediaItem() }
                                    .filterExplicit(cachedHideExplicit)
                                    .filterVideoSongs(cachedHideVideoSongs)

                            if (radioItems.isNotEmpty()) {
                                val liveIndex = player.currentMediaItemIndex
                                if (player.currentMediaItem?.mediaId != currentMediaId || liveIndex == C.INDEX_UNSET) return@launch
                                val itemCount = player.mediaItemCount
                                if (itemCount > liveIndex + 1) {
                                    player.removeMediaItems(liveIndex + 1, itemCount)
                                }
                                player.addMediaItems(liveIndex + 1, radioItems)
                                if (player.shuffleModeEnabled) {
                                    applyShuffleOrder(
                                        player.currentMediaItemIndex,
                                        player.mediaItemCount,
                                        cachedShufflePlaylistFirst,
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun getAutomixAlbum(albumId: String, requestGeneration: Int? = playQueueGeneration) {
        val owner = beginAutomixRequest(requestGeneration) ?: return
        automixJob = scope.launch(SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    if (isActiveAutomixOwner(owner)) loadAutomix(it.album.playlistId, owner)
                }
        }
    }

    fun getAutomix(playlistId: String, requestGeneration: Int? = playQueueGeneration) {
        val owner = beginAutomixRequest(requestGeneration) ?: return
        if (isAutomixAllowed()) {
            automixJob = scope.launch(SilentHandler) { loadAutomix(playlistId, owner) }
        }
    }

    private suspend fun loadAutomix(playlistId: String, owner: AutomixRequestOwner) {
        if (!isActiveAutomixOwner(owner) || !isAutomixAllowed()) return
        try {
            YouTube.next(WatchEndpoint(playlistId = playlistId))
                .onSuccess firstPage@ { firstResult ->
                    if (!isActiveAutomixOwner(owner)) return@firstPage
                    YouTube.next(WatchEndpoint(playlistId = firstResult.endpoint.playlistId))
                        .onSuccess { secondResult ->
                            publishAutomix(owner, secondResult.items.map { it.toMediaItem() })
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                            if (isActiveAutomixOwner(owner) && firstResult.items.isNotEmpty()) {
                                publishAutomix(owner, firstResult.items.map { it.toMediaItem() })
                            }
                        }
                }.onFailure playlistFailure@ { error ->
                    if (error is CancellationException) throw error
                    if (!isActiveAutomixOwner(owner) || !this::player.isInitialized) return@playlistFailure
                    val currentSong = player.currentMetadata ?: return@playlistFailure
                    // Preserve personalized current-track and related fallbacks.
                    YouTube.next(WatchEndpoint(videoId = currentSong.id))
                        .onSuccess { radioResult ->
                            val items = radioResult.items.filter { it.id != currentSong.id }.map { it.toMediaItem() }
                            if (items.isNotEmpty()) publishAutomix(owner, items)
                        }.onFailure radioFailure@ { failure ->
                            if (failure is CancellationException) throw failure
                            if (!isActiveAutomixOwner(owner)) return@radioFailure
                            YouTube.next(WatchEndpoint(videoId = currentSong.id)).getOrNull()
                                ?.relatedEndpoint?.let { endpoint ->
                                    YouTube.related(endpoint).onSuccess { relatedPage ->
                                        val items = relatedPage.songs.filter { it.id != currentSong.id }.map { it.toMediaItem() }
                                        if (items.isNotEmpty()) publishAutomix(owner, items)
                                    }
                                }
                        }
                }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
        }
    }

    private fun isAutomixAllowed(): Boolean {
        val prefs = startupPrefs
        val repeat = if (this::player.isInitialized) player.repeatMode else prefs?.get(RepeatModeKey) ?: REPEAT_MODE_OFF
        return prefs?.get(SimilarContent) != false &&
            !(prefs?.get(DisableLoadMoreWhenRepeatAllKey) == true && repeat == REPEAT_MODE_ALL)
    }

    private fun invalidateAutomixRequests() {
        ++automixRequestSequence
        automixJob?.cancel()
        automixJob = null
    }

    private fun beginAutomixRequest(generation: Int?): AutomixRequestOwner? {
        if (generation == null || generation != playQueueGeneration || !scope.isActive) return null
        invalidateAutomixRequests()
        return AutomixRequestOwner(generation, automixRequestSequence)
    }

    private suspend fun isActiveAutomixOwner(owner: AutomixRequestOwner): Boolean =
        currentCoroutineContext().isActive && scope.isActive &&
            owner.queueGeneration == playQueueGeneration && owner.sequence == automixRequestSequence

    private suspend fun publishAutomix(owner: AutomixRequestOwner, items: List<MediaItem>) {
        if (isActiveAutomixOwner(owner)) automixItems.value = items
    }

    internal fun promoteAutomixIfNeeded(
        canSkipNext: Boolean,
        items: List<MediaItem>,
        requestGeneration: Int = playQueueGeneration,
    ) {
        val load = _queueLoadState.value
        if (!load.committed || load.generation != requestGeneration || load.generation != playQueueGeneration ||
            !scope.isActive || !playerInitialized.value || !this::player.isInitialized || canSkipNext ||
            items !== automixItems.value || items.isEmpty() || player.mediaItemCount == 0 ||
            player.playbackState == STATE_IDLE || player.hasNextMediaItem()) return
        val timeline = player.currentTimeline
        val index = player.currentMediaItemIndex
        if (index !in 0 until timeline.windowCount) return
        val window = timeline.getWindow(index, Timeline.Window())
        if (window.isLive && window.isDynamic) return
        addToQueueAutomix(items[0], 0, items)
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
        expectedItems: List<MediaItem>? = null,
    ) {
        val items = automixItems.value
        if (!canConsumeAutomix(items, item, position, expectedItems)) return
        val generation = addToQueue(listOf(item)) ?: return
        if (generation == playQueueGeneration && automixItems.value === items) {
            automixItems.value = items.toMutableList().apply { removeAt(position) }
        }
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
        expectedItems: List<MediaItem>? = null,
    ) {
        val items = automixItems.value
        if (!canConsumeAutomix(items, item, position, expectedItems)) return
        val generation = playNext(listOf(item)) ?: return
        if (generation == playQueueGeneration && automixItems.value === items) {
            automixItems.value = items.toMutableList().apply { removeAt(position) }
        }
    }

    private fun canConsumeAutomix(
        items: List<MediaItem>,
        item: MediaItem,
        position: Int,
        expectedItems: List<MediaItem>?,
    ): Boolean = scope.isActive && playerInitialized.value && this::player.isInitialized &&
        player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS) &&
        (expectedItems == null || expectedItems === items) &&
        position in items.indices && items[position] === item

    private fun preserveManualEditDuringInitialization(commandGeneration: Int): Int? {
        // Player callbacks are synchronous: an earlier edit must not claim a
        // newer request admitted by one of its own timeline/prepare listeners.
        if (commandGeneration != playQueueGeneration) return null
        val load = _queueLoadState.value
        if (load.committed || load.generation != commandGeneration) return commandGeneration
        val generation = ++playQueueGeneration
        invalidateAutomixRequests()
        queueLoadJob?.cancel()
        queuePageJob?.cancel()
        isLoadingMore = false
        // The explicit edit wins; a late initial result may not replace it.
        // Copy the native view on its application thread before adopting it.
        currentQueue = ListQueue(queueTitle, player.mediaItems.toList(), player.currentMediaItemIndex, player.currentPosition)
        originalQueueSize = player.mediaItemCount
        _queueLoadState.value = QueueLoadState(generation, committed = true)
        if (cachedPersistentQueue) saveQueueToDisk()
        return generation
    }

    fun clearAutomix() {
        invalidateAutomixRequests()
        automixItems.value = emptyList()
    }

    fun playNext(items: List<MediaItem>): Int? {
        val commandGeneration = playQueueGeneration
        // If queue is empty or player is idle, play immediately instead
        if (player.mediaItemCount == 0 || player.playbackState == STATE_IDLE) {
            player.setMediaItems(items)
            if (commandGeneration != playQueueGeneration) return null
            player.prepare()
            if (commandGeneration != playQueueGeneration) return null
            if (castConnectionHandler?.isCasting?.value != true) {
                player.play()
            }
            return if (items.isNotEmpty()) preserveManualEditDuringInitialization(commandGeneration)
                else commandGeneration.takeIf { it == playQueueGeneration }
        }

        if (startupPrefs?.get(PreventDuplicateTracksInQueueKey) == true) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            // Remove from highest index to lowest to maintain index stability
            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
                if (commandGeneration != playQueueGeneration) return null
            }
        }

        val insertIndex = player.currentMediaItemIndex + 1
        val shuffleEnabled = player.shuffleModeEnabled

        // Insert items immediately after the current item in the window/index space
        player.addMediaItems(insertIndex, items)
        if (commandGeneration != playQueueGeneration) return null
        player.prepare()
        if (commandGeneration != playQueueGeneration) return null

        if (shuffleEnabled) {
            // Rebuild shuffle order so that newly inserted items are played next
            val timeline = player.currentTimeline
            if (!timeline.isEmpty) {
                val size = timeline.windowCount
                val currentIndex = player.currentMediaItemIndex

                // Newly inserted indices are a contiguous range [insertIndex, insertIndex + items.size)
                val newIndices = (insertIndex until (insertIndex + items.size)).toSet()

                // Collect existing shuffle traversal order excluding current index
                val orderAfter = mutableListOf<Int>()
                var idx = currentIndex
                while (true) {
                    idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, /*shuffleModeEnabled=*/true)
                    if (idx == C.INDEX_UNSET) break
                    if (idx != currentIndex) orderAfter.add(idx)
                }

                val prevList = mutableListOf<Int>()
                var pIdx = currentIndex
                while (true) {
                    pIdx = timeline.getPreviousWindowIndex(pIdx, Player.REPEAT_MODE_OFF, /*shuffleModeEnabled=*/true)
                    if (pIdx == C.INDEX_UNSET) break
                    if (pIdx != currentIndex) prevList.add(pIdx)
                }
                prevList.reverse() // preserve original forward order

                val existingOrder = (prevList + orderAfter).filter { it != currentIndex && it !in newIndices }

                // Build new shuffle order: current -> newly inserted (in insertion order) -> rest
                val nextBlock = (insertIndex until (insertIndex + items.size)).toList()
                val finalOrder = IntArray(size)
                var pos = 0
                prevList
                    .filter { it !in newIndices }
                    .forEach { if (it in 0 until size) finalOrder[pos++] = it }
                finalOrder[pos++] = currentIndex
                nextBlock.forEach { if (it in 0 until size) finalOrder[pos++] = it }
                orderAfter
                    .filter { it !in newIndices }
                    .forEach { if (pos < size) finalOrder[pos++] = it }

                // Fill any missing indices (safety) to ensure a full permutation
                if (pos < size) {
                    for (i in 0 until size) {
                        if (!finalOrder.contains(i)) {
                            finalOrder[pos++] = i
                            if (pos == size) break
                        }
                    }
                }

                player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
                if (commandGeneration != playQueueGeneration) return null
            }
        }
        return if (items.isNotEmpty()) preserveManualEditDuringInitialization(commandGeneration)
            else commandGeneration.takeIf { it == playQueueGeneration }
    }

    fun addToQueue(items: List<MediaItem>): Int? {
        val commandGeneration = playQueueGeneration
        if (startupPrefs?.get(PreventDuplicateTracksInQueueKey) == true) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            // Remove from highest index to lowest to maintain index stability
            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
                if (commandGeneration != playQueueGeneration) return null
            }
        }

        player.addMediaItems(items)
        if (commandGeneration != playQueueGeneration) return null
        if (player.shuffleModeEnabled) {
            val shufflePlaylistFirst = cachedShufflePlaylistFirst
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
            if (commandGeneration != playQueueGeneration) return null
        }
        player.prepare()
        if (commandGeneration != playQueueGeneration) return null
        return if (items.isNotEmpty()) preserveManualEditDuringInitialization(commandGeneration)
            else commandGeneration
    }

    fun toggleLibrary() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val isInLibrary = it.song.inLibrary != null
                val token = if (isInLibrary) it.song.libraryRemoveToken else it.song.libraryAddToken

                token?.let { feedbackToken ->
                    YouTube.feedback(listOf(feedbackToken))
                }

                database.query {
                    update(it.song.toggleLibrary())
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleLike() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let { librarySong ->
                val songEntity = librarySong.song

                // For podcast episodes, toggle save for later instead of like
                if (songEntity.isEpisode) {
                    toggleEpisodeSaveForLater(songEntity)
                    return@let
                }

                val song = songEntity.toggleLike()

                updateNotification(isLiked = song.liked)
                updateWidgetUI(player.isPlaying, isLiked = song.liked)

                database.query {
                    update(song)
                    syncUtils.likeSong(song)

                    if (dataStore.get(AutoDownloadOnLikeKey, false) && song.liked) {
                        val downloadRequest =
                            androidx.media3.exoplayer.offline.DownloadRequest
                                .Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.title.toByteArray())
                                .build()
                        androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                            this@MusicService,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false,
                        )
                    }
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun addToTargetPlaylist() {
        scope.launch {
            val currentSong = currentSong.first() ?: return@launch
            val targetPlaylistId = dataStore.get(AndroidAutoTargetPlaylistKey, MediaSessionConstants.TARGET_PLAYLIST_AUTO)

            if (targetPlaylistId == MediaSessionConstants.TARGET_PLAYLIST_AUTO) {
                Handler(Looper.getMainLooper()).post {
                    Toast
                        .makeText(
                            this@MusicService,
                            getString(R.string.android_auto_target_playlist_not_set),
                            Toast.LENGTH_SHORT,
                        ).show()
                }
                return@launch
            }

            val targetPlaylist = database.playlist(targetPlaylistId).first()
            if (targetPlaylist != null) {
                database.addSongsToPlaylist(targetPlaylist, listOf(currentSong.id to null), prepend = true)
            }
        }
    }

    private suspend fun toggleEpisodeSaveForLater(songEntity: com.sakayori.music.db.entities.SongEntity) {
        val isCurrentlySaved = songEntity.inLibrary != null
        val shouldBeSaved = !isCurrentlySaved

        updateNotification(isLiked = shouldBeSaved)
        updateWidgetUI(player.isPlaying, isLiked = shouldBeSaved)

        // Update database first (optimistic update)
        // Also ensure isEpisode = true so it appears in saved episodes list
        database.query {
            update(
                songEntity.copy(
                    inLibrary = if (isCurrentlySaved) null else java.time.LocalDateTime.now(),
                    isEpisode = true,
                ),
            )
        }
        currentMediaMetadata.value = player.currentMetadata

        // Sync with YouTube (handles login check internally)
        val setVideoId = if (isCurrentlySaved) database.getSetVideoId(songEntity.id)?.setVideoId else null
        syncUtils.saveEpisode(songEntity.id, shouldBeSaved, setVideoId)
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    private fun seedLoudnessCacheFromPrefs() {
        val prefs = startupPrefs!!
        normalizationEnabledCached = prefs[AudioNormalizationKey] ?: true
        loudnessLevelCached = prefs[LoudnessLevelKey].toEnum(LoudnessLevel.BALANCED)

        Timber.tag(TAG).d(
            "Seeded loudness cache: normalization=$normalizationEnabledCached, level=$loudnessLevelCached"
        )
    }

    private fun applyCachedAudioNormalizationNow() {
        if (isCrossfading) return
        try {
            val gain = cachedNormalizationGainMb
            if (cachedNormalizationEnabled && gain != null) {
                normalizationTargets().forEach {
                    it.setTargetGain(gain)
                    it.enabled = true
                }
            } else {
                normalizationTargets().forEach { it.enabled = false }
            }
        } catch (e: Exception) {
            reportException(e)
            normalizationTargets().forEach { it.enabled = false }
        }
    }

    private fun setupAudioNormalization() {
        val requestGeneration = ++loudnessSetupGeneration
        loudnessSetupJob?.cancel()

        loudnessSetupJob = scope.launch {
            try {
                val currentMediaId = withContext(Dispatchers.Main) {
                    player.currentMediaItem?.mediaId
                }

                val normalizeAudio = normalizationEnabledCached

                if (normalizeAudio && currentMediaId != null) {
                    val format = currentRendition.value?.takeIf { it.mediaId == currentMediaId }?.toPlaybackFormat()
                        ?: withContext(Dispatchers.IO) { database.formatOnce(currentMediaId)?.toPlaybackFormat() }

                    val targetLufs = loudnessLevelCached.targetLufs

                    Timber.tag(TAG).d("Audio normalization enabled: $normalizeAudio")
                    
                    val measuredLufs: Double? = format?.perceptualLoudnessDb
                        ?: format?.loudnessDb?.let { it + LoudnessLevel.AGGRESSIVE.targetLufs }

                    withContext(Dispatchers.Main) {
                        if (!isActive || requestGeneration != loudnessSetupGeneration) return@withContext
                        if (player.currentMediaItem?.mediaId != currentMediaId) return@withContext

                        when {
                            measuredLufs != null -> {
                                val loudnessDb = measuredLufs - targetLufs
                                val targetGain = (-loudnessDb * 100.0).toInt()
                                val clampedGain = targetGain.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)

                                cachedNormalizationGainMb = clampedGain
                                cachedNormalizationEnabled = true
                                if (isCrossfading) {
                                    playerNormalizationProcessors[player]?.let {
                                        it.setTargetGain(clampedGain)
                                        it.enabled = true
                                    }
                                } else {
                                    normalizationTargets().forEach {
                                        it.setTargetGain(clampedGain)
                                        it.enabled = true
                                    }
                                }
                            }
                            format == null -> {
                                Timber.tag(TAG).d("Loudness row not ready yet; keeping cached normalization state")
                                if (isCrossfading) return@withContext
                            }
                            else -> {
                                cachedNormalizationGainMb = 0
                                cachedNormalizationEnabled = false
                                val processors = normalizationTargets()
                                processors.forEach {
                                    it.setTargetGain(0)
                                    it.enabled = false
                                }
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        if (!isActive || requestGeneration != loudnessSetupGeneration) return@withContext
                        cachedNormalizationGainMb = null
                        cachedNormalizationEnabled = false
                        playerNormalizationProcessors.values.forEach { it.enabled = false }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                normalizationTargets().forEach { it.enabled = false }
            }
        }
    }

    private fun normalizationTargets(): Collection<VolumeNormalizationAudioProcessor> =
        if (isCrossfading || qualityCandidate != null || normalFallbackPlayer != null) listOfNotNull(playerNormalizationProcessors[player])
        else playerNormalizationProcessors.values

    private fun openAudioEffectSession() {
        val audioSessionId = player.audioSessionId
        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            return
        }

        if (isAudioEffectSessionOpened && openedAudioEffectSessionId == audioSessionId) {
            return
        }

        if (isAudioEffectSessionOpened && openedAudioEffectSessionId > 0) {
            closeAudioEffectSession(sessionIdOverride = openedAudioEffectSessionId, clearNormalizationCache = false)
        }

        isAudioEffectSessionOpened = true
        openedAudioEffectSessionId = audioSessionId

        sendBroadcast(
            Intent(android.media.audiofx.AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun closeAudioEffectSession(sessionIdOverride: Int? = null, clearNormalizationCache: Boolean = true) {
        val sessionIdToClose = sessionIdOverride ?: openedAudioEffectSessionId

        loudnessSetupGeneration++
        loudnessSetupJob?.cancel()
        loudnessSetupJob = null

        // Guard: only release/reset state if closing the currently active session
        val isClosingCurrentSession =
            isAudioEffectSessionOpened &&
                    openedAudioEffectSessionId != C.AUDIO_SESSION_ID_UNSET &&
                    sessionIdToClose == openedAudioEffectSessionId

        if (isClosingCurrentSession) {

            isAudioEffectSessionOpened = false
            openedAudioEffectSessionId = C.AUDIO_SESSION_ID_UNSET
        }

        if (sessionIdToClose != C.AUDIO_SESSION_ID_UNSET && sessionIdToClose > 0) {
            sendBroadcast(
                Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sessionIdToClose)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                },
            )
        }
    }

    private var previousMediaItemIndex = C.INDEX_UNSET
    private var previousEpisodeId: String? = null

    // Tracks the mediaId that was playing immediately before the current
    // onMediaItemTransition call, so we can decide whether IT finished
    // naturally (and is therefore safe to mark as fully cached).
    private var lastTransitionedMediaId: String? = null
    private var previousEpisodePosition: Long = 0L

    /**
     * Save podcast episode playback position to database.
     * Only saves if the item is an episode and position is meaningful (> 3 seconds).
     */
    private fun saveEpisodePosition(
        episodeId: String,
        positionMs: Long,
    ) {
        if (positionMs < 3000) return // Don't save if less than 3 seconds played
        scope.launch(Dispatchers.IO + SilentHandler) {
            database.updatePlaybackPosition(episodeId, positionMs)
            Timber.tag(TAG).d("Saved episode position: $episodeId at ${positionMs}ms")
        }
    }

    /**
     * Restore podcast episode playback position from database.
     * Seeks to saved position if available.
     */
    private fun restoreEpisodePosition(episodeId: String) {
        scope.launch(Dispatchers.IO + SilentHandler) {
            val savedPosition = database.getPlaybackPosition(episodeId)
            if (savedPosition != null && savedPosition > 0) {
                withContext(Dispatchers.Main) {
                    if (player.currentMediaItem?.mediaId == episodeId) {
                        player.seekTo(savedPosition)
                        Timber.tag(TAG).d("Restored episode position: $episodeId to ${savedPosition}ms")
                    }
                }
            }
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        silenceSkipJob?.cancel()
        silenceSkipJob = null
        isSilenceSkipping = false
        if (!isCrossfading && secondaryPlayer != null && secondaryPlayer !== qualityCandidate) {
            crossfadePreparationJob?.cancel()
        }
        cancelRenditionChanges()
        mediaItem?.metadata?.let(losslessResolver::remember)
        mediaItem?.mediaId?.let {
            losslessResolver.beginPlayback(it)
            startRenditionLookup(it)
        }
        val nextId = player.nextMediaItemIndex.takeIf { it != C.INDEX_UNSET }?.let { player.getMediaItemAt(it).mediaId }
        losslessResolver.retainPlaybackLookups(setOfNotNull(mediaItem?.mediaId, nextId))
        losslessFallbackInProgress = false
        currentRendition.value = null
        activeRenditionKey = null
        activeRenditionDescriptor = null
        persistedRenditionKey = null
        currentStreamClient.value = null
        formatPublishJob?.cancel()
        // The track that was playing before this transition only gets marked as
        // "fully cached" if it advanced AUTOmatically (i.e. it actually finished),
        // never on a manual skip/seek. lastTransitionedMediaId must be read BEFORE
        // it gets overwritten below.
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            lastTransitionedMediaId?.let { previousId ->
                scope.launch(Dispatchers.IO) { markCachedIfFullyDownloaded(previousId) }
            }
        }
        lastTransitionedMediaId = mediaItem?.mediaId
        initialBufferRecoveryJob?.cancel()
        initialBufferRecoveryJob = null
        initialBufferRecoveryAttemptedMediaId = null
        retryJob?.cancel()
        retryJob = null
        updateInitialBufferRecovery(player.playbackState)

        previousEpisodeId?.let { episodeId ->
            if (previousEpisodePosition > 0) {
                saveEpisodePosition(episodeId, previousEpisodePosition)
            }
        }
        previousEpisodeId = null
        previousEpisodePosition = 0L

        val newMetadata = mediaItem?.metadata
        if (newMetadata?.isEpisode == true) {
            previousEpisodeId = newMetadata.id
            scope.launch {
                delay(100)
                restoreEpisodePosition(newMetadata.id)
            }
        }

        // Force Repeat One if the player ignored it and auto-advanced
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            if (player.repeatMode == REPEAT_MODE_ONE &&
                previousMediaItemIndex != C.INDEX_UNSET &&
                previousMediaItemIndex != player.currentMediaItemIndex
            ) {
                player.seekTo(previousMediaItemIndex, 0)
            }
        }
        previousMediaItemIndex = player.currentMediaItemIndex

        lastPlaybackSpeed = -1.0f // force update song

        setupAudioNormalization()

        scrobbleManager?.onSongStop()
        if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
            scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
        }

        // Skip if this change was triggered by Cast sync (to prevent loops)
        if (castConnectionHandler?.isCasting?.value == true &&
            castConnectionHandler?.isSyncingFromCast != true &&
            mediaItem != null
        ) {
            val metadata = mediaItem.metadata
            if (metadata != null) {
                // Try to navigate to the item if it's already in Cast queue
                // This avoids a full reload which causes the widget to refresh
                val navigated = castConnectionHandler?.navigateToMediaIfInQueue(metadata.id) ?: false
                if (!navigated) {
                    castConnectionHandler?.loadMedia(metadata)
                }
            }
        }

        if (cachedAutoLoadMore &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage() &&
            !(cachedDisableLoadMoreWhenRepeatAll && player.repeatMode == REPEAT_MODE_ALL) &&
            !isLoadingMore
        ) {
            isLoadingMore = true
            val pagedQueue = currentQueue
            val generation = playQueueGeneration
            queuePageJob = scope.launch(SilentHandler) {
                try {
                    val mediaItems =
                        withContext(Dispatchers.IO) {
                            pagedQueue
                                .nextPage()
                                .filterExplicit(cachedHideExplicit)
                                .filterVideoSongs(cachedHideVideoSongs)
                        }
                    if (generation == playQueueGeneration && currentQueue === pagedQueue &&
                        player.playbackState != STATE_IDLE && mediaItems.isNotEmpty()) {
                        player.addMediaItems(mediaItems)
                        if (player.shuffleModeEnabled) {
                            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, cachedShufflePlaylistFirst)
                        }
                    }
                } finally {
                    if (generation == playQueueGeneration && currentQueue === pagedQueue) isLoadingMore = false
                }
            }
        }

        if (cachedPersistentQueue && _queueLoadState.value.committed) {
            saveQueueToDisk()
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        updateInitialBufferRecovery(playbackState)

        if (playbackState == Player.STATE_ENDED) {
            // Check sleep timer guard - don't autoplay/repeat if sleep timer will pause
            val timer = sleepTimer ?: return
            if (timer.isActive && timer.pauseWhenSongEnd) {
                return
            }

            val repeatMode = player.repeatMode

            if (repeatMode == REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                player.seekTo(0, 0)
                player.prepare()
                player.play()
                return
            }

            if (repeatMode == REPEAT_MODE_ONE) {
                player.seekTo(player.currentMediaItemIndex, 0)
                player.prepare()
                player.play()
                return
            }

            if (cachedAutoplay && player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.prepare()
                if (castConnectionHandler?.isCasting?.value != true) {
                    player.play()
                }
            }
        }

        // Save state when playback state changes (but not during silence skipping)
        if (cachedPersistentQueue && !isSilenceSkipping && _queueLoadState.value.committed) {
            saveQueueToDisk()
        }

        if (playbackState == Player.STATE_READY) {
            losslessFallbackInProgress = false
            consecutivePlaybackErr = 0
            retryCount = 0
            waitingForNetworkConnection.value = false
            retryJob?.cancel()

            player.currentMediaItem?.mediaId?.let { mediaId ->
                resetRetryCount(mediaId)
                Timber.tag(TAG).d("Playback successful for $mediaId, reset retry count")
            }
            scheduleCrossfade()
        }

        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            scrobbleManager?.onSongStop()
        }
    }

    override fun onPlayWhenReadyChanged(
        playWhenReady: Boolean,
        reason: Int,
    ) {
        normalFallbackPlayer?.playWhenReady = playWhenReady
        qualityCandidate?.playWhenReady = playWhenReady
        // Safety net: if local player tries to start while casting, immediately pause it
        if (playWhenReady && castConnectionHandler?.isCasting?.value == true) {
            player.pause()
            return
        }

        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            if (playWhenReady) {
                isPausedByVolumeMute = false
            }

            if (!playWhenReady && !isPausedByVolumeMute) {
                wasPlayingBeforeVolumeMute = false
            }
        }

        if (!playWhenReady) {
            val currentMetadata = player.currentMediaItem?.metadata
            if (currentMetadata?.isEpisode == true && player.currentPosition > 0) {
                saveEpisodePosition(currentMetadata.id, player.currentPosition)
                previousEpisodePosition = player.currentPosition
            }
        }

        if (playWhenReady) {
            applyCachedAudioNormalizationNow()
        }

        updateInitialBufferRecovery(player.playbackState)
    }

    private fun updateInitialBufferRecovery(
        @Player.State playbackState: Int,
    ) {
        val mediaId = player.currentMediaItem?.mediaId
        val shouldWatch =
            playbackState == Player.STATE_BUFFERING &&
                player.playWhenReady &&
                player.currentPosition < INITIAL_BUFFER_RECOVERY_POSITION_MS &&
                mediaId != null &&
                initialBufferRecoveryAttemptedMediaId != mediaId

        if (!shouldWatch) {
            initialBufferRecoveryJob?.cancel()
            initialBufferRecoveryJob = null
            return
        }
        if (initialBufferRecoveryJob?.isActive == true) return

        initialBufferRecoveryJob =
            scope.launch {
                delay(INITIAL_BUFFER_RECOVERY_DELAY_MS)
                if (player.playbackState != Player.STATE_BUFFERING ||
                    !player.playWhenReady ||
                    player.currentPosition >= INITIAL_BUFFER_RECOVERY_POSITION_MS ||
                    player.currentMediaItem?.mediaId != mediaId
                ) {
                    return@launch
                }

                initialBufferRecoveryAttemptedMediaId = mediaId
                val failedStreamClient = songUrlCache.clientName(mediaId)
                Timber.tag(TAG).w(
                    "Initial stream stalled, refreshing mediaId=%s client=%s",
                    mediaId,
                    failedStreamClient ?: "unknown",
                )
                performAggressiveCacheClear(mediaId)
                refreshStreamAndRetry(
                    mediaId = mediaId,
                    failedStreamClient = failedStreamClient,
                    refreshCipherConfig = false,
                    retryReason = "initial buffer stall",
                )
            }
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (player !== this.player) return
        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED, Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_IS_LOADING_CHANGED,
                Player.EVENT_REPEAT_MODE_CHANGED, Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY,
            )
        ) updateNativePreloading()
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_PLAYBACK_STATE_CHANGED, EVENT_TIMELINE_CHANGED)) {
            val mediaId = player.currentMediaItem?.mediaId
            if (mediaId != null) sourceOwners[player]?.let { owner ->
                sourceSelections[owner]?.get(mediaId)?.let { publishActiveRendition(owner, it) }
            }
        }
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
            )
        ) {
            scheduleCrossfade()
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                val focusGranted = requestAudioFocus()
                if (focusGranted) {
                    openAudioEffectSession()
                }
            } else if (player.playbackState == Player.STATE_IDLE) {
                closeAudioEffectSession()
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
            if (events.contains(EVENT_TIMELINE_CHANGED)) synchronizeNormalFallbackTimeline()
        }

        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            normalFallbackPlayer?.playWhenReady = player.isPlaying
            updateWidgetUI(player.isPlaying)
            if (player.isPlaying) {
                discordIntentionalDisconnect = false
                screenOffHandler.removeCallbacks(screenOffTimeout)
                screenOffHandler.removeCallbacks(pauseTimeout)
                startWidgetUpdates()
            } else {
                stopWidgetUpdates()
                if (isScreenOff) {
                    screenOffHandler.removeCallbacks(screenOffTimeout)
                    screenOffHandler.postDelayed(screenOffTimeout, 600_000)
                } else {
                    screenOffHandler.postDelayed(pauseTimeout, 60_000)
                }
            }
        }

        if (events.containsAny(
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                Player.EVENT_IS_PLAYING_CHANGED,
            )
        ) {
            syncDiscordState()
        }

        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            scrobbleManager?.onPlayerStateChanged(player.isPlaying, player.currentMetadata, duration = player.duration)
        }
    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {
            if (player.mediaItemCount == 0) return

            val shufflePlaylistFirst = cachedShufflePlaylistFirst
            val currentIndex = player.currentMediaItemIndex
            val totalCount = player.mediaItemCount

            applyShuffleOrder(currentIndex, totalCount, shufflePlaylistFirst)
        }

        if (startupPrefs?.get(RememberShuffleAndRepeatKey) != false) {
            scope.launch {
                safeDataStoreEdit { settings ->
                    settings[ShuffleModeKey] = shuffleModeEnabled
                }
            }
        }

        if (cachedPersistentQueue) {
            saveQueueToDisk()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            safeDataStoreEdit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }

        if (cachedPersistentQueue) {
            saveQueueToDisk()
        }
    }

    /**
     * Applies a new shuffle order to the player, maintaining the current item's position.
     * If `shufflePlaylistFirst` is true, it attempts to shuffle original items separately from added items.
     */
    private fun applyShuffleOrder(
        currentIndex: Int,
        totalCount: Int,
        shufflePlaylistFirst: Boolean,
    ) {
        if (totalCount == 0) return

        if (shufflePlaylistFirst && originalQueueSize > 0 && originalQueueSize < totalCount) {
            // Shuffle original items and added items separately
            val originalIndices = (0 until originalQueueSize).filter { it != currentIndex }.toMutableList()
            val addedIndices = (originalQueueSize until totalCount).filter { it != currentIndex }.toMutableList()

            originalIndices.shuffle()
            addedIndices.shuffle()

            val shuffledIndices = IntArray(totalCount)
            var pos = 0
            shuffledIndices[pos++] = currentIndex

            if (currentIndex < originalQueueSize) {
                originalIndices.forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            } else {
                (0 until originalQueueSize).shuffled().forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        } else {
            val shuffledIndices = IntArray(totalCount) { it }
            shuffledIndices.shuffle()
            // Ensure current item is first in the shuffle order
            val currentItemIndexInShuffled = shuffledIndices.indexOf(currentIndex)
            if (currentItemIndexInShuffled != -1) { // Should always be true if totalCount > 0
                val temp = shuffledIndices[0]
                shuffledIndices[0] = shuffledIndices[currentItemIndexInShuffled]
                shuffledIndices[currentItemIndexInShuffled] = temp
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        if (playbackParameters.speed != lastPlaybackSpeed) {
            Timber.tag("DiscordSvc").d("onPlaybackParametersChanged: speed changed %s -> %s", lastPlaybackSpeed, playbackParameters.speed)
            lastPlaybackSpeed = playbackParameters.speed
            DiscordRpcManager.notifySettingsChanged()
            scope.launch {
                delay(1000)
                if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                    syncDiscordState()
                }
            }
        }
    }

    /**
     * Extracts the HTTP response code from an error's cause chain.
     * Returns null if no HTTP response code is found.
     */
    private fun getHttpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    /**
     * Checks if the error is caused by an expired/forbidden URL (HTTP 403 or 410).
     * This typically happens when a YouTube stream URL expires.
     */
    private fun isExpiredUrlError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 403 || responseCode == 410
    }

    /**
     * Checks if the error is a Range Not Satisfiable error (HTTP 416).
     * This happens when cached data doesn't match the actual stream size.
     */
    private fun isRangeNotSatisfiableError(error: PlaybackException): Boolean {
        val responseCode = getHttpResponseCode(error)
        return responseCode == 416
    }

    /**
     * Checks if the error is a "page needs to be reloaded" error.
     * This is a YouTube-specific error that requires refreshing the stream.
     */
    private fun isPageReloadError(error: PlaybackException): Boolean {
        val errorMessage = error.message?.lowercase() ?: ""
        val causeMessage = error.cause?.message?.lowercase() ?: ""
        val innerCauseMessage =
            error.cause
                ?.cause
                ?.message
                ?.lowercase() ?: ""

        val reloadKeywords =
            listOf(
                "page needs to be reloaded",
                "pagina deve essere ricaricata",
                "la pagina deve essere ricaricata",
                "page must be reloaded",
                "reload",
                "ricaricata",
            )

        return reloadKeywords.any { keyword ->
            errorMessage.contains(keyword) ||
                causeMessage.contains(keyword) ||
                innerCauseMessage.contains(keyword)
        }
    }

    private fun isNetworkRelatedError(error: PlaybackException): Boolean {
        // Don't treat specific errors as network errors - they need special handling
        if (isExpiredUrlError(error) || isRangeNotSatisfiableError(error) || isPageReloadError(error)) {
            return false
        }
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
            error.cause is java.net.ConnectException ||
            error.cause is java.net.UnknownHostException ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
    }

    /**
     * Checks if the error is caused by AudioTrack write or initialization failures.
     * These errors indicate the audio renderer is in a corrupted/invalid state.
     */
    private fun isAudioRendererError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED

    /**
     * Checks if the error is an IO_FILE_NOT_FOUND (ENOENT).
     *
     * In practice this surfaces when the player cache reports a chunk as cached
     * but the backing file has been evicted/removed (e.g. LRU eviction racing
     * with a buffer read, an external cache wipe, or partial corruption).
     * CacheDataSource then falls back to the upstream DefaultDataSource with a
     * URI that is just the bare mediaId (no scheme), which is interpreted as a
     * local file path and fails to open.
     */
    private fun isFileNotFoundError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND

    private fun isRemotePlaybackError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_REMOTE_ERROR

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        if (!playerInitialized.value) {
            Timber.tag(TAG).e(error, "Player error occurred but player not initialized")
            return
        }

        val mediaId = player.currentMediaItem?.mediaId
        val optionalRendition = activeRenditionDescriptor?.takeIf { it.mediaId == mediaId && it.provider == AudioRendition.DIRECT_FLAC }
        if (mediaId != null && (optionalRendition != null || error.isLosslessStreamFailure())) {
            if (losslessFallbackInProgress) return
            losslessResolver.failed(mediaId, optionalRendition)
            losslessFallbackInProgress = true
            if (returnToNormalPlayer(mediaId)) return
            val index = player.currentMediaItemIndex
            val position = player.currentPosition
            val play = player.playWhenReady
            retryJob?.cancel()
            initialBufferRecoveryJob?.cancel()
            cancelCrossfadeForReload()
            activeRenditionKey = null
            activeRenditionDescriptor = null
            currentRendition.value = null
            sourceOwners[player]?.let { sourceSelections[it]?.remove(mediaId) }
            player.stop()
            player.seekTo(index, position)
            player.prepare()
            player.playWhenReady = play
            return
        }
        losslessFallbackInProgress = false
        val failedStreamClient = mediaId?.let(songUrlCache::clientName)
        Timber
            .tag(TAG)
            .w(error, "Player error occurred for $mediaId: errorCode=${error.errorCode}, message=${error.message}")
        reportException(error)

        if (mediaId != null && hasExceededRetryLimit(mediaId)) {
            Timber.tag(TAG).w("Song $mediaId has exceeded retry limit, skipping")
            markSongAsFailed(mediaId)
            handleFinalFailure()
            return
        }

        if (mediaId != null) {
            performAggressiveCacheClear(mediaId)
        }

        when {
            isAudioRendererError(error) -> {
                Timber.tag(TAG).d("AudioTrack error detected (${error.errorCode}), performing safe recovery")
                handleAudioRendererError(mediaId)
                return
            }

            isRangeNotSatisfiableError(error) -> {
                Timber.tag(TAG).d("Range Not Satisfiable (416) detected, performing strict recovery")
                handleRangeNotSatisfiableError(mediaId)
                return
            }

            isPageReloadError(error) -> {
                Timber.tag(TAG).d("Page reload error detected, performing strict recovery")
                handlePageReloadError(mediaId)
                return
            }

            isExpiredUrlError(error) -> {
                Timber.tag(TAG).d("Expired URL (403/410) detected, refreshing stream URL")
                handleExpiredUrlError(mediaId, failedStreamClient)
                return
            }

            isFileNotFoundError(error) -> {
                Timber.tag(TAG).d("Cache file missing (ENOENT) detected, refreshing stream")
                handleFileNotFoundError(mediaId)
                return
            }

            isRemotePlaybackError(error) -> {
                Timber.tag(TAG).d("Remote playback error detected (${error.errorCode}), refreshing stream URL")
                handleExpiredUrlError(mediaId, failedStreamClient)
                return
            }

            !isNetworkConnected.value -> {
                Timber.tag(TAG).d("No internet connection, waiting for connection")
                waitOnNetworkError()
                return
            }

            isNetworkRelatedError(error) -> {
                Timber.tag(TAG).d("Network-related error detected while connected, attempting recovery")
                handleGenericIOError(mediaId)
                return
            }
        }

        // For IO_BAD_HTTP_STATUS, try recovery first
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            Timber.tag(TAG).d("IO error detected (${error.errorCode}), attempting recovery")
            handleGenericIOError(mediaId)
            return
        }

        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            Timber.tag(TAG).d("Auto-skipping to next track due to unrecoverable error")
            skipOnError()
        } else {
            Timber.tag(TAG).d("Stopping playback due to unrecoverable error")
            stopOnError()
        }
    }

    /**
     * Performs aggressive cache clearing for a media item.
     * Clears playback-cache copies and invalidates URL resolution for recovery.
     */
    private fun performAggressiveCacheClear(mediaId: String) {
        Timber.tag(TAG).d("Performing aggressive cache clear for $mediaId")

        songUrlCache.invalidate(mediaId)
        freshAudioSelections[mediaId] = ++nextFreshAudioSelectionToken
        if (cacheClearTasks[mediaId]?.isActive == true) return
        val completion = kotlinx.coroutines.CompletableDeferred<Unit>()
        cacheClearTasks[mediaId] = completion
        scope.launch(Dispatchers.IO) {
            try {
                renditionStore.removePlayerCopies(mediaId)
                YTPlayerUtils.forceRefreshForVideo(mediaId)
                completion.complete(Unit)
                cacheClearTasks.remove(mediaId, completion)
            } catch (error: Exception) {
                completion.completeExceptionally(error)
                if (error is CancellationException) throw error
                Timber.tag(TAG).e(error, "Failed to clear playback cache for $mediaId")
            }
        }
    }

    /**
     * Checks if a song has exceeded the retry limit.
     */
    private fun hasExceededRetryLimit(mediaId: String): Boolean {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        return currentRetries >= MAX_RETRY_PER_SONG
    }

    /**
     * Increments the retry count for a song.
     */
    private fun incrementRetryCount(mediaId: String) {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        currentMediaIdRetryCount[mediaId] = currentRetries + 1
        Timber.tag(TAG).d("Retry count for $mediaId: ${currentRetries + 1}/$MAX_RETRY_PER_SONG")
    }

    /**
     * Resets the retry count for a song (called on successful playback).
     */
    private fun resetRetryCount(mediaId: String) {
        currentMediaIdRetryCount.remove(mediaId)
    }

    /**
     * Marks a song as failed to prevent further retry attempts.
     */
    private fun markSongAsFailed(mediaId: String) {
        currentMediaIdRetryCount.remove(mediaId)
    }

    /**
     * Handles AudioTrack errors (write failed, init failed) with safe recovery.
     * These errors indicate the audio renderer is corrupted and needs careful reset.
     */
    private fun handleAudioRendererError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        val wasPlaying = player.playWhenReady

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob =
            scope.launch {
                try {
                    player.pause()
                    Timber.tag(TAG).d("Paused playback due to AudioTrack error")

                    // Wait longer for audio renderer to settle before retry
                    // This prevents the renderer from continuing to fail in a loop
                    delay(RETRY_DELAY_MS * 3) // 3 seconds instead of 1 second

                    if (!playerInitialized.value) {
                        Timber.tag(TAG).w("Player no longer initialized, aborting AudioTrack recovery")
                        return@launch
                    }

                    val currentIndex = player.currentMediaItemIndex
                    if (currentIndex != C.INDEX_UNSET) {
                        // Seek to current position to force a clean audio renderer reinit
                        val currentPosition = player.currentPosition
                        player.seekTo(currentIndex, currentPosition)
                        player.prepare()

                        Timber.tag(TAG).d("Retrying playback for $mediaId after AudioTrack error")

                        if (wasPlaying) {
                            delay(500) // Brief delay to allow renderer to be ready
                            if (hasAudioFocus && playerInitialized.value) {
                                if (castConnectionHandler?.isCasting?.value != true) {
                                    player.play()
                                }
                            }
                        }
                    } else {
                        Timber.tag(TAG).w("Invalid media item index during AudioTrack recovery")
                        handleFinalFailure()
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error during AudioTrack error recovery")
                    handleFinalFailure()
                }
            }
    }

    /**
     * Handles Range Not Satisfiable (416) errors with strict recovery.
     * This error occurs when cached data doesn't match the actual stream size.
     */
    private fun handleRangeNotSatisfiableError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob =
            scope.launch {
                performAggressiveCacheClear(mediaId)

                delay(RETRY_DELAY_MS)

                // Force re-prepare from position 0 to avoid range issues
                val currentIndex = player.currentMediaItemIndex
                player.seekTo(currentIndex, 0)
                player.prepare()

                Timber.tag(TAG).d("Retrying playback for $mediaId after 416 error (from position 0)")
            }
    }

    /**
     * Handles "page needs to be reloaded" errors with strict recovery.
     * This requires clearing decryption caches and getting fresh stream URLs.
     */
    private fun handlePageReloadError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob =
            scope.launch {
                Timber.tag(TAG).d("Handling page reload error for $mediaId")

                performAggressiveCacheClear(mediaId)

                // Additional delay for page reload errors as they may be rate-limited
                delay(RETRY_DELAY_MS * 2)

                val currentPosition = player.currentPosition
                val currentIndex = player.currentMediaItemIndex
                player.seekTo(currentIndex, currentPosition)
                player.prepare()

                Timber.tag(TAG).d("Retrying playback for $mediaId after page reload error")
            }
    }

    /**
     * Handles expired URL (403/410) errors by clearing caches and retrying.
     */
    private fun handleExpiredUrlError(
        mediaId: String?,
        failedStreamClient: String?,
    ) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        refreshStreamAndRetry(
            mediaId = mediaId,
            failedStreamClient = failedStreamClient,
            refreshCipherConfig = true,
            retryReason = "expired URL error",
        )
    }

    private fun refreshStreamAndRetry(
        mediaId: String,
        failedStreamClient: String?,
        refreshCipherConfig: Boolean,
        retryReason: String,
    ) {
        if (hasExceededRetryLimit(mediaId)) {
            Timber.tag(TAG).w("Song $mediaId reached the retry limit during $retryReason")
            markSongAsFailed(mediaId)
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        songUrlCache.invalidate(mediaId)
        if (failedStreamClient == "WEB_REMIX") {
            YTPlayerUtils.markWebRemixFailed(mediaId)
        }
        Timber.tag(TAG).d("Cleared cached URL for $mediaId after $retryReason (client=$failedStreamClient)")

        try {
            YTPlayerUtils.forceRefreshForVideo(mediaId)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear decryption caches")
        }

        if (refreshCipherConfig) {
            // A rejection can mean the cipher produced a wrong-but-non-throwing signature. If a
            // rate-limited refresh corrects the table, allow WEB_REMIX again on the next resolution.
            scope.launch {
                if (CipherDeobfuscator.onStreamRejected()) {
                    Timber.tag(TAG).d("Player config changed after stream rejection — restoring WEB_REMIX")
                    YTPlayerUtils.clearWebRemixFailures()
                }
            }
        }

        val retryPosition = player.currentPosition
        val retryIndex = player.currentMediaItemIndex
        val retryPlayWhenReady = player.playWhenReady
        retryJob?.cancel()
        retryJob =
            scope.launch {
                delay(RETRY_DELAY_MS)

                if (player.currentMediaItem?.mediaId != mediaId ||
                    player.currentMediaItemIndex != retryIndex ||
                    player.currentPosition != retryPosition ||
                    player.playWhenReady != retryPlayWhenReady
                ) {
                    Timber.tag(TAG).d("Skipping stale retry for $mediaId after $retryReason")
                    return@launch
                }

                retryJob = null
                player.seekTo(retryIndex, retryPosition)
                player.prepare()

                Timber.tag(TAG).d("Retrying playback for $mediaId after $retryReason")
            }
    }

    /**
     * Handles IO_FILE_NOT_FOUND (ENOENT) by purging any cached state for the
     * media item and forcing the resolver to fetch a fresh stream URL.
     *
     * The aggressive cache clear at the top of [onPlayerError] already drops
     * the player cache entry and the cached stream URL, so re-preparing the
     * player here causes the resolver to take the "fetch fresh stream" path
     * instead of attempting another cache read for a file that no longer
     * exists on disk.
     */
    private fun handleFileNotFoundError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob =
            scope.launch {
                val currentPosition = player.currentPosition
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex == C.INDEX_UNSET) {
                    Timber.tag(TAG).w("Invalid media item index during file-not-found recovery")
                    handleFinalFailure()
                    return@launch
                }
                player.seekTo(currentIndex, currentPosition)
                player.prepare()

                Timber.tag(TAG).d("Retrying playback for $mediaId after IO_FILE_NOT_FOUND")
            }
    }

    /**
     * Handles generic IO errors with recovery attempt.
     */
    private fun handleGenericIOError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)

        retryJob?.cancel()
        retryJob =
            scope.launch {
                performAggressiveCacheClear(mediaId)
                delay(RETRY_DELAY_MS)

                val currentPosition = player.currentPosition
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    player.seekTo(currentIndex, currentPosition)
                }
                player.prepare()

                Timber.tag(TAG).d("Retrying playback for $mediaId after generic IO error")
            }
    }

    /**
     * Handles final failure when all recovery attempts have been exhausted.
     */
    private fun handleFinalFailure() {
        if (isCrossfading) {
            crossfadeJob?.cancel()
            cleanupCrossfade()
        }
        val autoSkipOnError = dataStore.get(AutoSkipNextOnErrorKey, false)
        val autoplay = dataStore.get(AutoplayKey, true)
        val canAdvance = player.hasNextMediaItem()

        if (autoSkipOnError || (autoplay && canAdvance)) {
            Timber.tag(TAG).d("All recovery attempts exhausted, auto-skipping to next track")
            skipOnError()
        } else {
            Timber.tag(TAG).d("All recovery attempts exhausted, stopping playback")
            stopOnError()
        }
    }

    override fun onDeviceVolumeChanged(
        volume: Int,
        muted: Boolean,
    ) {
        super.onDeviceVolumeChanged(volume, muted)
        val pauseOnMute = dataStore.get(PauseOnMute, false)

        if ((volume == 0 || muted) && pauseOnMute) {
            if (player.isPlaying) {
                wasPlayingBeforeVolumeMute = true
                isPausedByVolumeMute = true
                player.pause()
            }
        } else if (volume > 0 && !muted && pauseOnMute) {
            if (wasPlayingBeforeVolumeMute && !player.isPlaying && castConnectionHandler?.isCasting?.value != true) {
                wasPlayingBeforeVolumeMute = false
                isPausedByVolumeMute = false
                player.play()
            }
        }
    }

    private var isSilenceSkipping = false

    private fun handleLongSilenceDetected(owner: Any) {
        if (!instantSilenceSkipEnabled.value) return
        if (silenceSkipJob?.isActive == true) return

        silenceSkipJob =
            scope.launch {
                // Debounce so short fades or transitions do not trigger a jump.
                delay(200)
                if (activeSourceOwner === owner) performInstantSilenceSkip(owner)
            }
    }

    private suspend fun performInstantSilenceSkip(owner: Any) {
        val mediaId = player.currentMediaItem?.mediaId
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        if (duration <= INSTANT_SILENCE_SKIP_STEP_MS) return

        isSilenceSkipping = true
        try {
            var hops = 0
            val silenceProcessor = playerSilenceProcessors[player] ?: return
            while (coroutineContext.isActive && activeSourceOwner === owner && player.currentMediaItem?.mediaId == mediaId &&
                instantSilenceSkipEnabled.value && silenceProcessor.isCurrentlySilent()) {
                val current = player.currentPosition
                val target = (current + INSTANT_SILENCE_SKIP_STEP_MS).coerceAtMost(duration - 500)

                if (target <= current) break

                // Reset silence tracking before seeking to prevent immediate re-trigger
                silenceProcessor.resetTracking()
                player.seekTo(target)
                hops++

                if (hops >= 80 || target >= duration - 500) break

                delay(INSTANT_SILENCE_SKIP_SETTLE_MS)
            }
            if (hops > 0) {
                Timber.tag(TAG).d("Silence skip: jumped $hops times")
            }
        } finally {
            if (activeSourceOwner === owner && player.currentMediaItem?.mediaId == mediaId) isSilenceSkipping = false
        }
    }

    private fun syncDiscordState() {
        if (!discordRpcEnabled) return

        val isCasting = castConnectionHandler?.isCasting?.value == true
        val songId: String?
        val isPlaying: Boolean
        if (isCasting) {
            songId = castConnectionHandler?.castMediaId
            isPlaying = castConnectionHandler?.castIsPlaying?.value == true
        } else {
            songId = player.currentMetadata?.id
            isPlaying = player.isPlaying
        }
        if (songId == null) {
            Timber.tag("DiscordSvc").d("syncDiscordState: no song, clearing presence")
            DiscordRpcManager.clear()
            return
        }

            if (!DiscordRpcManager.isReady()) {
            if (discordIntentionalDisconnect) {
                Timber.tag("DiscordSvc").d("syncDiscordState: not ready, skipping (intentional disconnect)")
                return
            }
            val token = DiscordRpcManager.getAccessToken()
            val now = System.currentTimeMillis()
            if (token != null && (now - lastDiscordReconnectAttemptAtMs) > 30_000L) {
                lastDiscordReconnectAttemptAtMs = now
                Timber.tag("DiscordSvc").i("syncDiscordState: not ready, attempting reconnect")
                scope.launch(Dispatchers.IO) {
                    if (!DiscordRpcManager.isInitialized()) {
                        DiscordRpcManager.init(this@MusicService)
                    }
                    DiscordRpcManager.reconnectWithToken(token)
                }
            }
            return
        }

        if (!isPlaying) {
            // Paused: clear the presence (Spotify-style). Keeping a stale activity
            // makes Discord show a phantom counting timer instead of real state.
            // Resume re-sends via EVENT_IS_PLAYING_CHANGED.
            Timber.tag("DiscordSvc").d("syncDiscordState: paused, clearing presence")
            DiscordRpcManager.clear()
            return
        }
        if (DiscordRpcManager.isShowingSong(songId, isPlaying)) {
            Timber.tag("DiscordSvc").d("syncDiscordState: dedup, already showing songId=%s isPlaying=%s", songId, isPlaying)
            return
        }

        scope.launch(Dispatchers.IO) {
            val isCasting = castConnectionHandler?.isCasting?.value == true
            val (freshSongId, freshIsPlaying) = withContext(Dispatchers.Main.immediate) {
                if (isCasting) {
                    castConnectionHandler?.castMediaId to (castConnectionHandler?.castIsPlaying?.value == true)
                } else {
                    player.currentMetadata?.id to player.isPlaying
                }
            }
            if (freshSongId == null) return@launch
            val song = database.song(freshSongId).first() ?: return@launch
            updateDiscordRPC(song, freshIsPlaying)
        }
    }

    fun onCastPlaybackStateChanged() {
        syncDiscordState()
    }

    private suspend fun updateDiscordRPC(song: Song, isPlaying: Boolean) {
        if (!DiscordRpcManager.isReady()) {
            Timber.tag("DiscordSvc").w("updateDiscordRPC: skipping — not ready")
            return
        }
        if (!discordRpcEnabled) {
            Timber.tag("DiscordSvc").w("updateDiscordRPC: skipping — RPC disabled")
            return
        }

        Timber.tag("DiscordSvc").i("updateDiscordRPC: song=%s, isPlaying=%s", song.song.title, isPlaying)

        val isCasting = castConnectionHandler?.isCasting?.value == true
        // ExoPlayer must be accessed on the main thread
        val (currentPosition, speed) = withContext(Dispatchers.Main.immediate) {
            if (isCasting) {
                (castConnectionHandler?.castPosition?.value ?: 0L) to 1.0f
            } else {
                player.currentPosition to player.playbackParameters.speed
            }
        }
        val safeSpeed = speed.coerceAtLeast(0.01f)
        val safePosition = currentPosition.coerceAtLeast(0L)
        val adjustedTime = (safePosition / safeSpeed).toLong()
        val now = System.currentTimeMillis()
        val startTime = if (isPlaying) now - adjustedTime else 0L
        val durationMs = song.song.duration.takeIf { it > 0 }?.times(1000L)
        val remainingMs = durationMs?.minus(safePosition)?.coerceAtLeast(0L)
        val adjustedRemainingMs = remainingMs?.let { (it / safeSpeed).toLong() }
        val endTime = if (isPlaying && adjustedRemainingMs != null) now + adjustedRemainingMs else null

        val artistName = song.artists.joinToString { it.name }.ifEmpty { DiscordDefaults.UNKNOWN_ARTIST }
        val albumName = song.discordAlbumName()
        val songTitle = if (speed != 1.0f) {
            "${song.song.title} [${String.format("%.2fx", speed)}]"
        } else {
            song.song.title
        }
        val artistThumbnail = song.artists.firstOrNull()?.thumbnailUrl

        val advancedMode = dataStore.get(DiscordAdvancedModeKey, false)
        val activityType = dataStore.get(DiscordActivityTypeKey, DiscordDefaults.ACTIVITY_TYPE).toIntOrNull() ?: DiscordActivity.TYPE_LISTENING
        val activityName = dataStore.get(DiscordActivityNameKey, DiscordDefaults.ACTIVITY_NAME)
        val stateTemplate = dataStore.get(DiscordStateTemplateKey, DiscordDefaults.STATE_TEMPLATE)
        val detailsTemplate = dataStore.get(DiscordDetailsTemplateKey, DiscordDefaults.DETAILS_TEMPLATE)
        val btn1Enabled = dataStore.get(DiscordButton1EnabledKey, true)
        val btn1Label = dataStore.get(DiscordButton1LabelKey, DiscordDefaults.BUTTON1_LABEL)
        val btn1Url = dataStore.get(DiscordButton1UrlKey, DiscordDefaults.BUTTON1_URL_TEMPLATE)
        val btn2Enabled = dataStore.get(DiscordButton2EnabledKey, true)
        val btn2Label = dataStore.get(DiscordButton2LabelKey, DiscordDefaults.BUTTON2_LABEL)
        val btn2Url = dataStore.get(DiscordButton2UrlKey, DiscordDefaults.BUTTON2_URL)

        Timber.tag("DiscordSvc").d(
            "updateDiscordRPC: prefs — advancedMode=%s, activityType=%d, activityName=%s, stateTemplate=%s, detailsTemplate=%s",
            advancedMode, activityType, activityName, stateTemplate, detailsTemplate,
        )

        val activity = DiscordActivityBuilder.build(
            song = song,
            artistName = artistName,
            albumName = albumName,
            artistThumbnail = artistThumbnail,
            songTitle = songTitle,
            startTimestamp = startTime,
            endTimestamp = endTime,
            advancedMode = advancedMode,
            activityType = activityType,
            activityName = activityName,
            stateTemplate = stateTemplate,
            detailsTemplate = detailsTemplate,
            btn1Enabled = btn1Enabled,
            btn1Label = btn1Label,
            btn1Url = btn1Url,
            btn2Enabled = btn2Enabled,
            btn2Label = btn2Label,
            btn2Url = btn2Url,
        )

        Timber.tag("DiscordSvc").i("updateDiscordRPC: type=%d name=%s state=%s details=%s start=%d end=%d isPlaying=%s",
            activity.activityType, activity.name, activity.state, activity.details, startTime, endTime ?: 0L, isPlaying)

        val statusStr = dataStore.get(DiscordUserStatusKey, DiscordDefaults.USER_STATUS)
        val presenceStatus = when (statusStr) {
            DiscordDefaults.STATUS_IDLE -> if (advancedMode) PresenceStatus.Idle else PresenceStatus.Online
            DiscordDefaults.STATUS_DND -> if (advancedMode) PresenceStatus.Dnd else PresenceStatus.Online
            else -> PresenceStatus.Online
        }

        DiscordRpcManager.setActivity(
            activity,
            songId = song.song.id,
            isPlaying = isPlaying,
            status = presenceStatus,
        )

        val fetched = fetchArtistThumbnail(song)
        if (fetched != null && DiscordRpcManager.isReady() && discordRpcEnabled) {
            Timber.tag("DiscordSvc").i("updateDiscordRPC: updating with fetched thumbnail")
            val fetchedArtistName = fetched.artists.joinToString { it.name }.ifEmpty { DiscordDefaults.UNKNOWN_ARTIST }
            val fetchedAlbumName = fetched.discordAlbumName()
            val fetchedArtistThumbnail = fetched.artists.firstOrNull()?.thumbnailUrl

            val (freshPosition, freshSpeed, freshIsPlaying) = withContext(Dispatchers.Main.immediate) {
                Triple(player.currentPosition, player.playbackParameters.speed, player.isPlaying)
            }
            val safeFreshSpeed = freshSpeed.coerceAtLeast(0.01f)
            val safeFreshPosition = freshPosition.coerceAtLeast(0L)
            val freshAdjustedTime = (safeFreshPosition / safeFreshSpeed).toLong()
            val freshNow = System.currentTimeMillis()
            val freshStartTime = if (freshIsPlaying) freshNow - freshAdjustedTime else 0L
            val freshDurationMs = fetched.song.duration.takeIf { it > 0 }?.times(1000L)
            val freshRemainingMs = freshDurationMs?.minus(safeFreshPosition)?.coerceAtLeast(0L)
            val freshAdjustedRemainingMs = freshRemainingMs?.let { (it / safeFreshSpeed).toLong() }
            val freshEndTime = if (freshIsPlaying && freshAdjustedRemainingMs != null) freshNow + freshAdjustedRemainingMs else null

            val fetchedActivity = DiscordActivityBuilder.build(
                song = fetched,
                artistName = fetchedArtistName,
                albumName = fetchedAlbumName,
                artistThumbnail = fetchedArtistThumbnail,
                songTitle = songTitle,
                startTimestamp = freshStartTime,
                endTimestamp = freshEndTime,
                advancedMode = advancedMode,
                activityType = activityType,
                activityName = activityName,
                stateTemplate = stateTemplate,
                detailsTemplate = detailsTemplate,
                btn1Enabled = btn1Enabled,
                btn1Label = btn1Label,
                btn1Url = btn1Url,
                btn2Enabled = btn2Enabled,
                btn2Label = btn2Label,
                btn2Url = btn2Url,
            )

            DiscordRpcManager.setActivity(
                fetchedActivity,
                songId = song.song.id,
                isPlaying = freshIsPlaying,
                status = presenceStatus,
            )
        } else {
            Timber.tag("DiscordSvc").i("updateDiscordRPC: fetched=%s (no thumbnail update)", fetched != null)
        }
    }

    private suspend fun fetchArtistThumbnail(song: Song): Song? {
        val artist = song.artists.firstOrNull()
        if (artist == null) {
            Timber.tag("DiscordSvc").d("fetchArtistThumbnail: no artist, skipping")
            return null
        }
        if (artist.thumbnailUrl != null) {
            Timber.tag("DiscordSvc").d("fetchArtistThumbnail: artist already has thumbnail, skipping")
            return null
        }

        val browseId = when {
            artist.channelId != null && !artist.channelId.startsWith("LA")
                && !artist.channelId.startsWith("FEmusic_library_privately_owned") -> {
                artist.channelId
            }
            !artist.id.startsWith("LA")
                && !artist.id.startsWith("FEmusic_library_privately_owned") -> {
                artist.id
            }
            else -> {
                Timber.tag("DiscordSvc").d("fetchArtistThumbnail: no valid browseId for artist %s", artist.name)
                return null
            }
        }

        Timber.tag("DiscordSvc").d("fetchArtistThumbnail: fetching for artist=%s, browseId=%s", artist.name, browseId)

        return try {
            val artistPage = withContext(Dispatchers.IO) {
                YouTube.artist(browseId).getOrNull()
            }
            val thumbnail = artistPage?.artist?.thumbnail?.resize(1080, 1080)
            if (thumbnail != null) {
                Timber.tag("DiscordSvc").d("fetchArtistThumbnail: got thumbnail for %s", artist.name)
                withContext(Dispatchers.IO) {
                    database.update(artist.copy(thumbnailUrl = thumbnail))
                }
                database.getSongById(song.song.id)
            } else {
                Timber.tag("DiscordSvc").d("fetchArtistThumbnail: no thumbnail found for %s", artist.name)
                null
            }
        } catch (e: Exception) {
            Timber.tag("DiscordSvc").w(e, "fetchArtistThumbnail: failed for artist=%s", artist.name)
            null
        }
    }

    private fun createDataSourceFactory(owner: Any, preloadController: NextItemPreloadController): DataSource.Factory {
        val factory = preloadController.wrap(renditionSources.playbackFactory(
        quality = { audioQuality },
        selectionVersion = { audioSelectionVersion },
        usePlayerCache = { cachedSongCacheEnabled },
        freshSelectionToken = { freshAudioSelections[it] },
        beforeSelection = { cacheClearTasks[it]?.await() },
        priority = { id -> renditionPreparationBudgets[owner]?.resolutionPriority ?: preloadController.priority(id) },
        preferLossless = { preferLossless && castConnectionHandler?.isCasting?.value != true },
        optionalLookup = { id -> qualityLookups[owner]?.takeIf { it.first == id }?.second?.result },
        forcedLossless = { id -> forcedRenditions[owner]?.takeIf { it.mediaId == id } },
        onSelected = { selected ->
            scope.launch {
                if (selected.selectionVersion != audioSelectionVersion || selected.quality != audioQuality) return@launch
                if (selected.contextRevision != YouTube.requestContextRevision.value) return@launch
                if (owner !in sourceOwners.values) return@launch
                if (selected.rendition.provider == AudioRendition.DIRECT_FLAC && losslessResolver.isBlocked(selected.rendition.mediaId)) return@launch
                val freshToken = freshAudioSelections[selected.rendition.mediaId]
                if (freshToken != null && freshToken != selected.freshSelectionToken) return@launch
                val records = sourceSelections.getOrPut(owner) { LinkedHashMap(0, 0.75f, true) }
                records[selected.rendition.mediaId] = selected
                while (records.size > 32) records.entries.iterator().apply { next(); remove() }
                if ((selected.stream != null && streamResolver.canReuse(selected.stream)) ||
                    selected.rendition.provider == AudioRendition.DIRECT_FLAC
                ) {
                    selected.freshSelectionToken?.let { freshAudioSelections.remove(selected.rendition.mediaId, it) }
                }
                publishActiveRendition(owner, selected)
            }
        },
        ))
        return renditionPreparationBudgets[owner]?.wrap(factory) ?: factory
    }

    private fun publishActiveRendition(owner: Any, selected: SelectedAudio) {
        val rendition = selected.rendition
        if (castConnectionHandler?.isCasting?.value == true) return
        if (rendition.provider == AudioRendition.DIRECT_FLAC && losslessResolver.isBlocked(rendition.mediaId)) return
        if (sourceOwners[player] !== owner || player.currentMediaItem?.mediaId != rendition.mediaId ||
            selected.selectionVersion != audioSelectionVersion || selected.quality != audioQuality ||
            selected.contextRevision != YouTube.requestContextRevision.value
        ) return
        val key = rendition.cacheKey
        currentRendition.value = rendition
        if (activeRenditionDescriptor != rendition) {
            activeRenditionDescriptor = rendition
            persistedRenditionKey = null
            formatPublishJob?.cancel()
        }
        currentStreamClient.value = selected.stream?.playback?.streamClient ?: rendition.clientName
        selected.stream?.playback?.playbackTracking?.videostatsPlaybackUrl?.baseUrl?.let {
            playbackUrlCache[cacheKey(rendition.mediaId)] = it
        }
        if (activeRenditionKey != key) {
            activeRenditionKey = key
            persistedRenditionKey = null
            // Active playback only: speculative opens never trigger recommendations.
            recoverSongDeduped(rendition.mediaId, selected.stream?.playback)
        }
        if (persistedRenditionKey == key || (formatPublishKey == key && formatPublishJob?.isActive == true)) return
        formatPublishJob?.cancel()
        formatPublishKey = key
        formatPublishJob = scope.launch(Dispatchers.IO + SilentHandler) {
            // A cancelled synchronous Room write may finish. Serialise writers so a newer
            // active selection is always persisted after it, never overwritten by it later.
            formatPublicationMutex.withLock {
                val length = renditionStore.knownLength(rendition)
                val format = rendition.toFormatEntity(knownLength = length) ?: return@withLock
                val existing = database.formatOnce(rendition.mediaId)
                if (isActive && activeRenditionKey == key && activeRenditionDescriptor == rendition && selected.selectionVersion == audioSelectionVersion) {
                    if (existing != format) database.upsert(format)
                    if (isActive && activeRenditionKey == key && activeRenditionDescriptor == rendition && length != null) persistedRenditionKey = key
                }
            }
        }
    }

    private fun createMediaSourceFactory(owner: Any, preloadController: NextItemPreloadController): MediaSource.Factory {
        val delegate = DefaultMediaSourceFactory(
            createDataSourceFactory(owner, preloadController),
            ExtractorsFactory {
                arrayOf(MatroskaExtractor(), FragmentedMp4Extractor(), Mp4Extractor(), FlacExtractor())
            },
        ).setLoadErrorHandlingPolicy(object : DefaultLoadErrorHandlingPolicy() {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long =
                if (loadErrorInfo.exception.isLosslessStreamFailure()) C.TIME_UNSET else super.getRetryDelayMsFor(loadErrorInfo)
        })
        return object : MediaSource.Factory by delegate {
            override fun createMediaSource(mediaItem: MediaItem): MediaSource {
                mediaItem.metadata?.let(losslessResolver::remember)
                return delegate.createMediaSource(mediaItem)
            }
        }
    }

    internal fun isOptionalPlaybackFailure(error: PlaybackException): Boolean =
        error.isLosslessStreamFailure() || currentRendition.value?.provider == AudioRendition.DIRECT_FLAC

    private fun startRenditionLookup(mediaId: String, existingLookup: LosslessLookup? = null) {
        if (!preferLossless || preloadRoomActive || castConnectionHandler?.isCasting?.value == true) return
        val owner = sourceOwners[player] ?: return
        val attempt = renditionAttempt
        val version = audioSelectionVersion
        val revision = YouTube.requestContextRevision.value
        val lookup = existingLookup ?: losslessResolver.request(mediaId)
        qualityLookups[owner] = mediaId to lookup
        renditionLookupJob = scope.launch {
            val resource = try { lookup.result.await() } catch (cancelled: CancellationException) { return@launch }
                catch (_: Exception) { return@launch } ?: return@launch
            if (attempt != renditionAttempt || version != audioSelectionVersion || revision != YouTube.requestContextRevision.value ||
                !preferLossless || sourceOwners[player] !== owner || player.currentMediaItem?.mediaId != mediaId
            ) return@launch
            renditionChangeJob = scope.launch { prepareRenditionHandoff(resource, attempt, version, revision) }
        }
    }

    /** The current decoder/output stays in charge throughout discovery, connection, extraction,
     * buffering and alignment. A failed muted preparation has no effect on its playback. */
    private suspend fun prepareRenditionHandoff(resource: FlacResource, attempt: Long, version: Long, revision: Long) {
        var candidate: ExoPlayer? = null
        var budget: PreloadReadBudget? = null
        val primary = player
        fun current() = isRunning && renditionAttempt == attempt && audioSelectionVersion == version &&
            YouTube.requestContextRevision.value == revision && preferLossless && player === primary &&
            primary.currentMediaItem?.mediaId == resource.mediaId && !preloadRoomActive && !preloadMemoryConstrained &&
            castConnectionHandler?.isCasting?.value != true
        try {
            if (preloadMemoryConstrained || startupPrefs?.get(AudioOffload) == true) return
            // Do not take a third player from an in-progress user crossfade.
            while (current() && isCrossfading) delay(20)
            if (!current() || currentRendition.value?.provider == AudioRendition.DIRECT_FLAC || secondaryPlayer != null) return
            withTimeoutOrNull(QUALITY_PREPARE_TIMEOUT_MS) {
                while (current() && (!primary.isPlaying || primary.bufferedPosition - primary.currentPosition < 2_000L)) delay(20)
                if (!current()) return@withTimeoutOrNull
                budget = PreloadReadBudget(NextItemPreloadController.MAX_BYTES)
                val prepared = createExoPlayer(forcedResource = resource, preparationBudget = budget)
                candidate = prepared
                qualityCandidate = prepared
                secondaryPlayer = prepared
                prepared.volume = 0f
                prepared.playbackParameters = primary.playbackParameters
                prepared.skipSilenceEnabled = primary.skipSilenceEnabled
                prepared.repeatMode = primary.repeatMode
                prepared.shuffleModeEnabled = primary.shuffleModeEnabled
                val index = primary.currentMediaItemIndex
                prepared.setMediaItems(primary.mediaItems, index, primary.currentPosition)
                copyPlaybackOrder(primary, prepared)
                val audioClockStarted = CompletableDeferred<Unit>()
                val audioListener = object : AnalyticsListener {
                    override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
                        audioClockStarted.complete(Unit)
                    }
                }
                prepared.addAnalyticsListener(audioListener)
                try {
                    prepared.prepare()
                    awaitPlayerCondition(prepared) { prepared.playbackState == Player.STATE_READY }
                    if (!current() || primary.duration == C.TIME_UNSET || prepared.duration == C.TIME_UNSET ||
                        kotlin.math.abs(primary.duration - prepared.duration) > QUALITY_DURATION_TOLERANCE_MS
                    ) return@withTimeoutOrNull
                    // Native STREAMINFO is not enough: the muted player has now extracted and
                    // decoded its initial media. Promotion reuses these bytes instead of refetching.
                    budget.promote()
                    prepared.playWhenReady = true
                    audioClockStarted.await()
                    awaitPlayerCondition(prepared) { prepared.isPlaying }
                    if (!synchronizePlayerQueue(primary, prepared)) return@withTimeoutOrNull
                    awaitPlayerCondition(prepared) { prepared.isPlaying }
                    var stableSamples = 0
                    while (current() && stableSamples < 3) {
                        if (prepared.playerError != null || !primary.isPlaying) return@withTimeoutOrNull
                        val drift = primary.currentPosition - prepared.currentPosition
                        if (kotlin.math.abs(drift) <= QUALITY_ALIGNMENT_TOLERANCE_MS && prepared.isPlaying) {
                            prepared.playbackParameters = primary.playbackParameters
                            stableSamples++
                        } else {
                            stableSamples = 0
                            // This correction is inaudible. Never seek the playing primary or
                            // change its speed just to catch up a secondary decoder.
                            val correction = (drift / 1_000f).coerceIn(-0.15f, 0.15f)
                            prepared.playbackParameters = primary.playbackParameters.withSpeed(
                                (primary.playbackParameters.speed * (1f + correction)).coerceIn(0.1f, 8f),
                            )
                        }
                        delay(20)
                    }
                    if (!current() || stableSamples < 3) return@withTimeoutOrNull
                    qualityFading = true
                    val steps = 8
                    for (step in 0..steps) {
                        if (!current() || !primary.isPlaying || !prepared.isPlaying ||
                            kotlin.math.abs(primary.currentPosition - prepared.currentPosition) > QUALITY_ALIGNMENT_TOLERANCE_MS * 3
                        ) return@withTimeoutOrNull
                        val progress = step / steps.toFloat()
                        val volume = calculateEffectiveVolume()
                        playerAudibility[prepared]?.update(silent = step == 0)
                        prepared.volume = volume * progress
                        primary.volume = volume * (1f - progress)
                        if (step < steps) delay(QUALITY_TRANSITION_MS / steps)
                    }
                    if (primary.mediaItems != prepared.mediaItems) return@withTimeoutOrNull
                    // Only a prepared, advancing and aligned output takes ownership. No media-
                    // item transition is synthesized for a quality change of the same recording.
                    changeRenditionPlayer(prepared)
                    normalFallbackPlayer = primary
                    playerAudibility[primary]?.update(silent = true)
                    primary.volume = 0f
                    qualityCandidate = null
                    secondaryPlayer = null
                    startNormalFallbackWatch(primary, resource.mediaId)
                } finally {
                    if (prepared in sourceOwners) prepared.removeAnalyticsListener(audioListener)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Optional preparation/decoder/route failure is silent; primary remains usable.
        } finally {
            qualityFading = false
            if (candidate !== player) {
                budget?.cancel()
                candidate?.let { pending ->
                    if (qualityCandidate === pending) qualityCandidate = null
                    if (secondaryPlayer === pending) secondaryPlayer = null
                    releaseExoPlayer(pending)
                }
            }
            if (::player.isInitialized && isRunning) applyEffectiveVolume()
            if (isRunning) updateNativePreloading()
        }
    }

    private suspend fun awaitPlayerCondition(candidate: ExoPlayer, ready: () -> Boolean) {
        val completion = CompletableDeferred<Unit>()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { if (ready()) completion.complete(Unit) }
            override fun onPlayerError(error: PlaybackException) { completion.completeExceptionally(error) }
        }
        candidate.addListener(listener)
        try {
            candidate.playerError?.let { throw it }
            if (ready()) return
            completion.await()
        } finally {
            if (candidate in sourceOwners) candidate.removeListener(listener)
        }
    }

    private fun changeRenditionPlayer(next: ExoPlayer) {
        val previous = player
        mediaSession?.let { (it as MediaSession).player = next }
        silenceSkipJob?.cancel()
        silenceSkipJob = null
        isSilenceSkipping = false
        previous.removeListener(this)
        sleepTimer?.let(previous::removeListener)
        player = next
        activeSourceOwner = sourceOwners[next]
        next.addListener(this)
        sleepTimer?.let { next.addListener(it); it.player = next }
        _playerFlow.value = next
        currentMediaMetadata.value = next.currentMetadata
        sourceOwners[next]?.let { owner ->
            next.currentMediaItem?.mediaId?.let { id -> sourceSelections[owner]?.get(id)?.let { publishActiveRendition(owner, it) } }
        }
        setupAudioNormalization()
        // Once output ownership has moved, optional session-effect failure does not orphan
        // the previous decoder or undo a successful audio handoff.
        runCatching { openAudioEffectSession(); scheduleCrossfade() }
    }

    private fun startNormalFallbackWatch(normal: ExoPlayer, mediaId: String) {
        renditionFallbackJob?.cancel()
        renditionFallbackJob = scope.launch {
            while (isActive && normalFallbackPlayer === normal && player.currentMediaItem?.mediaId == mediaId) {
                if (normal.playerError != null) {
                    normalFallbackPlayer = null
                    releaseExoPlayer(normal)
                    return@launch
                }
                normal.playbackParameters = player.playbackParameters
                if (normal.currentMediaItemIndex != player.currentMediaItemIndex || normal.currentMediaItem?.mediaId != mediaId) {
                    normal.setMediaItems(player.mediaItems, player.currentMediaItemIndex, player.currentPosition)
                    normal.prepare()
                }
                normal.playWhenReady = player.isPlaying
                if (player.playbackState == Player.STATE_READY && normal.playbackState == Player.STATE_READY &&
                    kotlin.math.abs(normal.currentPosition - player.currentPosition) > QUALITY_ALIGNMENT_TOLERANCE_MS * 3
                ) normal.seekTo(normal.currentMediaItemIndex, player.currentPosition)
                delay(250)
            }
        }
    }

    private fun synchronizeNormalFallbackTimeline() {
        val normal = normalFallbackPlayer ?: return
        if (player.currentMediaItemIndex == C.INDEX_UNSET) return
        if (!synchronizePlayerQueue(player, normal)) {
            normal.setMediaItems(player.mediaItems, player.currentMediaItemIndex, player.currentPosition)
            normal.prepare()
        }
        normal.playWhenReady = player.isPlaying
    }

    /** Preserve the current period while completing/reordering the surrounding queue. This
     * handles a one-item preload whose full playlist arrives during muted preparation. */
    private fun synchronizePlayerQueue(from: ExoPlayer, to: ExoPlayer): Boolean {
        val current = from.currentMediaItem ?: return false
        if (to.currentMediaItem != current) return false
        val items = from.mediaItems
        if (to.mediaItems != items || to.currentMediaItemIndex != from.currentMediaItemIndex) {
            val index = to.currentMediaItemIndex
            if (index + 1 < to.mediaItemCount) to.removeMediaItems(index + 1, to.mediaItemCount)
            if (index > 0) to.removeMediaItems(0, index)
            val target = from.currentMediaItemIndex
            if (target > 0) to.addMediaItems(0, items.subList(0, target))
            if (target + 1 < items.size) to.addMediaItems(items.subList(target + 1, items.size))
        }
        copyPlaybackOrder(from, to)
        return true
    }

    private fun copyPlaybackOrder(from: ExoPlayer, to: ExoPlayer) {
        to.repeatMode = from.repeatMode
        to.shuffleModeEnabled = from.shuffleModeEnabled
        val timeline = from.currentTimeline
        if (!from.shuffleModeEnabled || timeline.windowCount != from.mediaItemCount || timeline.isEmpty) return
        val order = mutableListOf<Int>()
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET && order.size < timeline.windowCount) {
            order.add(index)
            index = timeline.getNextWindowIndex(index, REPEAT_MODE_OFF, true)
        }
        if (order.size == timeline.windowCount) to.setShuffleOrder(DefaultShuffleOrder(order.toIntArray(), SystemClock.elapsedRealtime()))
    }

    private fun returnToNormalPlayer(mediaId: String): Boolean {
        val normal = normalFallbackPlayer ?: return false
        if (normal.currentMediaItem?.mediaId != mediaId || normal.playbackState != Player.STATE_READY || normal.playerError != null ||
            kotlin.math.abs(normal.currentPosition - player.currentPosition) > QUALITY_ALIGNMENT_TOLERANCE_MS * 3
        ) return false
        val failed = player
        normal.playWhenReady = failed.playWhenReady
        renditionFallbackJob?.cancel()
        normalFallbackPlayer = null
        changeRenditionPlayer(normal)
        playerAudibility[normal]?.update(silent = false)
        normal.volume = calculateEffectiveVolume()
        releaseExoPlayer(failed)
        return true
    }

    private fun cancelRenditionChanges() {
        ++renditionAttempt
        renditionLookupJob?.cancel()
        renditionLookupJob = null
        renditionChangeJob?.cancel()
        renditionChangeJob = null
        renditionFallbackJob?.cancel()
        renditionFallbackJob = null
        qualityLookups.clear()
        // The cancelled preparation owns its candidate cleanup in finally, on Main. Avoid
        // releasing that object twice while its suspended listener unwinds.
        qualityCandidate?.let {
            it.volume = 0f
            if (secondaryPlayer === it) secondaryPlayer = null
        }
        qualityCandidate = null
        normalFallbackPlayer?.let(::releaseExoPlayer)
        normalFallbackPlayer = null
        qualityFading = false
        if (::player.isInitialized && ::playerVolume.isInitialized && isRunning) applyEffectiveVolume()
    }

    private fun updateNativePreloading() {
        if (!::player.isInitialized) return
        val activeId = player.currentMediaItem?.mediaId
        val nextIndex = player.nextMediaItemIndex
        val warmEligible = isRunning && preferLossless && player.isPlaying && !player.isLoading && preloadNetworkAllowed &&
            !preloadMemoryConstrained && !preloadRoomActive && castConnectionHandler?.isCasting?.value != true &&
            player.repeatMode != REPEAT_MODE_ONE && nextIndex != C.INDEX_UNSET
        val warmItem = if (warmEligible) player.getMediaItemAt(nextIndex).metadata else null
        if (warmItem != null && !warmItem.isEpisode && !warmItem.isVideoSong) {
            losslessResolver.remember(warmItem)
            val warmKey = "$audioSelectionVersion:${YouTube.requestContextRevision.value}:${warmItem.id}"
            if (losslessWarmTarget != warmKey) {
                losslessWarmTarget = warmKey
                losslessResolver.retainPlaybackLookups(setOfNotNull(activeId, warmItem.id))
                losslessResolver.request(warmItem.id, priority = StreamRequestPriority.BACKGROUND)
            }
        } else {
            losslessWarmTarget = null
            losslessResolver.retainPlaybackLookups(setOfNotNull(activeId))
        }
        val eligible = isRunning && player.isPlaying && player.playbackState == Player.STATE_READY &&
            preloadNetworkAllowed && cachedSongCacheEnabled && preloadCacheAvailable &&
            !preloadMemoryConstrained && !preloadRoomActive && !crossfadeEnabled && !isCrossfading &&
            castConnectionHandler?.isCasting?.value != true &&
            player.repeatMode != REPEAT_MODE_ONE && nextIndex != C.INDEX_UNSET
        val next = if (eligible) player.currentTimeline.getWindow(nextIndex, Timeline.Window()) else null
        val target = next?.takeIf {
            !it.isLive && it.mediaItem.mediaId != activeId && (it.mediaItem.metadata?.duration ?: 0) > 0
        }?.let {
            NextItemPreloadController.Target(
                it.uid, it.mediaItem.mediaId, audioSelectionVersion,
                YouTube.requestContextRevision.value, YTPlayerUtils.disabledStreamClients,
            )
        }
        for ((candidate, control) in preloadControllers) {
            val desired = target.takeIf { candidate === player }
            val changed = control.update(desired, candidate.currentMediaItem?.mediaId)
            if (changed && candidate.preloadConfiguration.targetPreloadDurationUs != C.TIME_UNSET) {
                candidate.setPreloadConfiguration(ExoPlayer.PreloadConfiguration.DEFAULT)
            }
            val durationUs = if (desired == null) C.TIME_UNSET else NextItemPreloadController.TARGET_DURATION_US
            if (candidate.preloadConfiguration.targetPreloadDurationUs != durationUs) {
                candidate.setPreloadConfiguration(ExoPlayer.PreloadConfiguration(durationUs))
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
            level == ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        ) {
            preloadMemoryConstrained = true
            updateNativePreloading()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        preloadMemoryConstrained = true
        updateNativePreloading()
    }

    private fun createRenderersFactory(
        normalizationProcessor: VolumeNormalizationAudioProcessor,
        eqProcessor: CustomEqualizerAudioProcessor,
        silenceProcessor: SilenceDetectorAudioProcessor,
        useAudioTrackPlaybackParams: Boolean,
    ) = object : DefaultRenderersFactory(this) {
        override fun buildAudioRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            audioSink: AudioSink,
            eventHandler: Handler,
            eventListener: AudioRendererEventListener,
            out: ArrayList<Renderer>,
        ) {
            super.buildAudioRenderers(
                context,
                extensionRendererMode,
                mediaCodecSelector,
                enableDecoderFallback,
                audioSink,
                eventHandler,
                eventListener,
                out,
            )
            val coreRendererIndex = out.indexOfFirst { it is MediaCodecAudioRenderer }
            if (coreRendererIndex == -1) return
            out[coreRendererIndex] =
                object : MediaCodecAudioRenderer(
                    context,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    eventHandler,
                    eventListener,
                    audioSink,
                ) {
                    override fun getCodecOperatingRateV23(
                        targetPlaybackSpeed: Float,
                        format: Format,
                        streamFormats: Array<Format>,
                    ): Float =
                        super.getCodecOperatingRateV23(
                            if (targetPlaybackSpeed in 0.95f..1.05f) 1f else targetPlaybackSpeed,
                            format,
                            streamFormats,
                        )
                }
        }

        override fun buildAudioSink(
            context: Context,
            enableFloatOutput: Boolean,
            enableAudioTrackPlaybackParams: Boolean,
        ) = DefaultAudioSink
            .Builder(this@MusicService)
            .setEnableFloatOutput(enableFloatOutput)
            .setEnableAudioTrackPlaybackParams(useAudioTrackPlaybackParams)
            .setAudioProcessorChain(
                DefaultAudioSink.DefaultAudioProcessorChain(
                    arrayOf(
                        normalizationProcessor,
                        eqProcessor,
                        silenceProcessor,
                    ),
                    SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                    SonicAudioProcessor(),
                ),
            ).build()
    }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        recordPlaybackStats(eventTime, playbackStats.totalPlayTimeMs)
    }

    private fun recordPlaybackStats(eventTime: AnalyticsListener.EventTime, playTimeMs: Long) {
        if (eventTime.windowIndex !in 0 until eventTime.timeline.windowCount) return
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val historyDurationMs = startupPrefs?.get(HistoryDuration)?.times(1000f) ?: 30000f

        if (playTimeMs > 0 && playTimeMs >= historyDurationMs &&
            startupPrefs?.get(PauseListenHistoryKey) != true
        ) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playTimeMs,
                        ),
                    )
                } catch (_: SQLException) {
                }
            }
        }

        if (playTimeMs >= historyDurationMs) {
            scope.launch(Dispatchers.IO) {
                val playbackUrl =
                    playbackUrlCache[cacheKey(mediaItem.mediaId)]
                        ?: YTPlayerUtils
                            .playerResponseForMetadata(mediaItem.mediaId, null)
                            .getOrNull()
                            ?.playbackTracking
                            ?.videostatsPlaybackUrl
                            ?.baseUrl
                if (playbackUrl == null) {
                    Timber.tag(TAG).w("No playback tracking URL available for ${mediaItem.mediaId}, skipping YouTube history registration")
                    return@launch
                }
                YouTube
                    .registerPlayback(null, playbackUrl)
                    .onFailure {
                        reportException(it)
                    }
            }
        }
    }

    private fun saveQueueToDisk() {
        if (!cachedPersistentQueue || player.mediaItemCount == 0) {
            Timber.tag(TAG).d("Skipping queue save - no media items")
            return
        }

        try {
            val persistQueue =
                currentQueue.toPersistQueue(
                    title = queueTitle,
                    items = player.mediaItems.mapNotNull { it.metadata },
                    mediaItemIndex = player.currentMediaItemIndex,
                    position = player.currentPosition,
                )

            val persistAutomix =
                PersistQueue(
                    title = "automix",
                    items = automixItems.value.mapNotNull { it.metadata },
                    mediaItemIndex = 0,
                    position = 0,
                )

            val persistPlayerState =
                PersistPlayerState(
                    playWhenReady = player.playWhenReady,
                    repeatMode = player.repeatMode,
                    shuffleModeEnabled = player.shuffleModeEnabled,
                    volume = playerVolume.value,
                    currentPosition = player.currentPosition,
                    currentMediaItemIndex = player.currentMediaItemIndex,
                    playbackState = player.playbackState,
                )

            queueStore.save(PlaybackQueueSnapshot(persistQueue, persistAutomix, persistPlayerState))
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during queue save operation")
            reportException(e)
        }
    }

    /**
     * [Context.startForegroundService] requires [startForeground] to succeed quickly. If we cannot
     * enter the foreground state, stop immediately so the system does not ANR the app process.
     */
    private fun ensureStartedAsForegroundOrStop(): Boolean =
        startForegroundSafely(
            notification = createFallbackForegroundNotification(),
            deniedMessage = "Foreground service start not allowed; stopping service to avoid ANR",
            failureMessage = "Failed to enter foreground; stopping service to avoid ANR",
        )

    private fun ensureForegroundWithLatestNotificationOrStop(): Boolean =
        startForegroundSafely(
            notification = latestMediaNotification ?: createFallbackForegroundNotification(),
            deniedMessage = "Foreground promotion denied during notification update; stopping service",
            failureMessage = "Failed to promote service during notification update; stopping service",
            stopOnFailure = true,
        )

    private fun tryEnsureForegroundWithLatestNotification(): Boolean =
        startForegroundSafely(
            notification = latestMediaNotification ?: createFallbackForegroundNotification(),
            deniedMessage = "Foreground promotion denied during notification update",
            failureMessage = "Failed to promote service during notification update",
            stopOnFailure = false,
        )

    private fun ensureForegroundChannelExists() {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.music_player),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    private fun createFallbackForegroundNotification(): Notification {
        ensureForegroundChannelExists()
        val pending =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
        return NotificationCompat
            .Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.music_player))
            .setContentText("")
            .setSmallIcon(R.drawable.small_icon)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    private fun startForegroundSafely(
        notification: Notification,
        deniedMessage: String,
        failureMessage: String,
        stopOnFailure: Boolean = true,
    ): Boolean =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Timber.tag(TAG).w(e, deniedMessage)
            if (stopOnFailure) {
                stopSelf()
            }
            false
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, failureMessage)
            reportException(e)
            if (stopOnFailure) {
                stopSelf()
            }
            false
        }

    override fun onDestroy() {
        cancelRenditionChanges()
        crossfadePreparationJob?.cancel()
        val shutdownSignal = shutdownDeferred
        if (runningInstance === this) runningInstance = null
        losslessResolver.cancelPending()
        currentRendition.value = null
        preloadControllers.values.forEach { it.release() }
        cacheClearTasks.values.forEach { it.cancel() }
        cacheClearTasks.clear()
        freshAudioSelections.clear()
        formatPublishJob?.cancel()
        activeRenditionKey = null
        activeRenditionDescriptor = null
        persistedRenditionKey = null

        if (!::player.isInitialized) {
            try {
                scope.cancel()
            } catch (_: Exception) {
            }
            super.onDestroy()
            shutdownDeferred.complete(Unit)
            return
        }

        val currentMetadata = player.currentMediaItem?.metadata
        val finalPosition = player.currentPosition
        val episodeWrite = if (currentMetadata?.isEpisode == true && finalPosition > 0) {
            applicationScope.launch(Dispatchers.IO) { database.updatePlaybackPosition(currentMetadata.id, finalPosition) }
        } else null

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
        }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        castConnectionHandler?.release()
        if (cachedPersistentQueue) {
            saveQueueToDisk()
        }
        screenOffHandler.removeCallbacks(screenOffTimeout)
        screenOffHandler.removeCallbacks(pauseTimeout)
        if (DiscordRpcManager.isReady()) {
            Timber.tag("DiscordSvc").i("onDestroy: disconnecting Discord RPC")
            DiscordRpcManager.disconnect()
        }
        DiscordRpcManager.destroy()
        connectivityObserver.unregister()
        abandonAudioFocus()
        closeAudioEffectSession()
        mediaLibrarySessionCallback.release()
        mediaSession?.release()
        crossfadeMessage?.cancel()
        crossfadeMessage = null
        crossfadeJob?.cancel()
        crossfadeJob = null
        secondaryPlayer?.let { pendingPlayer ->
            pendingPlayer.removeListener(secondaryPlayerListener)
            releaseExoPlayer(pendingPlayer)
        }
        secondaryPlayer = null
        fadingPlayer?.let(::releaseExoPlayer)
        fadingPlayer = null
        isCrossfading = false
        player.removeListener(this)
        sleepTimer?.let { player.removeListener(it) }
        initialBufferRecoveryJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        releaseExoPlayer(player)
        scope.cancel()
        super.onDestroy()
        if (episodeWrite == null) shutdownSignal.complete(Unit) else episodeWrite.invokeOnCompletion { error ->
            if (error == null) shutdownSignal.complete(Unit) else shutdownSignal.completeExceptionally(error)
        }
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (dataStore.get(StopMusicOnTaskClearKey, false)) {
            if (!::player.isInitialized) {
                stopSelf()
                return
            }
            // Remote playback (Cast) is independent of the local ExoPlayer; ending the session
            // is required or audio keeps playing on the Cast device.
            runCatching {
                if (castConnectionHandler?.isCasting?.value == true) {
                    castConnectionHandler?.disconnect()
                }
                player.stop()
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                controllerFuture?.let { MediaController.releaseFuture(it) }
                controllerFuture = null
                // Media3: coordinates notification/foreground teardown and stopSelf; required when
                // playback was ongoing (default super.onTaskRemoved keeps the service alive).
                pauseAllPlayersAndStopSelf()
            }.onFailure { e ->
                Timber.tag(TAG).e(e, "Failed to stop playback on task clear")
                controllerFuture?.let { MediaController.releaseFuture(it) }
                controllerFuture = null
                runCatching { pauseAllPlayersAndStopSelf() }.onFailure { stopSelf() }
            }
            return
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        try {
            super.onUpdateNotification(session, startInForegroundRequired)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            handleForegroundServiceStartNotAllowed(e)
        } catch (e: IllegalStateException) {
            if (isForegroundServiceStartNotAllowedException(e)) {
                handleForegroundServiceStartNotAllowed(e)
            } else {
                throw e
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        // On Android O+, every startForegroundService() call requires
        // Service.startForeground() to be called within a short timeout.
        // Some OEMs (e.g. MIUI) strictly enforce this even when the
        // service is already in the foreground, so always promote here.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!ensureForegroundWithLatestNotificationOrStop()) {
                return START_NOT_STICKY
            }
        }

        when (intent?.action) {
            ACTION_ALARM_TRIGGER -> {
                handleAlarmTrigger(intent)
            }

            MusicWidgetReceiver.ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                updateWidgetUI(player.isPlaying)
            }

            MusicWidgetReceiver.ACTION_LIKE -> {
                toggleLike()
            }

            MusicWidgetReceiver.ACTION_NEXT -> {
                player.seekToNext()
                updateWidgetUI(player.isPlaying)
            }

            MusicWidgetReceiver.ACTION_PREVIOUS -> {
                player.seekToPrevious()
                updateWidgetUI(player.isPlaying)
            }

            MusicWidgetReceiver.ACTION_UPDATE_WIDGET,
            PlaylistWidgetReceiver.ACTION_UPDATE_WIDGET -> {
                updateWidgetUI(player.isPlaying)
            }

            PlaylistWidgetReceiver.ACTION_PLAY_TARGET -> {
                handlePlaylistWidgetPlay(intent)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }


    private fun handlePlaylistWidgetPlay(intent: Intent) {
        scope.launch {
            try {
                val queue = withContext(Dispatchers.IO) {
                    buildPlaylistWidgetQueue(intent)
                }
                if (queue == null) {
                    openPlaylistWidgetTarget(intent)
                    return@launch
                }
                playQueue(queue, playWhenReady = true)
                updateWidgetUI(true)
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Failed to start playlist widget target")
                openPlaylistWidgetTarget(intent)
            }
        }
    }

    private fun openPlaylistWidgetTarget(source: Intent) {
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_WIDGET_TARGET
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(
                MainActivity.EXTRA_WIDGET_TARGET_TYPE,
                source.getStringExtra(PlaylistWidgetReceiver.EXTRA_TARGET_TYPE),
            )
            putExtra(
                MainActivity.EXTRA_WIDGET_TARGET_ID,
                source.getStringExtra(PlaylistWidgetReceiver.EXTRA_TARGET_ID),
            )
        }
        try {
            startActivity(activityIntent)
        } catch (t: Throwable) {
            Timber.tag(TAG).e(t, "Failed to open playlist widget target")
        }
    }

    private suspend fun buildPlaylistWidgetQueue(intent: Intent): Queue? {
        val targetType = intent.getStringExtra(PlaylistWidgetReceiver.EXTRA_TARGET_TYPE) ?: return null
        val targetId = intent.getStringExtra(PlaylistWidgetReceiver.EXTRA_TARGET_ID).orEmpty()
        val targetTitle = intent.getStringExtra(PlaylistWidgetReceiver.EXTRA_TARGET_TITLE)

        return when (targetType) {
            PlaylistWidgetReceiver.TARGET_TYPE_LOCAL -> {
                if (targetId.isBlank()) return null
                val songs = database.playlistSongs(targetId).first()
                if (songs.isEmpty()) return null
                val playlistName = database.playlist(targetId).first()?.playlist?.name ?: targetTitle
                ListQueue(
                    title = playlistName,
                    items = songs.map { it.song.toMediaItem() },
                )
            }

            PlaylistWidgetReceiver.TARGET_TYPE_ONLINE -> {
                if (targetId.isBlank()) return null
                val cachedPlaylist = database.playlistByBrowseId(targetId).first()
                val cachedSongs = cachedPlaylist?.let { database.playlistSongs(it.playlist.id).first() }.orEmpty()
                if (cachedSongs.isNotEmpty()) {
                    ListQueue(
                        title = cachedPlaylist?.playlist?.name ?: targetTitle,
                        items = cachedSongs.map { it.song.toMediaItem() },
                    )
                } else {
                    YouTubePlaylistQueue(
                        playlistId = targetId,
                        playlistTitle = targetTitle,
                    )
                }
            }

            PlaylistWidgetReceiver.TARGET_TYPE_LIKED -> {
                val songs = database.likedSongsByCreateDateAsc().first()
                if (songs.isEmpty()) return null
                ListQueue(
                    title = getString(R.string.liked_songs),
                    items = songs.map { it.toMediaItem() },
                )
            }

            PlaylistWidgetReceiver.TARGET_TYPE_DOWNLOADED -> {
                val songs = database.downloadedSongsByCreateDateAsc().first()
                if (songs.isEmpty()) return null
                ListQueue(
                    title = getString(R.string.downloaded_songs),
                    items = songs.map { it.toMediaItem() },
                )
            }

            PlaylistWidgetReceiver.TARGET_TYPE_TOP -> {
                val limit = targetId.toIntOrNull() ?: 50
                val songs = database.mostPlayedSongs(LocalDateTime.of(1970, 1, 1, 0, 0), limit = limit).first()
                if (songs.isEmpty()) return null
                ListQueue(
                    title = getString(R.string.my_top),
                    items = songs.map { it.toMediaItem() },
                )
            }

            else -> null
        }
    }

    private fun handleAlarmTrigger(intent: Intent) {
        scope.launch(Dispatchers.IO) {
            try {
                MusicAlarmScheduler.scheduleFromPreferences(this@MusicService)
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Failed to reschedule alarms after trigger")
            }
        }
        val playlistId = intent.getStringExtra(EXTRA_ALARM_PLAYLIST_ID).orEmpty()
        val alarmId = intent.getStringExtra(EXTRA_ALARM_ID).orEmpty()
        if (playlistId.isBlank()) {
            if (alarmId.isNotBlank()) {
                scope.launch(Dispatchers.IO) {
                    try {
                        val alarms = MusicAlarmStore.load(this@MusicService)
                        val updated =
                            alarms.map { alarm ->
                                if (alarm.id == alarmId) alarm.copy(enabled = false, nextTriggerAt = -1L) else alarm
                            }
                        MusicAlarmScheduler.scheduleAll(this@MusicService, updated)
                    } catch (t: Throwable) {
                        Timber.tag(TAG).e(t, "Failed to disable alarm with invalid playlist")
                    }
                }
            }
            return
        }
        val randomSong = intent.getBooleanExtra(EXTRA_ALARM_RANDOM_SONG, false)
        scope.launch {
            try {
                val playlistSongs =
                    withContext(Dispatchers.IO) {
                        database.playlistSongs(playlistId).first()
                    }
                if (playlistSongs.isEmpty()) {
                    if (alarmId.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            val alarms = MusicAlarmStore.load(this@MusicService)
                            val updated =
                                alarms.map { alarm ->
                                    if (alarm.id == alarmId) alarm.copy(enabled = false, nextTriggerAt = -1L) else alarm
                                }
                            MusicAlarmScheduler.scheduleAll(this@MusicService, updated)
                        }
                    }
                    return@launch
                }
                val items = playlistSongs.map { it.song.toMediaItem() }
                val playlistName =
                    withContext(Dispatchers.IO) {
                        database
                            .playlist(playlistId)
                            .first()
                            ?.playlist
                            ?.name
                    }

                val alarmItems =
                    if (randomSong) {
                        val firstIndex = Random.nextInt(items.size)
                        buildList(items.size) {
                            add(items[firstIndex])
                            items.forEachIndexed { index, item ->
                                if (index != firstIndex) add(item)
                            }
                        }
                    } else {
                        items
                    }

                player.stop()
                player.clearMediaItems()
                playQueue(
                    ListQueue(
                        title = playlistName,
                        items = alarmItems,
                        startIndex = 0,
                        position = 0L,
                    ),
                    playWhenReady = true,
                )
            } catch (t: Throwable) {
                Timber.tag(TAG).e(t, "Failed to start alarm playback")
            }
        }
    }

    private fun handleForegroundServiceStartNotAllowed(error: Throwable?) {
        if (error != null) {
            Timber.tag(TAG).w(error, "Foreground service start denied during notification update")
        } else {
            Timber.tag(TAG).w("Foreground service start denied by MediaSessionService listener")
        }

        if (tryEnsureForegroundWithLatestNotification()) {
            return
        }

        if (!::player.isInitialized) {
            stopSelf()
            return
        }

        if (player.isPlaying) {
            Timber.tag(TAG).w("Keeping playback alive after denied foreground restart request")
            return
        }

        runCatching {
            pauseAllPlayersAndStopSelf()
        }.onFailure {
            Timber.tag(TAG).w(it, "Failed to stop service after foreground start denial")
            stopSelf()
        }
    }

    private fun isForegroundServiceStartNotAllowedException(error: IllegalStateException): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            error.javaClass.name == ForegroundServiceStartNotAllowedException::class.java.name

    /**
     * Updates all app widgets with current playback state
     */
    private var widgetUpdateInFlight = false
    private var pendingWidgetUpdate: Pair<Boolean, Boolean?>? = null

    private fun updateWidgetUI(
        isPlaying: Boolean,
        isLiked: Boolean? = currentSong.value?.song?.let { if (it.isEpisode) it.inLibrary != null else it.liked }
    ) {
        pendingWidgetUpdate = isPlaying to isLiked
        if (widgetUpdateInFlight) return
        widgetUpdateInFlight = true

        scope.launch {
            try {
                while (true) {
                    val (playing, isLikedRequested) = pendingWidgetUpdate ?: break
                    pendingWidgetUpdate = null

                    val songData = currentSong.value
                    val song = songData?.song
                    val songTitle = song?.title ?: getString(R.string.no_song_playing)
                    val artistName = songData?.artists?.joinToArtistString(getArtistSeparator(this@MusicService)) { it.name } ?: getString(R.string.tap_to_open)
                    val resolvedIsLiked = isLikedRequested == true

                    widgetManager.updateWidgets(
                        title = songTitle,
                        artist = artistName,
                        artworkUri = song?.thumbnailUrl,
                        isPlaying = playing,
                        isLiked = resolvedIsLiked,
                        duration = if (player.duration != C.TIME_UNSET) player.duration else 0,
                        currentPosition = player.currentPosition,
                    )
                }
            } catch (e: Exception) {
            } finally {
                widgetUpdateInFlight = false
            }
        }
    }

    private var widgetUpdateJob: Job? = null

    private fun startWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob =
            scope.launch {
                while (isActive) {
                    if (player.isPlaying) {
                        updateWidgetUI(true)
                    }
                    delay(200)
                }
            }
    }

    private fun stopWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = null
    }

    /**
     * Get the stream URL for a given media ID.
     * This is used for Google Cast to send the audio URL to Chromecast.
     */
    suspend fun getStreamUrl(mediaId: String): String? =
        withContext(Dispatchers.IO) {
            try {
                val song = database.songEntity(mediaId)
                val playbackData =
                    YTPlayerUtils
                        .playerResponseForPlayback(
                            videoId = mediaId,
                            audioQuality = audioQuality,
                            connectivityManager = connectivityManager,
                            contentHints = ContentHints(
                                isExplicit = song?.explicit,
                                isUploaded = song?.isUploaded,
                            ),
                        ).getOrNull()
                playbackData?.streamUrl
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to get stream URL for Cast")
                null
            }
        }

    /**
     * Initialize Google Cast support
     */
    private fun initializeCast() {
        if (dataStore.get(com.sakayori.music.constants.EnableGoogleCastKey, true)) {
            try {
                castConnectionHandler = CastConnectionHandler(this, scope, this)
                castConnectionHandler?.initialize()
                castConnectionHandler?.isCasting?.collect(scope) { casting ->
                    if (casting) {
                        cancelRenditionChanges()
                        currentRendition.value = null
                        currentStreamClient.value = null
                        losslessResolver.retainPlaybackLookups(emptySet())
                    }
                    updateNativePreloading()
                }
                timber.log.Timber.d("Google Cast initialized")
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to initialize Google Cast")
            }
        }
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            val mediaId = player.currentMediaItem?.mediaId
            val existingLookup = sourceOwners[player]?.let { qualityLookups[it]?.second }
            ++renditionAttempt
            renditionLookupJob?.cancel()
            renditionChangeJob?.cancel()
            qualityCandidate?.volume = 0f
            qualityCandidate?.let { if (secondaryPlayer === it) secondaryPlayer = null }
            qualityCandidate = null
            qualityFading = false
            normalFallbackPlayer?.let { normal ->
                synchronizeNormalFallbackTimeline()
                if (newPosition.mediaItemIndex in 0 until normal.mediaItemCount) {
                    normal.seekTo(newPosition.mediaItemIndex, newPosition.positionMs)
                }
            }
            if (mediaId != null && existingLookup != null && currentRendition.value?.provider != AudioRendition.DIRECT_FLAC) {
                startRenditionLookup(mediaId, existingLookup)
            }
            crossfadePreparationJob?.cancel()
            applyEffectiveVolume()
            scheduleCrossfade()
        }
    }

    private fun scheduleCrossfade() {
        crossfadeMessage?.cancel()
        crossfadeMessage = null
        
        val mediaCrossfadeDuration = crossfadeDuration.toLong()

        if (!crossfadeEnabled || crossfadeDuration <= 0f || player.duration == C.TIME_UNSET || player.duration <= mediaCrossfadeDuration) return
        if (crossfadeGapless && isNextItemGapless()) return
        if (!player.hasNextMediaItem() && player.repeatMode != REPEAT_MODE_ONE) return

        val triggerTime = player.duration - mediaCrossfadeDuration
        val mediaTimeRemaining = triggerTime - player.currentPosition
        if (mediaTimeRemaining <= 0) return

        val targetMediaId = player.currentMediaItem?.mediaId

        crossfadeMessage = player.createMessage { _, _ ->
            val timer = sleepTimer
            if (player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId && (timer == null || !timer.pauseWhenSongEnd)) {
                startCrossfade()
            }
        }.apply {
            setLooper(Looper.getMainLooper())
            setPosition(triggerTime)
            send()
        }
    }

    private fun isNextItemGapless(): Boolean {
        val current = player.currentMediaItem?.mediaMetadata ?: return false
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return false
        val next = player.getMediaItemAt(nextIndex).mediaMetadata
        return current.albumTitle != null && current.albumTitle == next.albumTitle
    }

    private fun startCrossfade() {
        if (isCrossfading || crossfadePreparationJob?.isActive == true) return



        // Preserve player state before creating the secondary player
        val savedRepeatMode = player.repeatMode
        val savedShuffleEnabled = player.shuffleModeEnabled

        // For repeat-one, crossfade back into the same track
        val targetIndex =
            if (savedRepeatMode == REPEAT_MODE_ONE) {
                player.currentMediaItemIndex
            } else {
                player.nextMediaItemIndex
            }
        if (targetIndex == C.INDEX_UNSET) return
        cancelRenditionChanges()

        val originalPlayer = player
        val originalMediaId = originalPlayer.currentMediaItem?.mediaId
        val generation = playQueueGeneration
        val resource = if (preferLossless) losslessResolver.ready(player.getMediaItemAt(targetIndex).mediaId) else null
        secondaryPlayer = createExoPlayer(forcedResource = resource)
        val secPlayer = secondaryPlayer!!
        secPlayer.addListener(secondaryPlayerListener)

        val itemCount = player.mediaItemCount
        val items = mutableListOf<MediaItem>()
        for (i in 0 until itemCount) {
            items.add(player.getMediaItemAt(i))
        }

        secPlayer.setMediaItems(items)
        secPlayer.seekTo(targetIndex, 0)
        secPlayer.volume = 0f
        playerAudibility[secPlayer]?.update(silent = true)

        secPlayer.setPlaybackParameters(player.playbackParameters)

        secPlayer.repeatMode = savedRepeatMode
        secPlayer.shuffleModeEnabled = savedShuffleEnabled
        secPlayer.playbackParameters = player.playbackParameters
        copyPlaybackOrder(player, secPlayer)

        try {
            secPlayer.prepare()
            secPlayer.playWhenReady = false
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to prepare secondary player for crossfade")
            secPlayer.removeListener(secondaryPlayerListener)
            releaseExoPlayer(secPlayer)
            secondaryPlayer = null
            return
        }

        crossfadePreparationJob = scope.launch {
            try {
                withTimeoutOrNull(CROSSFADE_PLAY_WAIT_TIMEOUT_MS) {
                    awaitPlayerCondition(secPlayer) { secPlayer.playbackState == Player.STATE_READY }
                    if (player !== originalPlayer || playQueueGeneration != generation ||
                        player.currentMediaItem?.mediaId != originalMediaId || !player.isPlaying
                    ) return@withTimeoutOrNull
                    val audioStarted = CompletableDeferred<Unit>()
                    val audioListener = object : AnalyticsListener {
                        override fun onAudioPositionAdvancing(eventTime: AnalyticsListener.EventTime, playoutStartSystemTimeMs: Long) {
                            audioStarted.complete(Unit)
                        }
                    }
                    secPlayer.addAnalyticsListener(audioListener)
                    try {
                        secPlayer.playWhenReady = true
                        audioStarted.await()
                        awaitPlayerCondition(secPlayer) { secPlayer.isPlaying }
                    } finally {
                        if (secPlayer in sourceOwners) secPlayer.removeAnalyticsListener(audioListener)
                    }
                    if (player !== originalPlayer || player.currentMediaItem?.mediaId != originalMediaId || !player.isPlaying) return@withTimeoutOrNull
                    performCrossfadeSwap()
                    // The original permutation was copied, rather than reshuffling on handoff.
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // Preparation failed while outgoing playback was still in charge.
            } finally {
                if (secPlayer !== player) {
                    if (secondaryPlayer === secPlayer) secondaryPlayer = null
                    secPlayer.removeListener(secondaryPlayerListener)
                    releaseExoPlayer(secPlayer)
                }
            }
        }
    }

    private fun performCrossfadeSwap() {
        val nextPlayer = secondaryPlayer ?: return
        isCrossfading = true
        val currentPlayer = player
        crossfadeOutgoingMediaId = currentPlayer.currentMediaItem?.mediaId
        crossfadeOutgoingCompleted = currentPlayer.playbackState == Player.STATE_ENDED
        currentPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (fadingPlayer === currentPlayer && playbackState == Player.STATE_ENDED) crossfadeOutgoingCompleted = true
            }
        })
        playerAudibility[nextPlayer]?.update(silent = false)

        // Old-player repeat/timeline changes must not mutate the newly active player's state.
        currentPlayer.removeListener(this)
        sleepTimer?.let(currentPlayer::removeListener)
        fadingPlayer = currentPlayer
        player = nextPlayer
        activeSourceOwner = sourceOwners[nextPlayer]
        _playerFlow.value = player
        secondaryPlayer = null
        updateNativePreloading()

        fadingPlayer?.repeatMode = Player.REPEAT_MODE_OFF
        fadingPlayer?.let {
            val currentIndex = it.currentMediaItemIndex
            if (currentIndex < it.mediaItemCount - 1) {
                it.removeMediaItems(currentIndex + 1, it.mediaItemCount)
            }
        }

        player.addListener(
            object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    if (isCrossfading && fadingPlayer != null) {
                        if (playWhenReady) {
                            fadingPlayer?.play()
                        } else {
                            fadingPlayer?.pause()
                        }
                    } else {
                        player.removeListener(this)
                    }
                }
            },
        )

        nextPlayer.removeListener(secondaryPlayerListener)
        nextPlayer.addListener(this)
        sleepTimer?.let { nextPlayer.addListener(it) }

        sleepTimer?.player = player

        try {
            mediaSession?.let { (it as MediaSession).player = player }
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to swap player in MediaSession")
        }

        // secondaryPlayer was playing this item silently in the background without
        // `this` attached as a listener, so its real transition into this item never
        // reached onMediaItemTransition. Re-fire it manually now that the swap is done
        // so metadata recovery, cache marking, scrobbling, and normalization all run.
        lastTransitionedMediaId = null
        onMediaItemTransition(player.currentMediaItem, Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        currentMediaMetadata.value = player.currentMetadata
        sourceOwners[player]?.let { owner ->
            player.currentMediaItem?.mediaId?.let { id -> sourceSelections[owner]?.get(id)?.let { publishActiveRendition(owner, it) } }
        }

        val previousAudioSessionId = fadingPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET

        openAudioEffectSession()

        crossfadeJob =
            scope.launch {
                val speed = fadingPlayer?.playbackParameters?.speed?.coerceAtLeast(0.01f) ?: 1f
                val duration = (crossfadeDuration / speed).toLong()
                val steps = 20
                val stepTime = duration / steps
                val startVolume =
                    try {
                        fadingPlayer?.volume ?: 1f
                    } catch (e: Exception) {
                        1f
                    }

                var waitedMs = 0L
                for (i in 0..steps) {
                    if (!isActive) break
                    while (!player.isPlaying && isActive && waitedMs < CROSSFADE_PLAY_WAIT_TIMEOUT_MS) {
                        delay(100)
                        waitedMs += 100
                    }

                    val progress = i / steps.toFloat()
                    val fadeIn = 1.0f - (1.0f - progress) * (1.0f - progress)
                    val fadeOut = (1.0f - progress) * (1.0f - progress)

                    try {
                        player.volume = startVolume * fadeIn
                        fadingPlayer?.volume = startVolume * fadeOut
                    } catch (e: Exception) {
                        break
                    }

                    delay(stepTime)
                }

                try {
                    fadingPlayer?.volume = 0f
                    player.volume = startVolume
                } catch (e: Exception) {
                }

                cleanupCrossfade(fadingPlayerSessionId = previousAudioSessionId)
            }
    }

    private fun cleanupCrossfade(fadingPlayerSessionId: Int = C.AUDIO_SESSION_ID_UNSET) {
        if (crossfadeOutgoingCompleted || fadingPlayer?.playbackState == Player.STATE_ENDED) {
            crossfadeOutgoingMediaId?.let { id -> scope.launch(Dispatchers.IO + SilentHandler) { markCachedIfFullyDownloaded(id) } }
        }
        crossfadeOutgoingMediaId = null
        crossfadeOutgoingCompleted = false
        fadingPlayer?.let { previousPlayer ->
            previousPlayer.stop()
            previousPlayer.clearMediaItems()
            releaseExoPlayer(previousPlayer)
        }
        fadingPlayer = null
        crossfadeJob = null
        isCrossfading = false
        updateNativePreloading()
        applyEffectiveVolume()
        sleepTimer?.notifySongTransition()

        applyCachedAudioNormalizationNow()

        if (fadingPlayerSessionId != C.AUDIO_SESSION_ID_UNSET && fadingPlayerSessionId > 0) {
            closeAudioEffectSession(sessionIdOverride = fadingPlayerSessionId, clearNormalizationCache = true)
        }
    }

    private fun cancelCrossfadeForReload() {
        cancelRenditionChanges()
        crossfadePreparationJob?.cancel()
        crossfadePreparationJob = null
        crossfadeOutgoingMediaId = null
        crossfadeOutgoingCompleted = false
        crossfadeMessage?.cancel()
        crossfadeMessage = null
        crossfadeJob?.cancel()
        crossfadeJob = null
        secondaryPlayer?.let {
            it.removeListener(secondaryPlayerListener)
            releaseExoPlayer(it)
        }
        secondaryPlayer = null
        val fadingSessionId = fadingPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        fadingPlayer?.let(::releaseExoPlayer)
        fadingPlayer = null
        isCrossfading = false
        applyEffectiveVolume()
        if (fadingSessionId > 0) closeAudioEffectSession(sessionIdOverride = fadingSessionId, clearNormalizationCache = false)
    }

    private fun releaseExoPlayer(player: ExoPlayer) {
        if (player !in sourceOwners) return
        playerAudibility.remove(player)?.update(playing = false)
        if (this::player.isInitialized && player === this.player) {
            activeSourceOwner = null
            currentRendition.value = null
            activeRenditionKey = null
            activeRenditionDescriptor = null
            persistedRenditionKey = null
            formatPublishJob?.cancel()
        }
        preloadControllers.remove(player)?.release()
        sourceOwners.remove(player)?.let {
            sourceSelections.remove(it)
            qualityLookups.remove(it)
            forcedRenditions.remove(it)
            renditionPreparationBudgets.remove(it)?.cancel()
        }
        playerNormalizationProcessors.remove(player)
        playerSilenceProcessors.remove(player)
        playerEqualizerProcessors.remove(player)?.let(equalizerService::removeAudioProcessor)
        player.release()
    }

    companion object {
        const val ACTION_ALARM_TRIGGER = "com.sakayori.music.action.ALARM_TRIGGER"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_PLAYLIST_ID = "extra_alarm_playlist_id"
        const val EXTRA_ALARM_RANDOM_SONG = "extra_alarm_random_song"

        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val YOUTUBE_PLAYLIST = "youtube_playlist"
        const val SEARCH = "search"
        const val SHUFFLE_ACTION = "__shuffle__"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
        const val MAX_RETRY_COUNT = 10

        private const val INITIAL_BUFFER_RECOVERY_DELAY_MS = 15_000L
        private const val CROSSFADE_PLAY_WAIT_TIMEOUT_MS = 15_000L
        private const val INITIAL_BUFFER_RECOVERY_POSITION_MS = 5_000L
        private const val QUALITY_PREPARE_TIMEOUT_MS = 5_000L
        private const val QUALITY_TRANSITION_MS = 120L
        private const val QUALITY_ALIGNMENT_TOLERANCE_MS = 10L
        private const val QUALITY_DURATION_TOLERANCE_MS = 150L
        private const val MAX_GAIN_MB = 300 // Maximum gain in millibels (3 dB)
        private const val MIN_GAIN_MB = -1500 // Minimum gain in millibels (-15 dB)

        private const val TAG = "MusicService"

        @Volatile
        private var runningInstance: MusicService? = null

        val isRunning: Boolean
            get() = runningInstance != null
            
        @Volatile
        var shutdownDeferred = kotlinx.coroutines.CompletableDeferred<Unit>().apply { complete(Unit) }
    }
}
