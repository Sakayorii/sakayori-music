package com.sakayori.music.update

import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

object UpdateInstallLock {
    private val installing = AtomicBoolean(false)

    fun begin() {
        installing.set(true)
    }

    fun end() {
        installing.set(false)
    }

    fun isInstalling(): Boolean = installing.get()

    fun awaitAndExit(onExit: () -> Unit) {
        thread(name = "UpdateInstallLock-Exit", isDaemon = true) {
            while (installing.get()) {
                Thread.sleep(200)
            }
            onExit()
        }
    }
}
