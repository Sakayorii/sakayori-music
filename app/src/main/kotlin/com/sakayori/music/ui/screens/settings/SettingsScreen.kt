/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.screens.settings

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.sakayori.music.BuildConfig
import com.sakayori.music.LocalPlayerAwareWindowInsets
import com.sakayori.music.R
import com.sakayori.music.ui.component.IconButton
import com.sakayori.music.ui.component.Material3SettingsGroup
import com.sakayori.music.ui.component.Material3SettingsItem
import com.sakayori.music.ui.component.ReleaseNotesCard
import com.sakayori.music.ui.utils.backToMain
import com.sakayori.music.utils.Updater
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val hasAndroidAuto = remember {
        try {
            context.packageManager.getPackageInfo(
                "com.google.android.projection.gearhead", 0
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    Column(
        Modifier
            .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(
            Modifier.windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current.only(
                    WindowInsetsSides.Top
                )
            )
        )

        // User Interface Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_ui),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.palette),
                    title = { Text(stringResource(R.string.appearance)) },
                    onClick = { navController.navigate("settings/appearance") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Player & Content Section (moved up and combined with content)
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_player_content),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.play),
                    title = { Text(stringResource(R.string.player_and_audio)) },
                    onClick = { navController.navigate("settings/player") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.radio),
                    title = { Text(stringResource(R.string.stream_sources)) },
                    onClick = { navController.navigate("settings/stream_sources") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.language),
                    title = { Text(stringResource(R.string.content)) },
                    onClick = { navController.navigate("settings/content") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.translate),
                    title = { Text(stringResource(R.string.ai_lyrics_translation)) },
                    onClick = { navController.navigate("settings/ai") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Android Auto Section — only shown if Android Auto is installed
        if (hasAndroidAuto) {
            Material3SettingsGroup(
                title = "Android Auto",
                items = listOf(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.ic_android_auto),
                        title = { Text(stringResource(R.string.android_auto)) },
                        onClick = { navController.navigate("settings/android_auto") }
                    )
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Privacy & Security Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_privacy),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.security),
                    title = { Text(stringResource(R.string.privacy)) },
                    onClick = { navController.navigate("settings/privacy") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Storage & Data Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_storage),
            items = listOf(
                Material3SettingsItem(
                    icon = painterResource(R.drawable.storage),
                    title = { Text(stringResource(R.string.storage)) },
                    onClick = { navController.navigate("settings/storage") }
                ),
                Material3SettingsItem(
                    icon = painterResource(R.drawable.restore),
                    title = { Text(stringResource(R.string.backup_restore)) },
                    onClick = { navController.navigate("settings/backup_restore") }
                )
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // System & About Section
        Material3SettingsGroup(
            title = stringResource(R.string.settings_section_system),
            items = buildList {
                if (isAndroid12OrLater) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.link),
                            title = { Text(stringResource(R.string.default_links)) },
                            onClick = {
                                try {
                                    val intent = Intent(
                                        Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS,
                                        "package:${context.packageName}".toUri()
                                    )
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    when (e) {
                                        is ActivityNotFoundException -> {
                                            Toast.makeText(
                                                context,
                                                R.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }

                                        is SecurityException -> {
                                            Toast.makeText(
                                                context,
                                                R.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }

                                        else -> {
                                            Toast.makeText(
                                                context,
                                                R.string.open_app_settings_error,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }
                        )
                    )
                }
                if (BuildConfig.UPDATER_AVAILABLE) {
                    add(
                        Material3SettingsItem(
                            icon = painterResource(R.drawable.update),
                            title = { Text(stringResource(R.string.updater)) },
                            onClick = { navController.navigate("settings/updater") }
                        )
                    )
                }
                val showChangelog = com.sakayori.music.LocalChangelogState.current
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.newspaper),
                        title = { Text(stringResource(R.string.changelog)) },
                        onClick = { showChangelog.value = true }
                    )
                )
                add(
                    Material3SettingsItem(
                        icon = painterResource(R.drawable.info),
                        title = { Text(stringResource(R.string.about)) },
                        onClick = { navController.navigate("settings/about") }
                    )
                )
                if (BuildConfig.UPDATER_AVAILABLE && Updater.isUpdateAvailable(BuildConfig.BASE_VERSION_NAME, latestVersionName)) {
                    val releaseInfo = Updater.getCachedLatestRelease()
                    val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }

                    if (downloadUrl != null) {
                        add(
                            Material3SettingsItem(
                                icon = painterResource(R.drawable.update),
                                title = { 
                                    Text(
                                        text = stringResource(R.string.new_version_available),
                                    )
                                },
                                description = {
                                    Text(
                                        text = latestVersionName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                showBadge = true,
                                onClick = { uriHandler.openUri(downloadUrl) }
                            )
                        )
                    }
                }
            }
        )
    if (BuildConfig.UPDATER_AVAILABLE && Updater.isUpdateAvailable(BuildConfig.BASE_VERSION_NAME, latestVersionName)) {
            Spacer(modifier = Modifier.height(16.dp))
            ReleaseNotesCard()
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    TopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            IconButton(
                onClick = navController::navigateUp,
                onLongClick = navController::backToMain
            ) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = null
                )
            }
        }
    )

    WarmSettingsIcons()
}

