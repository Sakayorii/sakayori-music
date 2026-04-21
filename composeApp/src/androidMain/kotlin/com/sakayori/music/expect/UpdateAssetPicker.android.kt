package com.sakayori.music.expect

import android.content.Intent
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import org.koin.mp.KoinPlatform.getKoin
import java.io.File

actual fun pickUpdateAssetName(versionTag: String): List<String> {
    val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "universal"
    val matched = when {
        abi.contains("arm64") -> "arm64-v8a"
        abi.contains("armeabi-v7a") || abi.contains("armv7") -> "armeabi-v7a"
        abi.contains("x86_64") -> "x86_64"
        else -> "universal"
    }
    return listOf(
        "androidApp-$matched-release.apk",
        "androidApp-universal-release.apk",
    )
}

actual fun installUpdateAsset(filePath: String) {
    val context: AppCompatActivity = getKoin().get()
    val file = File(filePath)
    if (!file.exists()) return
    val uri = FileProvider.getUriForFile(
        context,
        context.packageName + ".FileProvider",
        file,
    )
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/vnd.android.package-archive")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}
