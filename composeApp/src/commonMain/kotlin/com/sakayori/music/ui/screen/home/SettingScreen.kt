package com.sakayori.music.ui.screen.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Error
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Lyrics
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.eygraber.uri.toKmpUri
import com.sakayori.common.LIMIT_CACHE_SIZE
import com.sakayori.common.QUALITY
import com.sakayori.common.SUPPORTED_LANGUAGE
import com.sakayori.common.SUPPORTED_LOCATION
import com.sakayori.common.SponsorBlockType
import com.sakayori.common.VIDEO_QUALITY
import com.sakayori.domain.extension.now
import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.domain.manager.DataStoreManager.Values.TRUE
import com.sakayori.domain.utils.LocalResource
import com.sakayori.logger.Logger
import com.sakayori.music.Platform
import com.sakayori.music.expect.ui.fileSaverResult
import com.sakayori.music.expect.ui.openEqResult
import com.sakayori.music.extension.bytesToMB
import com.sakayori.music.extension.displayString
import com.sakayori.music.extension.isTwoLetterCode
import com.sakayori.music.extension.isValidProxyHost
import com.sakayori.music.getPlatform
import com.sakayori.music.ui.component.ActionButton
import com.sakayori.music.ui.component.CenterLoadingBox
import com.sakayori.music.ui.component.EndOfPage
import com.sakayori.music.ui.component.RippleIconButton
import com.sakayori.music.ui.component.SettingItem
import com.sakayori.music.ui.component.SettingSection
import com.sakayori.music.ui.navigation.destination.home.CreditDestination
import com.sakayori.music.ui.navigation.destination.home.EqualizerDestination
import com.sakayori.music.ui.navigation.destination.login.DiscordLoginDestination
import com.sakayori.music.ui.navigation.destination.login.LoginDestination
import com.sakayori.music.ui.navigation.destination.login.SpotifyLoginDestination
import com.sakayori.music.ui.theme.DarkColors
import com.sakayori.music.ui.theme.md_theme_dark_primary
import com.sakayori.music.ui.theme.typo
import com.sakayori.music.ui.theme.white
import com.sakayori.music.utils.VersionManager
import com.sakayori.music.viewModel.SettingAlertState
import com.sakayori.music.viewModel.SettingBasicAlertState
import com.sakayori.music.viewModel.SettingsViewModel
import com.sakayori.music.viewModel.SharedViewModel
import com.sakayori.music.viewModel.UIEvent
import com.mikepenz.aboutlibraries.entity.Library
import com.mikepenz.aboutlibraries.ui.compose.ChipColors
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.produceLibraries
import com.mohamedrejeb.calf.core.ExperimentalCalfApi
import com.mohamedrejeb.calf.io.getPath
import com.mohamedrejeb.calf.picker.FilePickerFileType
import com.mohamedrejeb.calf.picker.FilePickerSelectionMode
import com.mohamedrejeb.calf.picker.rememberFilePickerLauncher
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import com.sakayori.music.extension.getStringBlocking
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.about_us
import com.sakayori.music.generated.resources.add_an_account
import com.sakayori.music.generated.resources.ai
import com.sakayori.music.generated.resources.ai_api_key
import com.sakayori.music.generated.resources.ai_provider
import com.sakayori.music.generated.resources.anonymous
import com.sakayori.music.generated.resources.app_name
import com.sakayori.music.generated.resources.audio
import com.sakayori.music.generated.resources.author
import com.sakayori.music.generated.resources.auto_backup
import com.sakayori.music.generated.resources.auto_backup_description
import com.sakayori.music.generated.resources.backup
import com.sakayori.music.generated.resources.backup_downloaded
import com.sakayori.music.generated.resources.backup_downloaded_description
import com.sakayori.music.generated.resources.backup_frequency
import com.sakayori.music.generated.resources.balance_media_loudness
import com.sakayori.music.generated.resources.baseline_arrow_back_ios_new_24
import com.sakayori.music.generated.resources.baseline_close_24
import com.sakayori.music.generated.resources.baseline_people_alt_24
import com.sakayori.music.generated.resources.baseline_playlist_add_24
import com.sakayori.music.generated.resources.better_lyrics
import com.sakayori.music.generated.resources.blur_fullscreen_lyrics
import com.sakayori.music.generated.resources.blur_fullscreen_lyrics_description
import com.sakayori.music.generated.resources.blur_player_background
import com.sakayori.music.generated.resources.blur_player_background_description
import com.sakayori.music.generated.resources.buy_me_a_coffee
import com.sakayori.music.generated.resources.cancel
import com.sakayori.music.generated.resources.canvas_info
import com.sakayori.music.generated.resources.categories_sponsor_block
import com.sakayori.music.generated.resources.change
import com.sakayori.music.generated.resources.change_language_warning
import com.sakayori.music.generated.resources.check_for_update
import com.sakayori.music.generated.resources.checking
import com.sakayori.music.generated.resources.clear
import com.sakayori.music.generated.resources.clear_canvas_cache
import com.sakayori.music.generated.resources.clear_downloaded_cache
import com.sakayori.music.generated.resources.clear_player_cache
import com.sakayori.music.generated.resources.clear_thumbnail_cache
import com.sakayori.music.generated.resources.content
import com.sakayori.music.generated.resources.content_country
import com.sakayori.music.generated.resources.contributor_email
import com.sakayori.music.generated.resources.contributor_name
import com.sakayori.music.generated.resources.crossfade
import com.sakayori.music.generated.resources.crossfade_auto
import com.sakayori.music.generated.resources.crossfade_description
import com.sakayori.music.generated.resources.crossfade_dj_mode
import com.sakayori.music.generated.resources.crossfade_dj_mode_description
import com.sakayori.music.generated.resources.builtin_equalizer_with_presets
import com.sakayori.music.generated.resources.crash_reporting
import com.sakayori.music.generated.resources.crash_reporting_description
import com.sakayori.music.generated.resources.crossfade_preview
import com.sakayori.music.generated.resources.crossfade_preview_description
import com.sakayori.music.generated.resources.open_equalizer
import com.sakayori.music.generated.resources.privacy
import com.sakayori.music.generated.resources.search_settings
import com.sakayori.music.generated.resources.crossfade_duration
import com.sakayori.music.generated.resources.custom_ai_model_id
import com.sakayori.music.generated.resources.custom_model_id_messages
import com.sakayori.music.generated.resources.daily
import com.sakayori.music.generated.resources.database
import com.sakayori.music.generated.resources.default_models
import com.sakayori.music.generated.resources.description_and_licenses
import com.sakayori.music.generated.resources.discord_integration
import com.sakayori.music.generated.resources.donation
import com.sakayori.music.generated.resources.download_quality
import com.sakayori.music.generated.resources.downloaded_cache
import com.sakayori.music.generated.resources.enable_canvas
import com.sakayori.music.generated.resources.enable_liquid_glass_effect
import com.sakayori.music.generated.resources.enable_liquid_glass_effect_description
import com.sakayori.music.generated.resources.very_low_performance_mode
import com.sakayori.music.generated.resources.very_low_performance_mode_description
import com.sakayori.music.generated.resources.low_end_device_blur_disabled
import com.sakayori.music.utils.DeviceCapability
import com.sakayori.music.generated.resources.enable_rich_presence
import com.sakayori.music.generated.resources.enable_sponsor_block
import com.sakayori.music.generated.resources.enable_spotify_lyrics
import com.sakayori.music.generated.resources.free_space
import com.sakayori.music.generated.resources.gemini
import com.sakayori.music.generated.resources.guest
import com.sakayori.music.generated.resources.help_build_lyrics_database
import com.sakayori.music.generated.resources.help_build_lyrics_database_description
import com.sakayori.music.generated.resources.http
import com.sakayori.music.generated.resources.intro_login_to_discord
import com.sakayori.music.generated.resources.intro_login_to_spotify
import com.sakayori.music.generated.resources.invalid
import com.sakayori.music.generated.resources.invalid_api_key
import com.sakayori.music.generated.resources.invalid_host
import com.sakayori.music.generated.resources.invalid_language_code
import com.sakayori.music.generated.resources.invalid_port
import com.sakayori.music.generated.resources.keep_backups
import com.sakayori.music.generated.resources.keep_backups_format
import com.sakayori.music.generated.resources.keep_service_alive
import com.sakayori.music.generated.resources.keep_service_alive_description
import com.sakayori.music.generated.resources.keep_your_youtube_playlist_offline
import com.sakayori.music.generated.resources.keep_your_youtube_playlist_offline_description
import com.sakayori.music.generated.resources.kill_service_on_exit
import com.sakayori.music.generated.resources.kill_service_on_exit_description
import com.sakayori.music.generated.resources.language
import com.sakayori.music.generated.resources.last_backup
import com.sakayori.music.generated.resources.last_checked_at
import com.sakayori.music.generated.resources.never
import com.sakayori.music.generated.resources.limit_player_cache
import com.sakayori.music.generated.resources.local_tracking_description
import com.sakayori.music.generated.resources.local_tracking_title
import com.sakayori.music.generated.resources.log_in_to_discord
import com.sakayori.music.generated.resources.log_in_to_spotify
import com.sakayori.music.generated.resources.log_out
import com.sakayori.music.generated.resources.log_out_warning
import com.sakayori.music.generated.resources.logged_in
import com.sakayori.music.generated.resources.lrclib
import com.sakayori.music.generated.resources.lyrics
import com.sakayori.music.generated.resources.main_lyrics_provider
import com.sakayori.music.generated.resources.manage_your_youtube_accounts
import com.sakayori.music.generated.resources.sakayori_dev
import com.sakayori.music.generated.resources.monthly
import com.sakayori.music.generated.resources.never
import com.sakayori.music.generated.resources.no_account
import com.sakayori.music.generated.resources.normalize_volume
import com.sakayori.music.generated.resources.open_system_equalizer
import com.sakayori.music.generated.resources.openai
import com.sakayori.music.generated.resources.openai_api_compatible
import com.sakayori.music.generated.resources.other_app
import com.sakayori.music.generated.resources.play_explicit_content
import com.sakayori.music.generated.resources.play_explicit_content_description
import com.sakayori.music.generated.resources.play_video_for_video_track_instead_of_audio_only
import com.sakayori.music.generated.resources.playback
import com.sakayori.music.generated.resources.player_cache
import com.sakayori.music.generated.resources.prefer_320kbps_stream
import com.sakayori.music.generated.resources.prefer_320kbps_stream_description
import com.sakayori.music.generated.resources.proxy
import com.sakayori.music.generated.resources.proxy_description
import com.sakayori.music.generated.resources.proxy_host
import com.sakayori.music.generated.resources.proxy_host_message
import com.sakayori.music.generated.resources.proxy_port
import com.sakayori.music.generated.resources.proxy_port_message
import com.sakayori.music.generated.resources.proxy_type
import com.sakayori.music.generated.resources.quality
import com.sakayori.music.generated.resources.restore_your_data
import com.sakayori.music.generated.resources.restore_your_saved_data
import com.sakayori.music.generated.resources.desktop_rpc_auto_description
import com.sakayori.music.generated.resources.rich_presence_info
import com.sakayori.music.generated.resources.save
import com.sakayori.music.generated.resources.save_all_your_playlist_data
import com.sakayori.music.generated.resources.save_last_played
import com.sakayori.music.generated.resources.save_last_played_track_and_queue
import com.sakayori.music.generated.resources.sleep_timer_fade_out
import com.sakayori.music.generated.resources.sleep_timer_fade_out_description
import com.sakayori.music.generated.resources.save_playback_state
import com.sakayori.music.generated.resources.save_shuffle_and_repeat_mode
import com.sakayori.music.generated.resources.send_back_listening_data_to_google
import com.sakayori.music.generated.resources.set
import com.sakayori.music.generated.resources.settings
import com.sakayori.music.generated.resources.signed_in
import com.sakayori.music.generated.resources.SakayoriMusic_lyrics
import com.sakayori.music.generated.resources.skip_no_music_part
import com.sakayori.music.generated.resources.skip_silent
import com.sakayori.music.generated.resources.skip_sponsor_part_of_video
import com.sakayori.music.generated.resources.socks
import com.sakayori.music.generated.resources.sponsorBlock
import com.sakayori.music.generated.resources.sponsor_block_intro
import com.sakayori.music.generated.resources.spotify
import com.sakayori.music.generated.resources.spotify_canvas_cache
import com.sakayori.music.generated.resources.spotify_lyrícs_info
import com.sakayori.music.generated.resources.storage
import com.sakayori.music.generated.resources.such_as_music_video_lyrics_video_podcasts_and_more
import com.sakayori.music.generated.resources.third_party_libraries
import com.sakayori.music.generated.resources.thumbnail_cache
import com.sakayori.music.generated.resources.translation_language
import com.sakayori.music.generated.resources.translation_language_message
import com.sakayori.music.generated.resources.translucent_bottom_navigation_bar
import com.sakayori.music.generated.resources.unknown
import com.sakayori.music.generated.resources.update_channel
import com.sakayori.music.generated.resources.upload_your_listening_history_to_youtube_music_server_it_will_make_yt_music_recommendation_system_better_working_only_if_logged_in
import com.sakayori.music.generated.resources.use_ai_translation
import com.sakayori.music.generated.resources.use_ai_translation_description
import com.sakayori.music.generated.resources.use_your_system_equalizer
import com.sakayori.music.generated.resources.user_interface
import com.sakayori.music.generated.resources.version
import com.sakayori.music.generated.resources.version_format
import com.sakayori.music.generated.resources.video_download_quality
import com.sakayori.music.generated.resources.video_quality
import com.sakayori.music.generated.resources.warning
import com.sakayori.music.generated.resources.weekly
import com.sakayori.music.generated.resources.what_segments_will_be_skipped
import com.sakayori.music.generated.resources.you_can_see_the_content_below_the_bottom_bar
import com.sakayori.music.generated.resources.your_320kbps_url
import com.sakayori.music.generated.resources.youtube_account
import com.sakayori.music.generated.resources.youtube_subtitle_language
import com.sakayori.music.generated.resources.youtube_subtitle_language_message
import com.sakayori.music.generated.resources.youtube_transcript
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalCoilApi::class,
    ExperimentalHazeMaterialsApi::class,
    FormatStringsInDatetimeFormats::class,
    ExperimentalCalfApi::class,
)
@Composable
fun SettingScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
    sharedViewModel: SharedViewModel = koinInject(),
) {
    val platformContext = LocalPlatformContext.current
    val pl = com.mohamedrejeb.calf.core.LocalPlatformContext.current
    val localDensity = LocalDensity.current
    val uriHandler = LocalUriHandler.current
    val coroutineScope = rememberCoroutineScope()

    var width by rememberSaveable { mutableIntStateOf(0) }

    val formatter =
        LocalDateTime.Format {
            byUnicodePattern("yyyyMMddHHmmss")
        }
    val appName = stringResource(Res.string.app_name)

    val backupLauncher =
        fileSaverResult(
            "${appName}_${
                now().format(
                    formatter,
                )
            }.backup",
            "application/octet-stream",
        ) { uri ->
            uri?.let {
                viewModel.backup(it.toKmpUri())
            }
        }

    val restoreLauncher =
        rememberFilePickerLauncher(
            type =
                FilePickerFileType.All,
            selectionMode = FilePickerSelectionMode.Single,
        ) { file ->
            file.firstOrNull()?.getPath(pl)?.toKmpUri()?.let {
                viewModel.restore(it)
            }
        }

    val resultLauncher = openEqResult(viewModel.getAudioSessionId())

    val enableTranslucentNavBar by viewModel.translucentBottomBar.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val language by viewModel.language.collectAsStateWithLifecycle()
    val location by viewModel.location.collectAsStateWithLifecycle()
    val quality by viewModel.quality.collectAsStateWithLifecycle()
    val prefer320kbpsStream by viewModel.prefer320kbpsStream.collectAsStateWithLifecycle()
    val your320kbpsUrl by viewModel.your320kbpsUrl.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val videoDownloadQuality by viewModel.videoDownloadQuality.collectAsStateWithLifecycle()
    val keepYoutubePlaylistOffline by viewModel.keepYouTubePlaylistOffline.collectAsStateWithLifecycle()
    val localTrackingEnabled by viewModel.localTrackingEnabled.collectAsStateWithLifecycle(initialValue = false)
    val combineLocalAndYouTubeLiked by viewModel.combineLocalAndYouTubeLiked.collectAsStateWithLifecycle()
    val playVideo by viewModel.playVideoInsteadOfAudio.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val videoQuality by viewModel.videoQuality.collectAsStateWithLifecycle()
    val sendData by viewModel.sendBackToGoogle.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val normalizeVolume by viewModel.normalizeVolume.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val skipSilent by viewModel.skipSilent.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val savePlaybackState by viewModel.savedPlaybackState.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val saveLastPlayed by viewModel.saveRecentSongAndQueue.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val killServiceOnExit by viewModel.killServiceOnExit.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = true)
    val mainLyricsProvider by viewModel.mainLyricsProvider.collectAsStateWithLifecycle()
    val youtubeSubtitleLanguage by viewModel.youtubeSubtitleLanguage.collectAsStateWithLifecycle()
    val spotifyLoggedIn by viewModel.spotifyLogIn.collectAsStateWithLifecycle()
    val spotifyLyrics by viewModel.spotifyLyrics.collectAsStateWithLifecycle()
    val spotifyCanvas by viewModel.spotifyCanvas.collectAsStateWithLifecycle()
    val enableSponsorBlock by viewModel.sponsorBlockEnabled.map { it == TRUE }.collectAsStateWithLifecycle(initialValue = false)
    val skipSegments by viewModel.sponsorBlockCategories.collectAsStateWithLifecycle()
    val playerCache by viewModel.cacheSize.collectAsStateWithLifecycle()
    val downloadedCache by viewModel.downloadedCacheSize.collectAsStateWithLifecycle()
    val thumbnailCache by viewModel.thumbCacheSize.collectAsStateWithLifecycle()
    val canvasCache by viewModel.canvasCacheSize.collectAsStateWithLifecycle()
    val limitPlayerCache by viewModel.playerCacheLimit.collectAsStateWithLifecycle()
    val fraction by viewModel.fraction.collectAsStateWithLifecycle()
    val lastCheckUpdate by viewModel.lastCheckForUpdate.collectAsStateWithLifecycle()
    val explicitContentEnabled by viewModel.explicitContentEnabled.collectAsStateWithLifecycle()
    val usingProxy by viewModel.usingProxy.collectAsStateWithLifecycle()
    val proxyType by viewModel.proxyType.collectAsStateWithLifecycle()
    val proxyHost by viewModel.proxyHost.collectAsStateWithLifecycle()
    val proxyPort by viewModel.proxyPort.collectAsStateWithLifecycle()
    val blurFullscreenLyrics by viewModel.blurFullscreenLyrics.collectAsStateWithLifecycle()
    val blurPlayerBackground by viewModel.blurPlayerBackground.collectAsStateWithLifecycle()
    val aiProvider by viewModel.aiProvider.collectAsStateWithLifecycle()
    val isHasApiKey by viewModel.isHasApiKey.collectAsStateWithLifecycle()
    val useAITranslation by viewModel.useAITranslation.collectAsStateWithLifecycle()
    val translationLanguage by viewModel.translationLanguage.collectAsStateWithLifecycle()
    val customModelId by viewModel.customModelId.collectAsStateWithLifecycle()
    val customOpenAIBaseUrl by viewModel.customOpenAIBaseUrl.collectAsStateWithLifecycle()
    val customOpenAIHeaders by viewModel.customOpenAIHeaders.collectAsStateWithLifecycle()
    val helpBuildLyricsDatabase by viewModel.helpBuildLyricsDatabase.collectAsStateWithLifecycle()
    val contributor by viewModel.contributor.collectAsStateWithLifecycle()
    val backupDownloaded by viewModel.backupDownloaded.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val autoBackupFrequency by viewModel.autoBackupFrequency.collectAsStateWithLifecycle()
    val autoBackupMaxFiles by viewModel.autoBackupMaxFiles.collectAsStateWithLifecycle()
    val autoBackupLastTime by viewModel.autoBackupLastTime.collectAsStateWithLifecycle()
    val updateChannel by viewModel.updateChannel.collectAsStateWithLifecycle()
    val enableLiquidGlass by viewModel.enableLiquidGlass.collectAsStateWithLifecycle()
    val lowResourceMode by viewModel.lowResourceMode.collectAsStateWithLifecycle()
    val crashReportingEnabled by viewModel.crashReportingEnabled.collectAsStateWithLifecycle()
    val sleepTimerFadeOut by viewModel.sleepTimerFadeOut.collectAsStateWithLifecycle()
    val isLowEndDevice = remember { DeviceCapability.isLowEndDevice() }
    val lowEndDisableReason = stringResource(Res.string.low_end_device_blur_disabled)
    val discordLoggedIn by viewModel.discordLoggedIn.collectAsStateWithLifecycle()
    val richPresenceEnabled by viewModel.richPresenceEnabled.collectAsStateWithLifecycle()
    val keepServiceAlive by viewModel.keepServiceAlive.collectAsStateWithLifecycle()

    val crossfadeEnabled by viewModel.crossfadeEnabled.collectAsStateWithLifecycle()
    val crossfadeDuration by viewModel.crossfadeDuration.collectAsStateWithLifecycle()
    val crossfadeDjMode by viewModel.crossfadeDjMode.collectAsStateWithLifecycle()

    val isCheckingUpdate by sharedViewModel.isCheckingUpdate.collectAsStateWithLifecycle()

    val isBlurEnabled = com.sakayori.music.extension.LocalBlurEnabled.current
    val hazeState =
        rememberHazeState(
            blurEnabled = isBlurEnabled,
        )

    val checkForUpdateSubtitle by remember {
        derivedStateOf {
            if (isCheckingUpdate) {
                return@derivedStateOf getStringBlocking(Res.string.checking)
            } else {
                val lastCheckLong = lastCheckUpdate?.toLongOrNull() ?: 0L
                val timeString = if (lastCheckLong > 0L) {
                    DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault())
                        .format(Instant.ofEpochMilli(lastCheckLong))
                } else {
                    getStringBlocking(Res.string.never)
                }
                return@derivedStateOf getStringBlocking(Res.string.last_checked_at, timeString)
            }
        }
    }
    var showYouTubeAccountDialog by rememberSaveable {
        mutableStateOf(false)
    }
    var showThirdPartyLibraries by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(true) {
        viewModel.getAllGoogleAccount()
    }

    LaunchedEffect(true) {
        viewModel.getData()
        viewModel.getThumbCacheSize(platformContext)
    }

    var settingsSearchQuery by rememberSaveable { mutableStateOf("") }
    val uiLabel = stringResource(Res.string.user_interface)
    val contentLabel = stringResource(Res.string.content)
    val audioLabel = stringResource(Res.string.audio)
    val playbackLabel = stringResource(Res.string.playback)
    val crossfadeLabel = stringResource(Res.string.crossfade)
    val lyricsLabel = stringResource(Res.string.lyrics)
    val aiLabel = stringResource(Res.string.ai)
    val spotifyLabel = stringResource(Res.string.spotify)
    val discordLabel = stringResource(Res.string.discord_integration)
    val sponsorLabel = stringResource(Res.string.sponsorBlock)
    val storageLabel = stringResource(Res.string.storage)
    val privacyLabel = stringResource(Res.string.privacy)
    val backupLabel = stringResource(Res.string.backup)
    val aboutLabel = stringResource(Res.string.about_us)

    fun matchQuery(label: String): Boolean =
        settingsSearchQuery.isEmpty() || label.contains(settingsSearchQuery, ignoreCase = true)

    LazyColumn(
        contentPadding = innerPadding,
        modifier =
            Modifier
                .padding(horizontal = 16.dp)
                .hazeSource(hazeState),
    ) {
        item {
            Spacer(Modifier.height(64.dp))
        }
        item(key = "settings_search") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1A1A1A))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = androidx.compose.material.icons.Icons.Rounded.Search,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    androidx.compose.foundation.text.BasicTextField(
                        value = settingsSearchQuery,
                        onValueChange = { settingsSearchQuery = it },
                        singleLine = true,
                        textStyle = typo().bodyMedium.copy(color = white),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color(0xFF00BCD4)),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (settingsSearchQuery.isEmpty()) {
                                Text(
                                    stringResource(Res.string.search_settings),
                                    style = typo().bodyMedium,
                                    color = Color.White.copy(alpha = 0.4f),
                                )
                            }
                            inner()
                        },
                    )
                    if (settingsSearchQuery.isNotEmpty()) {
                        androidx.compose.material3.IconButton(
                            onClick = { settingsSearchQuery = "" },
                            modifier = Modifier.size(20.dp),
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = androidx.compose.material.icons.Icons.Rounded.Clear,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
        if (matchQuery(uiLabel)) item(key = "user_interface") {
            Spacer(Modifier.height(16.dp))
            SettingSection(
                title = stringResource(Res.string.user_interface),
                icon = Icons.Outlined.Palette,
            ) {
                SettingItem(
                    title = stringResource(Res.string.translucent_bottom_navigation_bar),
                    subtitle = stringResource(Res.string.you_can_see_the_content_below_the_bottom_bar),
                    smallSubtitle = true,
                    switch = (enableTranslucentNavBar to { viewModel.setTranslucentBottomBar(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.blur_fullscreen_lyrics),
                    subtitle = stringResource(Res.string.blur_fullscreen_lyrics_description),
                    smallSubtitle = true,
                    switch = (blurFullscreenLyrics to { viewModel.setBlurFullscreenLyrics(it) }),
                    isEnable = !isLowEndDevice,
                    disableReason = if (isLowEndDevice) lowEndDisableReason else null,
                )
                SettingItem(
                    title = stringResource(Res.string.blur_player_background),
                    subtitle = stringResource(Res.string.blur_player_background_description),
                    smallSubtitle = true,
                    switch = (blurPlayerBackground to { viewModel.setBlurPlayerBackground(it) }),
                    isEnable = !isLowEndDevice,
                    disableReason = if (isLowEndDevice) lowEndDisableReason else null,
                )
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.enable_liquid_glass_effect),
                        subtitle = stringResource(Res.string.enable_liquid_glass_effect_description),
                        smallSubtitle = true,
                        switch = (enableLiquidGlass to { viewModel.setEnableLiquidGlass(it) }),
                        isEnable = getPlatform() == Platform.Android && !isLowEndDevice,
                        disableReason = if (isLowEndDevice) lowEndDisableReason else null,
                    )
                }
                SettingItem(
                    title = stringResource(Res.string.very_low_performance_mode),
                    subtitle = stringResource(Res.string.very_low_performance_mode_description),
                    smallSubtitle = true,
                    switch = (lowResourceMode to { viewModel.setLowResourceMode(it) }),
                )
            }
        }
        if (matchQuery(contentLabel)) item(key = "content") {
            SettingSection(
                title = stringResource(Res.string.content),
                icon = Icons.Outlined.Language,
            ) {
                SettingItem(
                    title = stringResource(Res.string.youtube_account),
                    subtitle = stringResource(Res.string.manage_your_youtube_accounts),
                    onClick = {
                        viewModel.getAllGoogleAccount()
                        showYouTubeAccountDialog = true
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.language),
                    subtitle = SUPPORTED_LANGUAGE.getLanguageFromCode(language ?: "en-US"),
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.language),
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            SUPPORTED_LANGUAGE.items.map {
                                                (it.toString() == SUPPORTED_LANGUAGE.getLanguageFromCode(language ?: "en-US")) to it.toString()
                                            },
                                    ),
                                confirm =
                                    getStringBlocking(Res.string.change) to { state ->
                                        val code = SUPPORTED_LANGUAGE.getCodeFromLanguage(state.selectOne?.getSelected() ?: "English")
                                        viewModel.setBasicAlertData(
                                            SettingBasicAlertState(
                                                title = getStringBlocking(Res.string.warning),
                                                message = getStringBlocking(Res.string.change_language_warning),
                                                confirm =
                                                    getStringBlocking(Res.string.change) to {
                                                        sharedViewModel.activityRecreate()
                                                        viewModel.setBasicAlertData(null)
                                                        viewModel.changeLanguage(code)
                                                    },
                                                dismiss = getStringBlocking(Res.string.cancel),
                                            ),
                                        )
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.content_country),
                    subtitle = location ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.content_country),
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            SUPPORTED_LOCATION.items.map { item ->
                                                (item.toString() == location) to item.toString()
                                            },
                                    ),
                                confirm =
                                    getStringBlocking(Res.string.change) to { state ->
                                        viewModel.changeLocation(
                                            state.selectOne?.getSelected() ?: "US",
                                        )
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.quality),
                    subtitle = quality ?: "",
                    smallSubtitle = true,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.quality),
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            QUALITY.items.map { item ->
                                                (item.toString() == quality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    getStringBlocking(Res.string.change) to { state ->
                                        viewModel.changeQuality(state.selectOne?.getSelected())
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.prefer_320kbps_stream),
                    subtitle = stringResource(Res.string.prefer_320kbps_stream_description),
                    smallSubtitle = true,
                    switch = (prefer320kbpsStream to { viewModel.setPrefer320kbpsStream(it) }),
                )
                AnimatedVisibility(visible = prefer320kbpsStream, enter = slideInVertically() + fadeIn(), exit = slideOutVertically() + fadeOut()) {
                    SettingItem(
                        title = stringResource(Res.string.your_320kbps_url),
                        subtitle = your320kbpsUrl,
                        isEnable = prefer320kbpsStream,
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = getStringBlocking(Res.string.your_320kbps_url),
                                    textField =
                                        SettingAlertState.TextFieldData(
                                            label = getStringBlocking(Res.string.your_320kbps_url),
                                            value = "",
                                            verifyCodeBlock = {
                                                (it.isNotEmpty()) to getStringBlocking(Res.string.invalid)
                                            },
                                        ),
                                    message = "",
                                    confirm =
                                        getStringBlocking(Res.string.set) to { state ->
                                            viewModel.setYour320kbpsUrl(state.textField?.value ?: "")
                                        },
                                    dismiss = getStringBlocking(Res.string.cancel),
                                ),
                            )
                        },
                    )
                }
                SettingItem(
                    title = stringResource(Res.string.download_quality),
                    subtitle = downloadQuality ?: "",
                    smallSubtitle = true,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.download_quality),
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            QUALITY.items.map { item ->
                                                (item.toString() == downloadQuality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    getStringBlocking(Res.string.change) to { state ->
                                        state.selectOne?.getSelected()?.let { viewModel.setDownloadQuality(it) }
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.play_video_for_video_track_instead_of_audio_only),
                    subtitle = stringResource(Res.string.such_as_music_video_lyrics_video_podcasts_and_more),
                    smallSubtitle = true,
                    switch = (playVideo to { viewModel.setPlayVideoInsteadOfAudio(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.video_quality),
                    subtitle = videoQuality ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.video_quality),
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            VIDEO_QUALITY.items.map { item ->
                                                (item.toString() == videoQuality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    getStringBlocking(Res.string.change) to { state ->
                                        viewModel.changeVideoQuality(state.selectOne?.getSelected() ?: "")
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.video_download_quality),
                    subtitle = videoDownloadQuality ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.video_download_quality),
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            VIDEO_QUALITY.items.map { item ->
                                                (item.toString() == videoDownloadQuality) to item.toString()
                                            },
                                    ),
                                confirm =
                                    getStringBlocking(Res.string.change) to { state ->
                                        viewModel.setVideoDownloadQuality(state.selectOne?.getSelected() ?: "")
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.send_back_listening_data_to_google),
                    subtitle =
                        stringResource(
                            Res.string
                                .upload_your_listening_history_to_youtube_music_server_it_will_make_yt_music_recommendation_system_better_working_only_if_logged_in,
                        ),
                    smallSubtitle = true,
                    switch = (sendData to { viewModel.setSendBackToGoogle(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.play_explicit_content),
                    subtitle = stringResource(Res.string.play_explicit_content_description),
                    switch = (explicitContentEnabled to { viewModel.setExplicitContentEnabled(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.keep_your_youtube_playlist_offline),
                    subtitle = stringResource(Res.string.keep_your_youtube_playlist_offline_description),
                    switch = (keepYoutubePlaylistOffline to { viewModel.setKeepYouTubePlaylistOffline(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.local_tracking_title),
                    subtitle = stringResource(Res.string.local_tracking_description),
                    switch = (localTrackingEnabled to { viewModel.setLocalTrackingEnabled(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.proxy),
                    subtitle = stringResource(Res.string.proxy_description),
                    switch = (usingProxy to { viewModel.setUsingProxy(it) }),
                )
            }
        }
        item(key = "proxy") {
            Crossfade(usingProxy) { it ->
                if (it) {
                    Column {
                        SettingItem(
                            title = stringResource(Res.string.proxy_type),
                            subtitle =
                                when (proxyType) {
                                    DataStoreManager.ProxyType.PROXY_TYPE_HTTP -> stringResource(Res.string.http)
                                    DataStoreManager.ProxyType.PROXY_TYPE_SOCKS -> stringResource(Res.string.socks)
                                },
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = getStringBlocking(Res.string.proxy_type),
                                        selectOne =
                                            SettingAlertState.SelectData(
                                                listSelect =
                                                    listOf(
                                                        (proxyType == DataStoreManager.ProxyType.PROXY_TYPE_HTTP) to
                                                            getStringBlocking(Res.string.http),
                                                        (proxyType == DataStoreManager.ProxyType.PROXY_TYPE_SOCKS) to
                                                            getStringBlocking(Res.string.socks),
                                                    ),
                                            ),
                                        confirm =
                                            getStringBlocking(Res.string.change) to { state ->
                                                viewModel.setProxy(
                                                    if (state.selectOne?.getSelected() == getStringBlocking(Res.string.socks)) {
                                                        DataStoreManager.ProxyType.PROXY_TYPE_SOCKS
                                                    } else {
                                                        DataStoreManager.ProxyType.PROXY_TYPE_HTTP
                                                    },
                                                    proxyHost,
                                                    proxyPort,
                                                )
                                            },
                                        dismiss = getStringBlocking(Res.string.cancel),
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.proxy_host),
                            subtitle = proxyHost,
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = getStringBlocking(Res.string.proxy_host),
                                        message = getStringBlocking(Res.string.proxy_host_message),
                                        textField =
                                            SettingAlertState.TextFieldData(
                                                label = getStringBlocking(Res.string.proxy_host),
                                                value = proxyHost,
                                                verifyCodeBlock = {
                                                    isValidProxyHost(it) to getStringBlocking(Res.string.invalid_host)
                                                },
                                            ),
                                        confirm =
                                            getStringBlocking(Res.string.change) to { state ->
                                                viewModel.setProxy(
                                                    proxyType,
                                                    state.textField?.value ?: "",
                                                    proxyPort,
                                                )
                                            },
                                        dismiss = getStringBlocking(Res.string.cancel),
                                    ),
                                )
                            },
                        )
                        SettingItem(
                            title = stringResource(Res.string.proxy_port),
                            subtitle = proxyPort.toString(),
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = getStringBlocking(Res.string.proxy_port),
                                        message = getStringBlocking(Res.string.proxy_port_message),
                                        textField =
                                            SettingAlertState.TextFieldData(
                                                label = getStringBlocking(Res.string.proxy_port),
                                                value = proxyPort.toString(),
                                                verifyCodeBlock = {
                                                    (it.toIntOrNull() != null) to getStringBlocking(Res.string.invalid_port)
                                                },
                                            ),
                                        confirm =
                                            getStringBlocking(Res.string.change) to { state ->
                                                viewModel.setProxy(
                                                    proxyType,
                                                    proxyHost,
                                                    state.textField?.value?.toIntOrNull() ?: 0,
                                                )
                                            },
                                        dismiss = getStringBlocking(Res.string.cancel),
                                    ),
                                )
                            },
                        )
                    }
                }
            }
        }
        if (getPlatform() == Platform.Android) {
            if (matchQuery(audioLabel)) item(key = "audio") {
                SettingSection(
                    title = stringResource(Res.string.audio),
                    icon = Icons.Outlined.MusicNote,
                ) {
                    SettingItem(
                        title = stringResource(Res.string.normalize_volume),
                        subtitle = stringResource(Res.string.balance_media_loudness),
                        switch = (normalizeVolume to { viewModel.setNormalizeVolume(it) }),
                    )
                    SettingItem(
                        title = stringResource(Res.string.skip_silent),
                        subtitle = stringResource(Res.string.skip_no_music_part),
                        switch = (skipSilent to { viewModel.setSkipSilent(it) }),
                    )
                    SettingItem(
                        title = stringResource(Res.string.open_equalizer),
                        subtitle = stringResource(Res.string.builtin_equalizer_with_presets),
                        onClick = {
                            navController.navigate(EqualizerDestination)
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.open_system_equalizer),
                        subtitle = stringResource(Res.string.use_your_system_equalizer),
                        onClick = {
                            coroutineScope.launch {
                                resultLauncher.launch()
                            }
                        },
                    )
                }
            }
        }
        if (matchQuery(playbackLabel)) item(key = "playback") {
            SettingSection(
                title = stringResource(Res.string.playback),
                icon = Icons.Outlined.PlayCircle,
            ) {
                SettingItem(
                    title = stringResource(Res.string.save_playback_state),
                    subtitle = stringResource(Res.string.save_shuffle_and_repeat_mode),
                    switch = (savePlaybackState to { viewModel.setSavedPlaybackState(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.save_last_played),
                    subtitle = stringResource(Res.string.save_last_played_track_and_queue),
                    switch = (saveLastPlayed to { viewModel.setSaveLastPlayed(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.sleep_timer_fade_out),
                    subtitle = stringResource(Res.string.sleep_timer_fade_out_description),
                    smallSubtitle = true,
                    switch = (sleepTimerFadeOut to { viewModel.setSleepTimerFadeOut(it) }),
                )
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.kill_service_on_exit),
                        subtitle = stringResource(Res.string.kill_service_on_exit_description),
                        switch = (killServiceOnExit to { viewModel.setKillServiceOnExit(it) }),
                    )
                    SettingItem(
                        title = stringResource(Res.string.keep_service_alive),
                        subtitle = stringResource(Res.string.keep_service_alive_description),
                        switch = (keepServiceAlive to { viewModel.setKeepServiceAlive(it) }),
                    )
                }
            }
        }
        if (matchQuery(crossfadeLabel)) item(key = "crossfade_settings") {
            Column {
                SettingItem(
                    title = stringResource(Res.string.crossfade),
                    subtitle = stringResource(Res.string.crossfade_description),
                    smallSubtitle = true,
                    switch = (crossfadeEnabled to { viewModel.setCrossfadeEnabled(it) }),
                )
                AnimatedVisibility(visible = crossfadeEnabled) {
                    Column {
                        SettingItem(
                            title = stringResource(Res.string.crossfade_duration),
                            subtitle =
                                if (crossfadeDuration == DataStoreManager.CROSSFADE_DURATION_AUTO) {
                                    stringResource(Res.string.crossfade_auto)
                                } else {
                                    "${crossfadeDuration / 1000}s"
                                },
                            onClick = {
                                viewModel.setAlertData(
                                    SettingAlertState(
                                        title = getStringBlocking(Res.string.crossfade_duration),
                                        selectOne =
                                            SettingAlertState.SelectData(
                                                listSelect =
                                                    listOf(
                                                        (crossfadeDuration == DataStoreManager.CROSSFADE_DURATION_AUTO) to
                                                            getStringBlocking(Res.string.crossfade_auto),
                                                        (crossfadeDuration == 1000) to "1s",
                                                        (crossfadeDuration == 2000) to "2s",
                                                        (crossfadeDuration == 3000) to "3s",
                                                        (crossfadeDuration == 5000) to "5s",
                                                        (crossfadeDuration == 8000) to "8s",
                                                        (crossfadeDuration == 10000) to "10s",
                                                        (crossfadeDuration == 12000) to "12s",
                                                        (crossfadeDuration == 15000) to "15s",
                                                        (crossfadeDuration == 20000) to "20s",
                                                        (crossfadeDuration == 30000) to "30s",
                                                    ),
                                            ),
                                        confirm =
                                            getStringBlocking(Res.string.change) to { state ->
                                                val duration =
                                                    when (state.selectOne?.getSelected()) {
                                                        getStringBlocking(Res.string.crossfade_auto) -> DataStoreManager.CROSSFADE_DURATION_AUTO
                                                        "1s" -> 1000
                                                        "2s" -> 2000
                                                        "3s" -> 3000
                                                        "5s" -> 5000
                                                        "8s" -> 8000
                                                        "10s" -> 10000
                                                        "12s" -> 12000
                                                        "15s" -> 15000
                                                        "20s" -> 20000
                                                        "30s" -> 30000
                                                        else -> 5000
                                                    }
                                                viewModel.setCrossfadeDuration(duration)
                                            },
                                        dismiss = getStringBlocking(Res.string.cancel),
                                    ),
                                )
                            },
                        )
                        if (getPlatform() == Platform.Android) {
                            SettingItem(
                                title = stringResource(Res.string.crossfade_dj_mode),
                                subtitle = stringResource(Res.string.crossfade_dj_mode_description),
                                smallSubtitle = true,
                                switch = ((crossfadeDjMode) to { viewModel.setCrossfadeDjMode(it) }),
                            )
                        }
                        SettingItem(
                            title = stringResource(Res.string.crossfade_preview),
                            subtitle = stringResource(Res.string.crossfade_preview_description),
                            smallSubtitle = true,
                            onClick = {
                                val duration = sharedViewModel.getPlayerDuration()
                                if (duration > 0) {
                                    val previewMs = crossfadeDuration.coerceAtLeast(5000)
                                    val seekTo = (duration - previewMs).coerceAtLeast(0L)
                                    val progress = seekTo.toFloat() / duration.toFloat()
                                    sharedViewModel.onUIEvent(UIEvent.UpdateProgress(progress))
                                }
                            },
                        )
                    }
                }
            }
        }
        if (matchQuery(lyricsLabel)) item(key = "lyrics") {
            SettingSection(
                title = stringResource(Res.string.lyrics),
                icon = Icons.Outlined.Lyrics,
            ) {
                SettingItem(
                    title = stringResource(Res.string.main_lyrics_provider),
                    subtitle =
                        when (mainLyricsProvider) {
                            DataStoreManager.SakayoriMusic -> stringResource(Res.string.SakayoriMusic_lyrics)
                            DataStoreManager.YOUTUBE -> stringResource(Res.string.youtube_transcript)
                            DataStoreManager.LRCLIB -> stringResource(Res.string.lrclib)
                            DataStoreManager.BETTER_LYRICS -> stringResource(Res.string.better_lyrics)
                            else -> stringResource(Res.string.unknown)
                        },
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.main_lyrics_provider),
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            listOf(
                                                (mainLyricsProvider == DataStoreManager.SakayoriMusic) to
                                                    getStringBlocking(Res.string.SakayoriMusic_lyrics),
                                                (mainLyricsProvider == DataStoreManager.YOUTUBE) to
                                                    getStringBlocking(Res.string.youtube_transcript),
                                                (mainLyricsProvider == DataStoreManager.LRCLIB) to getStringBlocking(Res.string.lrclib),
                                                (mainLyricsProvider == DataStoreManager.BETTER_LYRICS) to
                                                    getStringBlocking(Res.string.better_lyrics),
                                            ),
                                    ),
                                confirm =
                                    getStringBlocking(Res.string.change) to { state ->
                                        viewModel.setLyricsProvider(
                                            when (state.selectOne?.getSelected()) {
                                                getStringBlocking(Res.string.SakayoriMusic_lyrics) -> DataStoreManager.SakayoriMusic
                                                getStringBlocking(Res.string.youtube_transcript) -> DataStoreManager.YOUTUBE
                                                getStringBlocking(Res.string.lrclib) -> DataStoreManager.LRCLIB
                                                getStringBlocking(Res.string.better_lyrics) -> DataStoreManager.BETTER_LYRICS
                                                else -> DataStoreManager.SakayoriMusic
                                            },
                                        )
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )

                SettingItem(
                    title = stringResource(Res.string.translation_language),
                    subtitle = translationLanguage ?: "",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.translation_language),
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = getStringBlocking(Res.string.translation_language),
                                        value = translationLanguage ?: "",
                                        verifyCodeBlock = {
                                            (it.length == 2 && it.isTwoLetterCode()) to
                                                getStringBlocking(Res.string.invalid_language_code)
                                        },
                                    ),
                                message = getStringBlocking(Res.string.translation_language_message),
                                confirm =
                                    getStringBlocking(Res.string.change) to { state ->
                                        viewModel.setTranslationLanguage(state.textField?.value ?: "")
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                    isEnable = true,
                )
                SettingItem(
                    title = stringResource(Res.string.youtube_subtitle_language),
                    subtitle = youtubeSubtitleLanguage,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.youtube_subtitle_language),
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = getStringBlocking(Res.string.youtube_subtitle_language),
                                        value = youtubeSubtitleLanguage,
                                        verifyCodeBlock = {
                                            (it.length == 2 && it.isTwoLetterCode()) to
                                                getStringBlocking(Res.string.invalid_language_code)
                                        },
                                    ),
                                message = getStringBlocking(Res.string.youtube_subtitle_language_message),
                                confirm =
                                    getStringBlocking(Res.string.change) to { state ->
                                        viewModel.setYoutubeSubtitleLanguage(state.textField?.value ?: "")
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.help_build_lyrics_database),
                    subtitle = stringResource(Res.string.help_build_lyrics_database_description),
                    switch = (helpBuildLyricsDatabase to { viewModel.setHelpBuildLyricsDatabase(it) }),
                )
                SettingItem(
                    title = stringResource(Res.string.contributor_name),
                    subtitle = contributor.first.ifEmpty { stringResource(Res.string.anonymous) },
                    isEnable = helpBuildLyricsDatabase,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.contributor_name),
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = getStringBlocking(Res.string.contributor_name),
                                        value = "",
                                    ),
                                message = "",
                                confirm =
                                    getStringBlocking(Res.string.set) to { state ->
                                        viewModel.setContributorName(state.textField?.value ?: "")
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.contributor_email),
                    subtitle = contributor.second.ifEmpty { stringResource(Res.string.anonymous) },
                    isEnable = helpBuildLyricsDatabase,
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.contributor_email),
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = getStringBlocking(Res.string.contributor_email),
                                        value = "",
                                        verifyCodeBlock = {
                                            if (it.isNotEmpty()) {
                                                (it.contains("@")) to getStringBlocking(Res.string.invalid)
                                            } else {
                                                true to ""
                                            }
                                        },
                                    ),
                                message = "",
                                confirm =
                                    getStringBlocking(Res.string.set) to { state ->
                                        viewModel.setContributorEmail(state.textField?.value ?: "")
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
            }
        }
        if (matchQuery(aiLabel)) item(key = "AI") {
            SettingSection(
                title = stringResource(Res.string.ai),
                icon = Icons.Outlined.Psychology,
            ) {
                SettingItem(
                    title = stringResource(Res.string.ai_provider),
                    subtitle =
                        when (aiProvider) {
                            DataStoreManager.AI_PROVIDER_OPENAI -> stringResource(Res.string.openai)
                            DataStoreManager.AI_PROVIDER_GEMINI -> stringResource(Res.string.gemini)
                            DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI -> stringResource(Res.string.openai_api_compatible)
                            else -> stringResource(Res.string.unknown)
                        },
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.ai_provider),
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            listOf(
                                                (mainLyricsProvider == DataStoreManager.AI_PROVIDER_OPENAI) to
                                                    getStringBlocking(Res.string.openai),
                                                (mainLyricsProvider == DataStoreManager.AI_PROVIDER_GEMINI) to
                                                    getStringBlocking(Res.string.gemini),
                                                (mainLyricsProvider == DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI) to
                                                    getStringBlocking(Res.string.openai_api_compatible),
                                            ),
                                    ),
                                confirm =
                                    getStringBlocking(Res.string.change) to { state ->
                                        viewModel.setAIProvider(
                                            when (state.selectOne?.getSelected()) {
                                                getStringBlocking(Res.string.openai) -> DataStoreManager.AI_PROVIDER_OPENAI
                                                getStringBlocking(Res.string.gemini) -> DataStoreManager.AI_PROVIDER_GEMINI
                                                getStringBlocking(Res.string.openai_api_compatible) -> DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI

                                                else -> DataStoreManager.AI_PROVIDER_OPENAI
                                            },
                                        )
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.ai_api_key),
                    subtitle = if (isHasApiKey) "XXXXXXXXXX" else "N/A",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.ai_api_key),
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = getStringBlocking(Res.string.ai_api_key),
                                        value = "",
                                        verifyCodeBlock = {
                                            (it.isNotEmpty()) to getStringBlocking(Res.string.invalid_api_key)
                                        },
                                    ),
                                message = "",
                                confirm =
                                    getStringBlocking(Res.string.set) to { state ->
                                        viewModel.setAIApiKey(state.textField?.value ?: "")
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.custom_ai_model_id),
                    subtitle = customModelId.ifEmpty { stringResource(Res.string.default_models) },
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.custom_ai_model_id),
                                textField =
                                    SettingAlertState.TextFieldData(
                                        label = getStringBlocking(Res.string.custom_ai_model_id),
                                        value = "",
                                        verifyCodeBlock = {
                                            (it.isNotEmpty() && !it.contains(" ")) to getStringBlocking(Res.string.invalid)
                                        },
                                    ),
                                message = getStringBlocking(Res.string.custom_model_id_messages),
                                confirm =
                                    getStringBlocking(Res.string.set) to { state ->
                                        viewModel.setCustomModelId(state.textField?.value ?: "")
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                if (aiProvider == DataStoreManager.AI_PROVIDER_CUSTOM_OPENAI) {
                    SettingItem(
                        title = "Custom Base URL",
                        subtitle = customOpenAIBaseUrl.ifEmpty { "https://api.openai.com/v1/" },
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = "Custom Base URL",
                                    textField =
                                        SettingAlertState.TextFieldData(
                                            label = "Base URL",
                                            value = customOpenAIBaseUrl,
                                            verifyCodeBlock = {
                                                (it.isEmpty() || it.startsWith("http")) to "Invalid URL format"
                                            },
                                        ),
                                    message = "Enter OpenAI-compatible API base URL (e.g., https://api.openai.com/v1/)",
                                    confirm =
                                        getStringBlocking(Res.string.set) to { state ->
                                            viewModel.setCustomOpenAIBaseUrl(state.textField?.value ?: "")
                                        },
                                    dismiss = getStringBlocking(Res.string.cancel),
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = "Custom Headers",
                        subtitle = if (customOpenAIHeaders.isNotEmpty()) "Configured" else "Not set",
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = "Custom Headers (JSON)",
                                    textField =
                                        SettingAlertState.TextFieldData(
                                            label = "Headers JSON",
                                            value = customOpenAIHeaders,
                                            verifyCodeBlock = { input ->
                                                if (input.isEmpty()) {
                                                    true to null
                                                } else {
                                                    try {
                                                        val trimmed = input.trim()
                                                        (trimmed.startsWith("{") && trimmed.endsWith("}")) to "Invalid JSON format"
                                                    } catch (e: Exception) {
                                                        false to "Invalid JSON format"
                                                    }
                                                }
                                            },
                                        ),
                                    message = "Enter custom headers in JSON format:\n{\"key1\":\"value1\",\"key2\":\"value2\"}",
                                    confirm =
                                        getStringBlocking(Res.string.set) to { state ->
                                            viewModel.setCustomOpenAIHeaders(state.textField?.value ?: "")
                                        },
                                    dismiss = getStringBlocking(Res.string.cancel),
                                ),
                            )
                        },
                    )
                }
                SettingItem(
                    title = stringResource(Res.string.use_ai_translation),
                    subtitle = stringResource(Res.string.use_ai_translation_description),
                    switch = (useAITranslation to { viewModel.setAITranslation(it) }),
                    isEnable = isHasApiKey,
                    onDisable = {
                        if (useAITranslation) {
                            viewModel.setAITranslation(false)
                        }
                    },
                )
            }
        }
        if (matchQuery(spotifyLabel)) item(key = "spotify") {
            SettingSection(
                title = stringResource(Res.string.spotify),
                icon = Icons.Outlined.MusicNote,
            ) {
                SettingItem(
                    title = stringResource(Res.string.log_in_to_spotify),
                    subtitle =
                        if (spotifyLoggedIn) {
                            stringResource(Res.string.logged_in)
                        } else {
                            stringResource(Res.string.intro_login_to_spotify)
                        },
                    onClick = {
                        if (spotifyLoggedIn) {
                            viewModel.setSpotifyLogIn(false)
                        } else {
                            navController.navigate(SpotifyLoginDestination)
                        }
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.enable_spotify_lyrics),
                    subtitle = stringResource(Res.string.spotify_lyrícs_info),
                    switch = (spotifyLyrics to { viewModel.setSpotifyLyrics(it) }),
                    isEnable = spotifyLoggedIn,
                    onDisable = {
                        if (spotifyLyrics) {
                            viewModel.setSpotifyLyrics(false)
                        }
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.enable_canvas),
                    subtitle = stringResource(Res.string.canvas_info),
                    switch = (spotifyCanvas to { viewModel.setSpotifyCanvas(it) }),
                    isEnable = spotifyLoggedIn,
                    onDisable = {
                        if (spotifyCanvas) {
                            viewModel.setSpotifyCanvas(false)
                        }
                    },
                )
            }
        }
        if (matchQuery(discordLabel)) item(key = "discord") {
            SettingSection(
                title = stringResource(Res.string.discord_integration),
                icon = Icons.Outlined.SettingsEthernet,
            ) {
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.log_in_to_discord),
                        subtitle =
                            if (discordLoggedIn) {
                                stringResource(Res.string.logged_in)
                            } else {
                                stringResource(Res.string.intro_login_to_discord)
                            },
                        onClick = {
                            if (discordLoggedIn) {
                                viewModel.logOutDiscord()
                            } else {
                                navController.navigate(DiscordLoginDestination)
                            }
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.enable_rich_presence),
                        subtitle = stringResource(Res.string.rich_presence_info),
                        switch = (richPresenceEnabled to { viewModel.setDiscordRichPresenceEnabled(it) }),
                        isEnable = discordLoggedIn,
                        onDisable = {
                            if (discordLoggedIn) {
                                viewModel.setDiscordRichPresenceEnabled(false)
                            }
                        },
                    )
                } else {
                    SettingItem(
                        title = stringResource(Res.string.enable_rich_presence),
                        subtitle = stringResource(Res.string.desktop_rpc_auto_description),
                        switch = (richPresenceEnabled to { viewModel.setDiscordRichPresenceEnabled(it) }),
                    )
                }
            }
        }
        if (matchQuery(sponsorLabel)) item(key = "sponsor_block") {
            SettingSection(
                title = stringResource(Res.string.sponsorBlock),
                icon = Icons.Outlined.Tune,
            ) {
                SettingItem(
                    title = stringResource(Res.string.enable_sponsor_block),
                    subtitle = stringResource(Res.string.skip_sponsor_part_of_video),
                    switch = (enableSponsorBlock to { viewModel.setSponsorBlockEnabled(it) }),
                )
                val listName =
                    SponsorBlockType.toList().map { it.displayString() }
                SettingItem(
                    title = stringResource(Res.string.categories_sponsor_block),
                    subtitle = stringResource(Res.string.what_segments_will_be_skipped),
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.categories_sponsor_block),
                                multipleSelect =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            listName
                                                .mapIndexed { index, item ->
                                                    (
                                                        skipSegments?.contains(
                                                            SponsorBlockType.toList().getOrNull(index)?.value,
                                                        ) == true
                                                    ) to item
                                                },
                                    ),
                                confirm =
                                    getStringBlocking(Res.string.save) to { state ->
                                        viewModel.setSponsorBlockCategories(
                                            state.multipleSelect
                                                ?.getListSelected()
                                                ?.map { selected ->
                                                    listName.indexOf(selected)
                                                }?.mapNotNull { s ->
                                                    SponsorBlockType.toList().getOrNull(s).let {
                                                        it?.value
                                                    }
                                                }?.toCollection(ArrayList()) ?: arrayListOf(),
                                        )
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                    isEnable = enableSponsorBlock,
                )
                val beforeUrl = stringResource(Res.string.sponsor_block_intro).substringBefore("https://sponsor.ajay.app/")
                val afterUrl = stringResource(Res.string.sponsor_block_intro).substringAfter("https://sponsor.ajay.app/")
                Text(
                    buildAnnotatedString {
                        append(beforeUrl)
                        withLink(
                            LinkAnnotation.Url(
                                "https://sponsor.ajay.app/",
                                TextLinkStyles(style = SpanStyle(color = md_theme_dark_primary)),
                            ),
                        ) {
                            append("https://sponsor.ajay.app/")
                        }
                        append(afterUrl)
                    },
                    style = typo().bodySmall,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
        if (getPlatform() == Platform.Android) {
            if (matchQuery(storageLabel)) item(key = "storage") {
                SettingSection(
                    title = stringResource(Res.string.storage),
                    icon = Icons.Outlined.Storage,
                ) {
                    SettingItem(
                        title = stringResource(Res.string.player_cache),
                        subtitle = "${playerCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = getStringBlocking(Res.string.clear_player_cache),
                                    message = null,
                                    confirm =
                                        getStringBlocking(Res.string.clear) to {
                                            viewModel.clearPlayerCache()
                                        },
                                    dismiss = getStringBlocking(Res.string.cancel),
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.downloaded_cache),
                        subtitle = "${downloadedCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = getStringBlocking(Res.string.clear_downloaded_cache),
                                    message = null,
                                    confirm =
                                        getStringBlocking(Res.string.clear) to {
                                            viewModel.clearDownloadedCache()
                                        },
                                    dismiss = getStringBlocking(Res.string.cancel),
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.thumbnail_cache),
                        subtitle = "${thumbnailCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = getStringBlocking(Res.string.clear_thumbnail_cache),
                                    message = null,
                                    confirm =
                                        getStringBlocking(Res.string.clear) to {
                                            viewModel.clearThumbnailCache(platformContext)
                                        },
                                    dismiss = getStringBlocking(Res.string.cancel),
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.spotify_canvas_cache),
                        subtitle = "${canvasCache.bytesToMB()} MB",
                        onClick = {
                            viewModel.setBasicAlertData(
                                SettingBasicAlertState(
                                    title = getStringBlocking(Res.string.clear_canvas_cache),
                                    message = null,
                                    confirm =
                                        getStringBlocking(Res.string.clear) to {
                                            viewModel.clearCanvasCache()
                                        },
                                    dismiss = getStringBlocking(Res.string.cancel),
                                ),
                            )
                        },
                    )
                    SettingItem(
                        title = stringResource(Res.string.limit_player_cache),
                        subtitle = LIMIT_CACHE_SIZE.getItemFromData(limitPlayerCache).toString(),
                        onClick = {
                            viewModel.setAlertData(
                                SettingAlertState(
                                    title = getStringBlocking(Res.string.limit_player_cache),
                                    selectOne =
                                        SettingAlertState.SelectData(
                                            listSelect =
                                                LIMIT_CACHE_SIZE.items.map { item ->
                                                    (item == LIMIT_CACHE_SIZE.getItemFromData(limitPlayerCache)) to item.toString()
                                                },
                                        ),
                                    confirm =
                                        getStringBlocking(Res.string.change) to { state ->
                                            viewModel.setPlayerCacheLimit(
                                                LIMIT_CACHE_SIZE.getDataFromItem(state.selectOne?.getSelected()),
                                            )
                                        },
                                    dismiss = getStringBlocking(Res.string.cancel),
                                ),
                            )
                        },
                    )
                    Box(
                        Modifier.padding(
                            horizontal = 24.dp,
                            vertical = 16.dp,
                        ),
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(0.dp),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .onGloballyPositioned { layoutCoordinates ->
                                        with(localDensity) {
                                            width =
                                                layoutCoordinates.size.width
                                                    .toDp()
                                                    .value
                                                    .toInt()
                                        }
                                    },
                        ) {
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.otherApp * width).dp,
                                            ).background(
                                                md_theme_dark_primary,
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.downloadCache * width).dp,
                                            ).background(
                                                Color(0xD540FF17),
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.playerCache * width).dp,
                                            ).background(
                                                Color(0xD5FFFF00),
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.canvasCache * width).dp,
                                            ).background(
                                                Color.Cyan,
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.thumbCache * width).dp,
                                            ).background(
                                                Color.Magenta,
                                            ).fillMaxHeight(),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.appDatabase * width).dp,
                                            ).background(
                                                Color.White,
                                            ),
                                )
                            }
                            item {
                                Box(
                                    modifier =
                                        Modifier
                                            .width(
                                                (fraction.freeSpace * width).dp,
                                            ).background(
                                                Color.DarkGray,
                                            ).fillMaxHeight(),
                                )
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    md_theme_dark_primary,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.other_app), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Green,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.downloaded_cache), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Yellow,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.player_cache), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Cyan,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.spotify_canvas_cache), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.Magenta,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.thumbnail_cache), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.White,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.database), style = typo().bodySmall)
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    ) {
                        Box(
                            Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    Color.LightGray,
                                ),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = stringResource(Res.string.free_space), style = typo().bodySmall)
                    }
                }
            }
        }
        if (matchQuery(privacyLabel)) item(key = "privacy") {
            SettingSection(
                title = stringResource(Res.string.privacy),
                icon = Icons.Outlined.Lock,
            ) {
                SettingItem(
                    title = stringResource(Res.string.crash_reporting),
                    subtitle = stringResource(Res.string.crash_reporting_description),
                    smallSubtitle = true,
                    switch = (crashReportingEnabled to { viewModel.setCrashReportingEnabled(it) }),
                )
            }
        }
        if (matchQuery(backupLabel)) item(key = "backup") {
            SettingSection(
                title = stringResource(Res.string.backup),
                icon = Icons.Outlined.Backup,
            ) {
                SettingItem(
                    title = stringResource(Res.string.backup_downloaded),
                    subtitle = stringResource(Res.string.backup_downloaded_description),
                    switch = (backupDownloaded to { viewModel.setBackupDownloaded(it) }),
                )
                if (getPlatform() == Platform.Android) {
                    SettingItem(
                        title = stringResource(Res.string.auto_backup),
                        subtitle = stringResource(Res.string.auto_backup_description),
                        switch = (autoBackupEnabled to { viewModel.setAutoBackupEnabled(it) }),
                    )
                    AnimatedVisibility(visible = autoBackupEnabled) {
                        Column {
                            SettingItem(
                                title = stringResource(Res.string.backup_frequency),
                                subtitle =
                                    when (autoBackupFrequency) {
                                        DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY -> stringResource(Res.string.daily)
                                        DataStoreManager.AUTO_BACKUP_FREQUENCY_WEEKLY -> stringResource(Res.string.weekly)
                                        DataStoreManager.AUTO_BACKUP_FREQUENCY_MONTHLY -> stringResource(Res.string.monthly)
                                        else -> stringResource(Res.string.daily)
                                    },
                                onClick = {
                                    viewModel.setAlertData(
                                        SettingAlertState(
                                            title = getStringBlocking(Res.string.backup_frequency),
                                            selectOne =
                                                SettingAlertState.SelectData(
                                                    listSelect =
                                                        listOf(
                                                            (autoBackupFrequency == DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY) to
                                                                getStringBlocking(Res.string.daily),
                                                            (autoBackupFrequency == DataStoreManager.AUTO_BACKUP_FREQUENCY_WEEKLY) to
                                                                getStringBlocking(Res.string.weekly),
                                                            (autoBackupFrequency == DataStoreManager.AUTO_BACKUP_FREQUENCY_MONTHLY) to
                                                                getStringBlocking(Res.string.monthly),
                                                        ),
                                                ),
                                            confirm =
                                                getStringBlocking(Res.string.change) to { state ->
                                                    val frequency =
                                                        when (state.selectOne?.getSelected()) {
                                                            getStringBlocking(Res.string.daily) -> DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY
                                                            getStringBlocking(Res.string.weekly) -> DataStoreManager.AUTO_BACKUP_FREQUENCY_WEEKLY
                                                            getStringBlocking(Res.string.monthly) -> DataStoreManager.AUTO_BACKUP_FREQUENCY_MONTHLY
                                                            else -> DataStoreManager.AUTO_BACKUP_FREQUENCY_DAILY
                                                        }
                                                    viewModel.setAutoBackupFrequency(frequency)
                                                },
                                            dismiss = getStringBlocking(Res.string.cancel),
                                        ),
                                    )
                                },
                            )
                            SettingItem(
                                title = stringResource(Res.string.keep_backups),
                                subtitle = stringResource(Res.string.keep_backups_format, "$autoBackupMaxFiles"),
                                onClick = {
                                    viewModel.setAlertData(
                                        SettingAlertState(
                                            title = getStringBlocking(Res.string.keep_backups),
                                            selectOne =
                                                SettingAlertState.SelectData(
                                                    listSelect =
                                                        listOf(
                                                            (autoBackupMaxFiles == 3) to "3",
                                                            (autoBackupMaxFiles == 5) to "5",
                                                            (autoBackupMaxFiles == 10) to "10",
                                                            (autoBackupMaxFiles == 15) to "15",
                                                        ),
                                                ),
                                            confirm =
                                                getStringBlocking(Res.string.change) to { state ->
                                                    val maxFiles = state.selectOne?.getSelected()?.toIntOrNull() ?: 5
                                                    viewModel.setAutoBackupMaxFiles(maxFiles)
                                                },
                                            dismiss = getStringBlocking(Res.string.cancel),
                                        ),
                                    )
                                },
                            )
                            SettingItem(
                                title = stringResource(Res.string.last_backup),
                                subtitle =
                                    if (autoBackupLastTime == 0L) {
                                        stringResource(Res.string.never)
                                    } else {
                                        DateTimeFormatter
                                            .ofPattern("yyyy-MM-dd HH:mm:ss")
                                            .withZone(ZoneId.systemDefault())
                                            .format(Instant.ofEpochMilli(autoBackupLastTime))
                                    },
                            )
                        }
                    }
                }
                SettingItem(
                    title = stringResource(Res.string.backup),
                    subtitle = stringResource(Res.string.save_all_your_playlist_data),
                    onClick = {
                        coroutineScope.launch {
                            backupLauncher.launch()
                        }
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.restore_your_data),
                    subtitle = stringResource(Res.string.restore_your_saved_data),
                    onClick = {
                        coroutineScope.launch {
                            restoreLauncher.launch()
                        }
                    },
                )
            }
        }
        if (matchQuery(aboutLabel)) item(key = "about_us") {
            SettingSection(
                title = stringResource(Res.string.about_us),
                icon = Icons.Outlined.Info,
            ) {
                SettingItem(
                    title = stringResource(Res.string.version),
                    subtitle = stringResource(Res.string.version_format, VersionManager.getVersionName()),
                    onClick = {
                        navController.navigate(CreditDestination)
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.update_channel),
                    subtitle = "SakayoriMusic GitHub Release",
                    onClick = {
                        viewModel.setAlertData(
                            SettingAlertState(
                                title = getStringBlocking(Res.string.update_channel),
                                selectOne =
                                    SettingAlertState.SelectData(
                                        listSelect =
                                            listOf(
                                                (updateChannel == DataStoreManager.GITHUB) to "SakayoriMusic GitHub Release",
                                            ),
                                    ),
                                confirm =
                                    getStringBlocking(Res.string.change) to { state ->
                                        viewModel.setUpdateChannel(
                                            DataStoreManager.GITHUB
                                        )
                                    },
                                dismiss = getStringBlocking(Res.string.cancel),
                            ),
                        )
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.check_for_update),
                    subtitle = checkForUpdateSubtitle,
                    onClick = {
                        sharedViewModel.checkForUpdate()
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.author),
                    subtitle = stringResource(Res.string.sakayori_dev),
                    onClick = {
                        uriHandler.openUri("https://github.com/Sakayorii/sakayori-music")
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.buy_me_a_coffee),
                    subtitle = stringResource(Res.string.donation),
                    onClick = {
                        uriHandler.openUri("https://ko-fi.com/sakayori")
                    },
                )
                SettingItem(
                    title = stringResource(Res.string.third_party_libraries),
                    subtitle = stringResource(Res.string.description_and_licenses),
                    onClick = {
                        showThirdPartyLibraries = true
                    },
                )
            }
        }
        item(key = "end") {
            EndOfPage()
        }
    }
    val basisAlertData by viewModel.basicAlertData.collectAsStateWithLifecycle()
    if (basisAlertData != null) {
        val alertBasicState = basisAlertData ?: return
        AlertDialog(
            onDismissRequest = { viewModel.setBasicAlertData(null) },
            title = {
                Text(
                    text = alertBasicState.title,
                    style = typo().titleSmall,
                )
            },
            text = {
                if (alertBasicState.message != null) {
                    Text(text = alertBasicState.message)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        alertBasicState.confirm.second.invoke()
                        viewModel.setBasicAlertData(null)
                    },
                ) {
                    Text(text = alertBasicState.confirm.first)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setBasicAlertData(null)
                    },
                ) {
                    Text(text = alertBasicState.dismiss)
                }
            },
        )
    }
    if (showYouTubeAccountDialog) {
        BasicAlertDialog(
            onDismissRequest = { },
            modifier = Modifier.wrapContentSize(),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = Color(0xFF242424),
                tonalElevation = AlertDialogDefaults.TonalElevation,
                shadowElevation = 1.dp,
            ) {
                val googleAccounts by viewModel.googleAccounts.collectAsStateWithLifecycle(
                    minActiveState = Lifecycle.State.RESUMED,
                )
                LazyColumn(modifier = Modifier.padding(8.dp)) {
                    item {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                        ) {
                            IconButton(
                                onClick = { showYouTubeAccountDialog = false },
                                colors =
                                    IconButtonDefaults.iconButtonColors().copy(
                                        contentColor = Color.White,
                                    ),
                                modifier =
                                    Modifier
                                        .align(Alignment.CenterStart)
                                        .fillMaxHeight(),
                            ) {
                                Icon(Icons.Outlined.Close, null, tint = Color.White)
                            }
                            Text(
                                stringResource(Res.string.youtube_account),
                                style = typo().titleMedium,
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .wrapContentHeight(align = Alignment.CenterVertically)
                                        .wrapContentWidth(),
                            )
                        }
                    }
                    if (googleAccounts is LocalResource.Success) {
                        val data = googleAccounts.data
                        if (data.isNullOrEmpty()) {
                            item {
                                Text(
                                    stringResource(Res.string.no_account),
                                    style = typo().bodyMedium,
                                    textAlign = TextAlign.Center,
                                    modifier =
                                        Modifier
                                            .padding(12.dp)
                                            .fillMaxWidth(),
                                )
                            }
                        } else {
                            items(data) {
                                Row(
                                    modifier =
                                        Modifier
                                            .padding(vertical = 8.dp)
                                            .clickable {
                                                viewModel.setUsedAccount(it)
                                            },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Spacer(Modifier.width(24.dp))
                                    AsyncImage(
                                        model =
                                            ImageRequest
                                                .Builder(LocalPlatformContext.current)
                                                .data(it.thumbnailUrl)
                                                .crossfade(550)
                                                .build(),
                                        placeholder = painterResource(Res.drawable.baseline_people_alt_24),
                                        error = painterResource(Res.drawable.baseline_people_alt_24),
                                        contentDescription = it.name,
                                        modifier =
                                            Modifier
                                                .size(48.dp)
                                                .clip(CircleShape),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(it.name, style = typo().labelMedium, color = white)
                                        Text(it.email, style = typo().bodySmall)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    AnimatedVisibility(it.isUsed) {
                                        Text(
                                            stringResource(Res.string.signed_in),
                                            style = typo().bodySmall,
                                            maxLines = 2,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.widthIn(0.dp, 64.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(24.dp))
                                }
                            }
                        }
                    } else {
                        item {
                            CenterLoadingBox(
                                Modifier
                                    .fillMaxWidth()
                                    .height(80.dp),
                            )
                        }
                    }
                    item {
                        Column {
                            ActionButton(
                                icon = painterResource(Res.drawable.baseline_people_alt_24),
                                text = Res.string.guest,
                            ) {
                                viewModel.setUsedAccount(null)
                                showYouTubeAccountDialog = false
                            }
                            ActionButton(
                                icon = painterResource(Res.drawable.baseline_close_24),
                                text = Res.string.log_out,
                            ) {
                                viewModel.setBasicAlertData(
                                    SettingBasicAlertState(
                                        title = getStringBlocking(Res.string.warning),
                                        message = getStringBlocking(Res.string.log_out_warning),
                                        confirm =
                                            getStringBlocking(Res.string.log_out) to {
                                                viewModel.logOutAllYouTube()
                                                showYouTubeAccountDialog = false
                                            },
                                        dismiss = getStringBlocking(Res.string.cancel),
                                    ),
                                )
                            }
                            ActionButton(
                                icon = painterResource(Res.drawable.baseline_playlist_add_24),
                                text = Res.string.add_an_account,
                            ) {
                                showYouTubeAccountDialog = false
                                navController.navigate(LoginDestination)
                            }
                        }
                    }
                }
            }
        }
    }
    val alertData by viewModel.alertData.collectAsStateWithLifecycle()
    if (alertData != null) {
        val alertState = alertData ?: return
        AlertDialog(
            onDismissRequest = { viewModel.setAlertData(null) },
            title = {
                Text(
                    text = alertState.title,
                    style = typo().titleSmall,
                )
            },
            text = {
                if (alertState.message != null) {
                    Column {
                        Text(text = alertState.message)
                        if (alertState.textField != null) {
                            val verify =
                                alertState.textField.verifyCodeBlock?.invoke(
                                    alertState.textField.value,
                                ) ?: (true to null)
                            TextField(
                                value = alertState.textField.value,
                                onValueChange = {
                                    viewModel.setAlertData(
                                        alertState.copy(
                                            textField =
                                                alertState.textField.copy(
                                                    value = it,
                                                ),
                                        ),
                                    )
                                },
                                isError = !verify.first,
                                label = { Text(text = alertState.textField.label) },
                                supportingText = {
                                    if (!verify.first) {
                                        Text(
                                            modifier = Modifier.fillMaxWidth(),
                                            text = verify.second ?: "",
                                            color = DarkColors.error,
                                        )
                                    }
                                },
                                trailingIcon = {
                                    if (!verify.first) {
                                        Icons.Outlined.Error
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            vertical = 6.dp,
                                        ),
                            )
                        }
                    }
                } else if (alertState.selectOne != null) {
                    LazyColumn(
                        Modifier
                            .padding(vertical = 6.dp)
                            .heightIn(0.dp, 500.dp),
                    ) {
                        items(alertState.selectOne.listSelect) { item ->
                            val onSelect = {
                                viewModel.setAlertData(
                                    alertState.copy(
                                        selectOne =
                                            alertState.selectOne.copy(
                                                listSelect =
                                                    alertState.selectOne.listSelect.toMutableList().map {
                                                        if (it == item) {
                                                            true to it.second
                                                        } else {
                                                            false to it.second
                                                        }
                                                    },
                                            ),
                                    ),
                                )
                            }
                            Row(
                                Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onSelect.invoke()
                                    }.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = item.first,
                                    onClick = {
                                        onSelect.invoke()
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = item.second,
                                    style = typo().bodyMedium,
                                    maxLines = 1,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .wrapContentHeight(align = Alignment.CenterVertically)
                                            .basicMarquee(
                                                iterations = Int.MAX_VALUE,
                                                animationMode = MarqueeAnimationMode.Immediately,
                                            ).focusable(),
                                )
                            }
                        }
                    }
                } else if (alertState.multipleSelect != null) {
                    LazyColumn(
                        Modifier.padding(vertical = 6.dp),
                    ) {
                        items(alertState.multipleSelect.listSelect) { item ->
                            val onCheck = {
                                viewModel.setAlertData(
                                    alertState.copy(
                                        multipleSelect =
                                            alertState.multipleSelect.copy(
                                                listSelect =
                                                    alertState.multipleSelect.listSelect.toMutableList().map {
                                                        if (it == item) {
                                                            !it.first to it.second
                                                        } else {
                                                            it
                                                        }
                                                    },
                                            ),
                                    ),
                                )
                            }
                            Row(
                                Modifier
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onCheck.invoke()
                                    }.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = item.first,
                                    onCheckedChange = {
                                        onCheck.invoke()
                                    },
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(text = item.second, style = typo().bodyMedium, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        alertState.confirm.second.invoke(alertState)
                        viewModel.setAlertData(null)
                    },
                    enabled =
                        if (alertState.textField?.verifyCodeBlock != null) {
                            alertState.textField.verifyCodeBlock
                                .invoke(
                                    alertState.textField.value,
                                ).first
                        } else {
                            true
                        },
                ) {
                    Text(text = alertState.confirm.first)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.setAlertData(null)
                    },
                ) {
                    Text(text = alertState.dismiss)
                }
            },
        )
    }

    if (showThirdPartyLibraries) {
        val libraries by produceLibraries {
            Res.readBytes("files/aboutlibraries.json").decodeToString()
        }
        val lazyListState = rememberLazyListState()
        val canScrollBackward by remember {
            derivedStateOf {
                lazyListState.canScrollBackward
            }
        }
        val sheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = {
                    !canScrollBackward
                },
            )
        val coroutineScope = rememberCoroutineScope()
        ModalBottomSheet(
            modifier =
                Modifier
                    .fillMaxHeight(),
            onDismissRequest = {
                showThirdPartyLibraries = false
            },
            containerColor = Color.Black,
            dragHandle = {},
            scrimColor = Color.Black,
            sheetState = sheetState,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
            shape = RectangleShape,
        ) {
            LibrariesContainer(
                libraries?.copy(
                    libraries =
                        libraries
                            ?.libraries
                            ?.distinctBy {
                                it.name
                            }?.toImmutableList() ?: emptyList<Library>().toImmutableList(),
                ),
                Modifier.fillMaxSize(),
                lazyListState = lazyListState,
                showDescription = true,
                contentPadding = innerPadding,
                typography = typo(),
                colors =
                    LibraryDefaults.libraryColors(
                        licenseChipColors =
                            object : ChipColors {
                                override val containerColor: Color
                                    get() = Color.DarkGray
                                override val contentColor: Color
                                    get() = Color.White
                            },
                    ),
                header = {
                    item {
                        TopAppBar(
                            windowInsets = WindowInsets(0, 0, 0, 0),
                            title = {
                                Text(
                                    text =
                                        stringResource(
                                            Res.string.third_party_libraries,
                                        ),
                                    style = typo().titleMedium,
                                )
                            },
                            navigationIcon = {
                                Box(Modifier.padding(horizontal = 5.dp)) {
                                    RippleIconButton(
                                        Res.drawable.baseline_arrow_back_ios_new_24,
                                        Modifier
                                            .size(32.dp),
                                        true,
                                    ) {
                                        coroutineScope.launch {
                                            sheetState.hide()
                                            showThirdPartyLibraries = false
                                        }
                                    }
                                }
                            },
                        )
                    }
                },
            )
        }
    }

    TopAppBar(
        title = {
            Text(
                text =
                    stringResource(
                        Res.string.settings,
                    ),
                style = typo().titleMedium,
            )
        },
        navigationIcon = {
            Box(Modifier.padding(horizontal = 5.dp)) {
                RippleIconButton(
                    Res.drawable.baseline_arrow_back_ios_new_24,
                    Modifier
                        .size(32.dp),
                    true,
                ) {
                    navController.navigateUp()
                }
            }
        },
        modifier =
            Modifier
                .hazeEffect(hazeState, style = HazeMaterials.ultraThin()) {
                    blurEnabled = isBlurEnabled
                },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
            ),
    )
}
