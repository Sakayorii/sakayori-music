package com.sakayori.music

import androidx.compose.ui.window.ComposeUIViewController
import com.sakayori.data.di.loader.loadAllModules
import com.sakayori.music.di.viewModelModule
import com.sakayori.music.utils.IosCrashLogger
import com.sakayori.music.utils.IosMemoryPressure
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin

private var koinStarted = false

private fun ensureKoin() {
    if (koinStarted) return
    IosCrashLogger.install()
    IosMemoryPressure.install()
    try {
        startKoin {
            loadKoinModules(listOf(viewModelModule))
            loadAllModules()
        }
    } catch (_: Throwable) {
        loadKoinModules(listOf(viewModelModule))
        loadAllModules()
    }
    koinStarted = true
}

fun MainViewController() = ComposeUIViewController {
    ensureKoin()
    App()
}
