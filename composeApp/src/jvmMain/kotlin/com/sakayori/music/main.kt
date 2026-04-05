package com.sakayori.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.kdroid.composetray.tray.api.Tray
import com.sakayori.data.di.loader.loadAllModules
import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.domain.mediaservice.handler.MediaPlayerHandler
import com.sakayori.domain.mediaservice.handler.ToastType
import com.sakayori.music.di.viewModelModule
import com.sakayori.music.ui.component.CustomTitleBar
import com.sakayori.music.ui.mini_player.MiniPlayerManager
import com.sakayori.music.ui.mini_player.MiniPlayerWindow
import com.sakayori.music.utils.VersionManager
import com.sakayori.music.viewModel.SharedViewModel
import com.sakayori.music.viewModel.changeLanguageNative
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import multiplatform.network.cmptoast.ToastHost
import multiplatform.network.cmptoast.showToast
import okhttp3.OkHttpClient
import okio.FileSystem
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.inject
import org.koin.mp.KoinPlatform.getKoin
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.app_name
import com.sakayori.music.generated.resources.circle_app_icon
import com.sakayori.music.generated.resources.close_miniplayer
import com.sakayori.music.generated.resources.explicit_content_blocked
import com.sakayori.music.generated.resources.open_app
import com.sakayori.music.generated.resources.open_miniplayer
import com.sakayori.music.generated.resources.quit_app
import com.sakayori.music.generated.resources.time_out_check_internet_connection_or_change_piped_instance_in_settings
import java.io.File
import kotlin.system.exitProcess
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
fun main(args: Array<String>) {
    try {
        println("SakayoriMusic starting...")
        
        System.setProperty("compose.swing.render.on.graphics", "true")
        System.setProperty("compose.interop.blending", "true")
        System.setProperty("compose.layers.type", "COMPONENT")

        val osName = System.getProperty("os.name", "").lowercase()
        val subDir = when {
            osName.contains("win") -> "windows"
            osName.contains("mac") -> "macos"
            else -> "linux"
        }
        
        val packagedPath = System.getProperty("compose.application.resources.dir")
        val appDir = System.getProperty("user.dir")
        
        val potentialPaths = listOf(
            File(packagedPath ?: "", subDir),
            File(appDir, "app/$subDir"),
            File(appDir, subDir),
            File("vlc-natives/$subDir")
        )

        val vlcPath = potentialPaths.firstOrNull { it.exists() && it.isDirectory }?.absolutePath
        
        if (vlcPath != null) {
            println("VLC natives found at: $vlcPath")
            System.setProperty("jna.library.path", vlcPath)
        } else {
            println("WARNING: VLC native libraries not found in any of: ${potentialPaths.joinToString { it.absolutePath }}")
        }

        startKoin {
            loadKoinModules(listOf(viewModelModule))
            loadAllModules()
        }

        val language = runBlocking(Dispatchers.IO) {
            getKoin()
                .get<DataStoreManager>()
                .language
                .first()
                .take(2)
        }
        changeLanguageNative(language)

        VersionManager.initialize()
        if (BuildKonfig.sentryDsn.isNotEmpty()) {
            Sentry.init { options ->
                options.dsn = BuildKonfig.sentryDsn
                options.release = "sakayorimusic-desktop@${VersionManager.getVersionName()}"
                options.setDiagnosticLevel(SentryLevel.ERROR)
            }
        }

        val mediaPlayerHandler by inject<MediaPlayerHandler>(MediaPlayerHandler::class.java)
        mediaPlayerHandler.showToast = { type ->
            showToast(
                when (type) {
                    ToastType.ExplicitContent -> {
                        runBlocking(Dispatchers.Default) { getString(Res.string.explicit_content_blocked) }
                    }
                    is ToastType.PlayerError -> {
                        runBlocking(Dispatchers.Default) { getString(Res.string.time_out_check_internet_connection_or_change_piped_instance_in_settings, type.error) }
                    }
                }
            )
        }

        application {
            val windowState = rememberWindowState(
                size = DpSize(1500.dp, 860.dp),
            )
            var isVisible by remember { mutableStateOf(true) }

            val openAppString = stringResource(Res.string.open_app)
            val quitAppString = stringResource(Res.string.quit_app)
            val openMiniPlayer = stringResource(Res.string.open_miniplayer)
            val closeMiniPlayer = stringResource(Res.string.close_miniplayer)

            Tray(
                icon = painterResource(Res.drawable.circle_app_icon),
                tooltip = stringResource(Res.string.app_name),
                primaryAction = {
                    isVisible = true
                    windowState.isMinimized = false
                },
            ) {
                if (!isVisible) {
                    Item(openAppString) {
                        isVisible = true
                        windowState.isMinimized = false
                    }
                }
                if (MiniPlayerManager.isOpen) {
                    Item(closeMiniPlayer) {
                        MiniPlayerManager.isOpen = false
                    }
                } else {
                    Item(openMiniPlayer) {
                        MiniPlayerManager.isOpen = true
                    }
                }
                Divider()
                Item(quitAppString) {
                    mediaPlayerHandler.release()
                    exitApplication()
                }
            }

            Window(
                onCloseRequest = { isVisible = false },
                title = stringResource(Res.string.app_name),
                icon = painterResource(Res.drawable.circle_app_icon),
                undecorated = false,
                transparent = false,
                state = windowState,
                visible = isVisible,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    CustomTitleBar(
                        title = stringResource(Res.string.app_name),
                        windowState = windowState,
                        window = window,
                        onCloseRequest = { isVisible = false },
                    )

                    val context = LocalPlatformContext.current
                    setSingletonImageLoaderFactory {
                        ImageLoader.Builder(context)
                            .components {
                                add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() }))
                            }
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .diskCache {
                                DiskCache.Builder()
                                    .directory(System.getProperty("java.io.tmpdir").toPath() / "image_cache")
                                    .maxSizeBytes(512L * 1024 * 1024)
                                    .build()
                            }
                            .crossfade(true)
                            .build()
                    }
                    App()
                    ToastHost()
                }
            }

            if (MiniPlayerManager.isOpen) {
                val sharedViewModel = getKoin().get<SharedViewModel>()
                MiniPlayerWindow(
                    sharedViewModel = sharedViewModel,
                    onCloseRequest = { MiniPlayerManager.isOpen = false },
                )
            }
        }
    } catch (e: Exception) {
        println("FATAL ERROR: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    } catch (e: Error) {
        println("FATAL ERROR (Native): ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}