private val settingsIconWarmup =
    listOf(
        R.drawable.about_icon,
        R.drawable.album,
        R.drawable.arrow_forward,
        R.drawable.artist,
        R.drawable.backup,
        R.drawable.baseline_event_repeat_24,
        R.drawable.bluetooth,
        R.drawable.buymeacoffee,
        R.drawable.cached,
        R.drawable.cast,
        R.drawable.check,
        R.drawable.clear_all,
        R.drawable.close,
        R.drawable.crop,
        R.drawable.delete_history,
        R.drawable.discord,
        R.drawable.discover_tune,
        R.drawable.download,
        R.drawable.drag_handle,
        R.drawable.edit,
        R.drawable.explicit,
        R.drawable.explore_outlined,
        R.drawable.fast_forward,
        R.drawable.favorite,
        R.drawable.fullscreen,
        R.drawable.github,
        R.drawable.gradient,
        R.drawable.graphic_eq,
        R.drawable.grid_view,
        R.drawable.group_outlined,
        R.drawable.hide_image,
        R.drawable.history,
        R.drawable.home_outlined,
        R.drawable.ic_logo_oval,
        R.drawable.key,
        R.drawable.language_korean_latin,
        R.drawable.linear_scale,
        R.drawable.location_on,
        R.drawable.lyrics,
        R.drawable.manage_search,
        R.drawable.more_time,
        R.drawable.music_note,
        R.drawable.nav_bar,
        R.drawable.notification,
        R.drawable.offline,
        R.drawable.person,
        R.drawable.playlist_add,
        R.drawable.queue_music,
        R.drawable.refresh,
        R.drawable.repeat,
        R.drawable.screenshot,
        R.drawable.search_off,
        R.drawable.settings,
        R.drawable.shuffle,
        R.drawable.similar,
        R.drawable.skip_next,
        R.drawable.sliders,
        R.drawable.slow_motion_video,
        R.drawable.speed,
        R.drawable.stats,
        R.drawable.swipe,
        R.drawable.sync,
        R.drawable.tab,
        R.drawable.time_auto,
        R.drawable.timer,
        R.drawable.timer_arrow_down,
        R.drawable.trending_up,
        R.drawable.tune,
        R.drawable.volume_off_pause,
        R.drawable.volume_up,
        R.drawable.wifi_proxy,
    )

@Composable
private fun WarmSettingsIcons() {
    var warmed by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        delay(400)
        while (warmed < settingsIconWarmup.size) {
            withFrameNanos { }
            warmed = (warmed + 6).coerceAtMost(settingsIconWarmup.size)
        }
    }
    if (warmed > 0) {
        Box(Modifier.size(0.dp)) {
            settingsIconWarmup.take(warmed).forEach { painterResource(it) }
        }
    }
}
