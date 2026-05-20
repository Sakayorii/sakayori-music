package com.sakayori.music

import androidx.compose.ui.window.ComposeUIViewController
import com.sakayori.data.di.loader.loadAllModules
import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.logger.Logger
import com.sakayori.music.di.viewModelModule
import com.sakayori.music.utils.IosCrashLogger
import com.sakayori.music.utils.IosCrashReporting
import com.sakayori.music.utils.IosLogReporter
import com.sakayori.music.utils.IosMemoryPressure
import com.sakayori.music.utils.VersionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

private var koinStarted = false

private fun ensureKoin() {
    if (koinStarted) return
    IosCrashLogger.install()
    IosMemoryPressure.install()
    try {
        VersionManager.initialize()
    } catch (_: Throwable) {
    }
    try {
        if (BuildKonfig.sentryDsnIos.isNotEmpty()) {
            IosCrashReporting.init(BuildKonfig.sentryDsnIos, VersionManager.getVersionName())
        }
    } catch (_: Throwable) {
    }
    try {
        Logger.installReporter(IosLogReporter())
    } catch (_: Throwable) {
    }
    try {
        startKoin {
            loadKoinModules(listOf(viewModelModule))
            loadAllModules()
        }
    } catch (_: Throwable) {
        loadKoinModules(listOf(viewModelModule))
        loadAllModules()
    }
    val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    scope.launch {
        try {
            val dataStoreManager = KoinPlatform.getKoin().get<DataStoreManager>()
            dataStoreManager.crashReportingEnabled.collect { value ->
                IosCrashReporting.setEnabled(value == DataStoreManager.TRUE)
            }
        } catch (_: Throwable) {
        }
    }
    koinStarted = true
}

fun MainViewController() = ComposeUIViewController {
    ensureKoin()
    App()
}
