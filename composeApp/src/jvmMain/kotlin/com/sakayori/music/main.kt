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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
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
import com.sakayori.data.di.loader.loadAllModules
import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.domain.mediaservice.handler.MediaPlayerHandler
import com.sakayori.music.extension.getStringBlocking
import com.sakayori.music.extension.makeDarkToast
import com.sakayori.domain.mediaservice.handler.ToastType
import com.sakayori.music.di.viewModelModule
import com.sakayori.music.ui.component.CustomTitleBar
import com.sakayori.music.ui.mini_player.MiniPlayerManager
import com.sakayori.music.ui.mini_player.MiniPlayerWindow
import com.sakayori.music.utils.VersionManager
import com.sakayori.music.viewModel.SharedViewModel
import com.sakayori.music.viewModel.changeLanguageNative
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
        com.sakayori.music.utils.FreezeWatchdog.start()
        com.sakayori.music.utils.HeapPressureMonitor.start()
        com.sakayori.music.utils.DesktopCrashReporting.init(
            BuildKonfig.sentryDsnDesktop,
            VersionManager.getVersionName(),
        )

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
                    io.sentry.Sentry.close()
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
                Thread {
                    kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                        getKoin().get<DataStoreManager>().crashReportingEnabled.collect { value ->
                            com.sakayori.music.utils.DesktopCrashReporting.setEnabled(value == "TRUE")
                        }
                    }
                }.apply { isDaemon = true; name = "SentryToggleWatcher" }.start()
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
            makeDarkToast(
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

            val isMacOS = remember { System.getProperty("os.name", "").lowercase().contains("mac") }

            Window(
                onCloseRequest = {
                    if (com.sakayori.music.update.UpdateInstallLock.isInstalling()) {
                        isVisible = false
                        Thread {
                            try { mediaPlayerHandler.release() } catch (_: Throwable) {}
                        }.apply { isDaemon = true; name = "ReleaseOnClose" }.start()
                        com.sakayori.music.update.UpdateInstallLock.awaitAndExit { exitApplication() }
                        return@Window
                    }
                    isVisible = false
                    Thread {
                        try { mediaPlayerHandler.release() } catch (_: Throwable) {}
                    }.apply { isDaemon = true; name = "ReleaseOnClose" }.start()
                    Thread {
                        try { Thread.sleep(3000) } catch (_: Throwable) {}
                        Runtime.getRuntime().halt(0)
                    }.apply { isDaemon = true; name = "ForceExitWatchdog" }.start()
                    exitApplication()
                },
                title = stringResource(Res.string.app_name),
                icon = painterResource(Res.drawable.circle_app_icon),
                undecorated = !isMacOS,
                transparent = false,
                state = windowState,
                visible = isVisible,
                onKeyEvent = { event ->
                    if (event.type != KeyEventType.KeyUp) return@Window false
                    val vm = getKoin().get<com.sakayori.music.viewModel.SharedViewModel>()
                    when (event.key) {
                        Key.Spacebar -> {
                            vm.onUIEvent(com.sakayori.music.viewModel.UIEvent.PlayPause)
                            true
                        }
                        Key.DirectionRight, Key.MediaNext -> {
                            vm.onUIEvent(com.sakayori.music.viewModel.UIEvent.Next)
                            true
                        }
                        Key.DirectionLeft, Key.MediaPrevious -> {
                            vm.onUIEvent(com.sakayori.music.viewModel.UIEvent.Previous)
                            true
                        }
                        Key.L -> {
                            vm.onUIEvent(com.sakayori.music.viewModel.UIEvent.ToggleLike)
                            true
                        }
                        Key.S -> {
                            vm.onUIEvent(com.sakayori.music.viewModel.UIEvent.Shuffle)
                            true
                        }
                        Key.R -> {
                            vm.onUIEvent(com.sakayori.music.viewModel.UIEvent.Repeat)
                            true
                        }
                        Key.DirectionUp -> {
                            val current = vm.controllerState.value.volume
                            val newVol = (current + 0.05f).coerceAtMost(1f)
                            vm.onUIEvent(com.sakayori.music.viewModel.UIEvent.UpdateVolume(newVol))
                            makeDarkToast("🔊 ${(newVol * 100).toInt()}%", multiplatform.network.cmptoast.ToastGravity.Top)
                            true
                        }
                        Key.DirectionDown -> {
                            val current = vm.controllerState.value.volume
                            val newVol = (current - 0.05f).coerceAtLeast(0f)
                            vm.onUIEvent(com.sakayori.music.viewModel.UIEvent.UpdateVolume(newVol))
                            makeDarkToast("🔊 ${(newVol * 100).toInt()}%", multiplatform.network.cmptoast.ToastGravity.Top)
                            true
                        }
                        Key.M -> {
                            val current = vm.controllerState.value.volume
                            val newVol = if (current > 0f) 0f else 1f
                            vm.onUIEvent(com.sakayori.music.viewModel.UIEvent.UpdateVolume(newVol))
                            makeDarkToast(
                                if (newVol > 0f) "🔊 Unmuted" else "🔇 Muted",
                                multiplatform.network.cmptoast.ToastGravity.Top,
                            )
                            true
                        }
                        Key.Slash, Key.H -> {
                            makeDarkToast(
                                "Shortcuts: Space=Play · ←→=Skip · ↑↓=Volume · M=Mute · L=Like · S=Shuffle · R=Repeat",
                                multiplatform.network.cmptoast.ToastGravity.Top,
                            )
                            true
                        }
                        else -> false
                    }
                },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (!isMacOS) {
                        CustomTitleBar(
                            title = stringResource(Res.string.app_name),
                            windowState = windowState,
                            window = window,
                            onCloseRequest = {
                                mediaPlayerHandler.release()
                                exitApplication()
                            },
                        )
                    }

                    val context = LocalPlatformContext.current
                    var lowResourceMode by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        lowResourceMode = getKoin().get<DataStoreManager>().lowResourceMode.first() == "TRUE"
                    }
                    val memoryCacheMaxBytes = if (lowResourceMode) 8L * 1024 * 1024 else 32L * 1024 * 1024
                    val diskCacheMaxBytes = if (lowResourceMode) 128L * 1024 * 1024 else 512L * 1024 * 1024
                    setSingletonImageLoaderFactory {
                        val loader = ImageLoader.Builder(context)
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
                        com.sakayori.music.utils.HeapPressureMonitor.onPressure = {
                            try {
                                loader.memoryCache?.clear()
                            } catch (_: Throwable) {
                            }
                        }
                        loader
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
