package com.sakayori.music

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
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
import com.sakayori.music.extension.getStringBlocking
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
        if (!com.sakayori.music.utils.SingleInstance.acquire()) {
            com.sakayori.music.utils.SingleInstance.signalExisting()
            exitProcess(0)
        }

        CrashDialog.install()

        System.setProperty("compose.swing.render.on.graphics", "true")
        System.setProperty("compose.interop.blending", "true")
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "off")

        val osName = System.getProperty("os.name", "").lowercase()
        val subDir = when {
            osName.contains("win") -> "windows"
            osName.contains("mac") -> "macos"
            else -> "linux"
        }

        val resourcesDir = System.getProperty("compose.application.resources.dir")
        val vlcPath = if (resourcesDir != null) {
            File(resourcesDir, subDir).absolutePath
        } else {
            File("vlc-natives/$subDir").absolutePath
        }
        System.setProperty("jna.library.path", vlcPath)

        val splash = SplashScreen()
        splash.show()

        splash.updateStatus("Loading modules...")
        startKoin {
            loadKoinModules(listOf(viewModelModule))
            loadAllModules()
        }

        Runtime.getRuntime().addShutdownHook(
            Thread {
                try {
                    val handler = getKoin().getOrNull<MediaPlayerHandler>()
                    handler?.release()
                    Thread.sleep(300)
                } catch (_: Throwable) {
                }
                try {
                    Sentry.close()
                } catch (_: Throwable) {
                }
            }.apply {
                name = "SakayoriShutdownHook"
                isDaemon = false
            },
        )

        splash.updateStatus("Initializing version manager...")
        VersionManager.initialize()

        splash.updateStatus("Initializing media player...")
        val mediaPlayerHandler by inject<MediaPlayerHandler>(MediaPlayerHandler::class.java)

        splash.updateStatus("Starting background services...")
        Thread {
            try {
                val lang = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                    getKoin().get<DataStoreManager>().language.first().take(2)
                }
                changeLanguageNative(lang)
                if (BuildKonfig.sentryDsn.isNotEmpty()) {
                    Sentry.init { options ->
                        options.dsn = BuildKonfig.sentryDsn
                        options.release = "sakayorimusic-desktop@${VersionManager.getVersionName()}"
                        options.setDiagnosticLevel(SentryLevel.ERROR)
                    }
                }
                Thread.sleep(2000)
                getKoin().get<SharedViewModel>().checkForUpdate()
            } catch (_: Throwable) {
            }
        }.apply {
            isDaemon = true
            name = "DeferredInit"
        }.start()

        splash.updateStatus("Configuring player...")
        mediaPlayerHandler.showToast = { type ->
            showToast(
                when (type) {
                    ToastType.ExplicitContent -> {
                        getStringBlocking(Res.string.explicit_content_blocked)
                    }
                    is ToastType.PlayerError -> {
                        getStringBlocking(Res.string.time_out_check_internet_connection_or_change_piped_instance_in_settings, type.error)
                    }
                }
            )
        }

        splash.updateStatus("Done!")
        Thread.sleep(200)
        splash.close()

        application {
            val windowState = rememberWindowState(
                size = DpSize(1500.dp, 860.dp),
            )
            var isVisible by remember { mutableStateOf(true) }

            LaunchedEffect(Unit) {
                com.sakayori.music.utils.SingleInstance.setOnFocusRequested {
                    javax.swing.SwingUtilities.invokeLater {
                        isVisible = true
                        windowState.isMinimized = false
                    }
                }
            }

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

            val isMacOS = remember { System.getProperty("os.name", "").lowercase().contains("mac") }

            Window(
                onCloseRequest = { isVisible = false },
                title = stringResource(Res.string.app_name),
                icon = painterResource(Res.drawable.circle_app_icon),
                undecorated = !isMacOS,
                transparent = false,
                state = windowState,
                visible = isVisible,
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!isMacOS) {
                        CustomTitleBar(
                            title = stringResource(Res.string.app_name),
                            windowState = windowState,
                            window = window,
                            onCloseRequest = { isVisible = false },
                        )
                    }

                    val context = LocalPlatformContext.current
                    var lowResourceMode by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        lowResourceMode = getKoin().get<DataStoreManager>().lowResourceMode.first() == "TRUE"
                    }
                    val memoryCacheMaxBytes = if (lowResourceMode) 32L * 1024 * 1024 else 128L * 1024 * 1024
                    val diskCacheMaxBytes = if (lowResourceMode) 128L * 1024 * 1024 else 512L * 1024 * 1024
                    setSingletonImageLoaderFactory {
                        ImageLoader.Builder(context)
                            .components {
                                add(OkHttpNetworkFetcherFactory(callFactory = { OkHttpClient() }))
                            }
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .memoryCache {
                                coil3.memory.MemoryCache.Builder()
                                    .maxSizeBytes(memoryCacheMaxBytes)
                                    .build()
                            }
                            .diskCache {
                                DiskCache.Builder()
                                    .directory(System.getProperty("java.io.tmpdir").toPath() / "image_cache")
                                    .maxSizeBytes(diskCacheMaxBytes)
                                    .build()
                            }
                            .crossfade(!lowResourceMode)
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
    } catch (t: Throwable) {
        try {
            val crashLog = File(System.getProperty("user.home"), ".sakayori-music/crash.log")
            crashLog.parentFile.mkdirs()
            crashLog.writeText(buildString {
                append("Crash at ${java.time.LocalDateTime.now()}\n")
                append("${t.javaClass.name}: ${t.message}\n")
                t.stackTrace.forEach { append("  at $it\n") }
                var cause = t.cause
                while (cause != null) {
                    append("Caused by: ${cause.javaClass.name}: ${cause.message}\n")
                    cause.stackTrace.forEach { append("  at $it\n") }
                    cause = cause.cause
                }
            })
        } catch (_: Throwable) {
        }
        exitProcess(1)
    }
}
