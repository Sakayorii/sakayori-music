package com.sakayori.music.expect

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.koin.mp.KoinPlatform.getKoin
import java.util.Locale

actual object SetupPlatform {
    actual val needsNotificationPermission: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    actual val needsBatteryOptOut: Boolean = true

    actual fun isNotificationGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val context: Context = try { getKoin().get() } catch (_: Throwable) { return false }
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    actual fun isBatteryOptIgnored(): Boolean {
        val context: Context = try { getKoin().get() } catch (_: Throwable) { return false }
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    actual fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            onResult(true)
            return
        }
        if (isNotificationGranted()) {
            onResult(true)
            return
        }
        val activity: Activity = try { getKoin().get<AppCompatActivity>() } catch (_: Throwable) {
            onResult(false)
            return
        }
        try {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                1001,
            )
        } catch (_: Throwable) {
        }
        onResult(isNotificationGranted())
    }

    actual fun requestIgnoreBatteryOptimizations() {
        val context: Context = try { getKoin().get() } catch (_: Throwable) { return }
        if (isBatteryOptIgnored()) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Throwable) {
            try {
                val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallback)
            } catch (_: Throwable) {
            }
        }
    }

    actual fun systemLanguageTag(): String {
        return try {
            Locale.getDefault().toLanguageTag()
        } catch (_: Throwable) {
            "en-US"
        }
    }
}
