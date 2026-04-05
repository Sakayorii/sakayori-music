package com.sakayori.music.ui.screen.login

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.LogoDev
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.sakayori.common.Config
import com.sakayori.music.expect.ui.PlatformWebView
import com.sakayori.music.expect.ui.createWebViewCookieManager
import com.sakayori.music.expect.ui.rememberWebViewState
import com.sakayori.music.extension.getStringBlocking
import com.sakayori.music.ui.component.DevCookieLogInBottomSheet
import com.sakayori.music.ui.component.DevLogInBottomSheet
import com.sakayori.music.ui.component.DevLogInType
import com.sakayori.music.ui.component.RippleIconButton
import com.sakayori.music.ui.theme.typo
import com.sakayori.music.viewModel.LogInViewModel
import com.sakayori.music.viewModel.SettingsViewModel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import dev.chrisbanes.haze.rememberHazeState
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.baseline_arrow_back_ios_new_24
import com.sakayori.music.generated.resources.log_in_to_spotify
import com.sakayori.music.generated.resources.login_success

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun SpotifyLoginScreen(
    innerPadding: PaddingValues,
    navController: NavController,
    viewModel: LogInViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    hideBottomNavigation: () -> Unit,
    showBottomNavigation: () -> Unit,
) {
    val hazeState = rememberHazeState()
    val spotifyStatus by viewModel.spotifyStatus.collectAsStateWithLifecycle()

    val fullSpotifyCookies by viewModel.fullSpotifyCookies.collectAsStateWithLifecycle()

    var devLoginSheet by rememberSaveable {
        mutableStateOf(false)
    }

    var showCookiesBottomSheet by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        hideBottomNavigation()
    }

    DisposableEffect(Unit) {
        onDispose {
            showBottomNavigation()
        }
    }

    LaunchedEffect(spotifyStatus) {
        if (spotifyStatus) {
            settingsViewModel.setSpotifyLogIn(true)
            viewModel.makeToast(getString(Res.string.login_success))
            navController.navigateUp()
        }
    }

    val state = rememberWebViewState()
    val cookieManager = createWebViewCookieManager()

    Box(modifier = Modifier.fillMaxSize().hazeSource(state = hazeState)) {
        Column {
            Spacer(
                Modifier
                    .size(
                        innerPadding.calculateTopPadding() + 64.dp,
                    ),
            )
            PlatformWebView(
                state,
                Config.SPOTIFY_LOG_IN_URL,
                aboveContent = {
                    FloatingActionButton(
                        onClick = {
                            showCookiesBottomSheet = true
                        },
                        containerColor = Color(0xFF40D96A),
                        modifier =
                            Modifier
                                .align(
                                    Alignment.BottomStart,
                                ).padding(innerPadding)
                                .padding(
                                    25.dp,
                                ),
                    ) {
                        Icon(
                            Icons.Default.Cookie,
                            "Cookies",
                        )
                    }
                    if (devLoginSheet) {
                        DevLogInBottomSheet(
                            onDismiss = {
                                devLoginSheet = false
                            },
                            onDone = { spdc, _ ->
                                devLoginSheet = false
                                val spdcText = "sp_dc=$spdc"
                                viewModel.saveSpotifySpdc(spdcText)
                                viewModel.makeToast(getStringBlocking(Res.string.login_success))
                                navController.navigateUp()
                            },
                            type = DevLogInType.Spotify,
                        )
                    }

                    if (showCookiesBottomSheet) {
                        DevCookieLogInBottomSheet(
                            onDismiss = {
                                showCookiesBottomSheet = false
                            },
                            type = DevLogInType.Spotify,
                            cookies = fullSpotifyCookies,
                        )
                    }
                },
            ) { url ->
                val cookie = cookieManager.getCookie(url)
                cookie.takeIf {
                    it.isNotEmpty()
                }?.let { cookie ->
                    val cookies =
                        cookie.split("; ").map {
                            val (key, value) = it.split("=")
                            key to value
                        }
                    viewModel.setFullSpotifyCookies(cookies)
                }
                if (Regex("^https://accounts\\.spotify\\.com/[a-z]{2}(-[a-zA-Z]{2})?/status$").matches(url)) {
                    cookie
                        .takeIf {
                            it.isNotEmpty()
                        }?.let {
                            viewModel.saveSpotifySpdc(it)
                        }
                    cookieManager.removeAllCookies()
                }
            }
        }

        TopAppBar(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .hazeEffect(state = hazeState, style = HazeMaterials.ultraThin()) {
                        blurEnabled = true
                    },
            title = {
                Text(
                    text = stringResource(Res.string.log_in_to_spotify),
                    style = typo().titleMedium,
                )
            },
            navigationIcon = {
                Box(Modifier.padding(horizontal = 5.dp)) {
                    RippleIconButton(
                        Res.drawable.baseline_arrow_back_ios_new_24,
                        Modifier.size(32.dp),
                        true,
                    ) {
                        navController.navigateUp()
                    }
                }
            },
            actions = {
                IconButton(
                    onClick = {
                        devLoginSheet = true
                    },
                ) {
                    Icon(
                        Icons.Default.LogoDev,
                        "Developer Mode",
                    )
                }
            },
            colors =
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                ),
        )
    }
}
