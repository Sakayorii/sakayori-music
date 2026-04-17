package com.sakayori.music.expect

import androidx.appcompat.app.AppCompatActivity
import org.koin.mp.KoinPlatform.getKoin

actual fun moveTaskToBack() {
    try {
        val activity: AppCompatActivity = getKoin().get()
        activity.moveTaskToBack(true)
    } catch (_: Exception) {
    }
}
