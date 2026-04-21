package com.sakayori.music.expect

import java.awt.Desktop
import java.io.File
import java.util.Locale

actual fun pickUpdateAssetName(versionTag: String): List<String> {
    val version = versionTag.removePrefix("v")
    val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
    val arch = System.getProperty("os.arch", "").lowercase(Locale.ROOT)
    return when {
        os.contains("win") -> listOf(
            "SakayoriMusic-$version.msi",
            "SakayoriMusic-$version.exe",
        )
        os.contains("mac") || os.contains("darwin") -> listOf(
            "SakayoriMusic-$version.dmg",
            "SakayoriMusic-$version.pkg",
        )
        os.contains("nux") || os.contains("nix") -> {
            val isRpmBased = detectRpmBased()
            if (isRpmBased) {
                listOf(
                    "sakayorimusic-$version.x86_64.rpm",
                    "sakayorimusic-$version-1.x86_64.rpm",
                    "sakayorimusic_${version}_amd64.deb",
                )
            } else {
                listOf(
                    "sakayorimusic_${version}_amd64.deb",
                    "sakayorimusic-$version.x86_64.rpm",
                    "sakayorimusic-$version-1.x86_64.rpm",
                )
            }
        }
        else -> emptyList()
    }
}

actual fun installUpdateAsset(filePath: String) {
    val file = File(filePath)
    if (!file.exists()) return
    val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
    com.sakayori.music.update.UpdateInstallLock.begin()
    val path = file.absolutePath
    try {
        when {
            os.contains("win") -> {
                when {
                    path.endsWith(".msi", ignoreCase = true) -> {
                        val logPath = File(file.parentFile, "install-${System.currentTimeMillis()}.log").absolutePath
                        val cmd = buildString {
                            append("Start-Sleep -Seconds 2; ")
                            append("\$procs = Get-Process -Name 'SakayoriMusic' -ErrorAction SilentlyContinue; ")
                            append("if (\$procs) { \$procs | Stop-Process -Force -ErrorAction SilentlyContinue; Start-Sleep -Seconds 2 }; ")
                            append("Start-Process msiexec -ArgumentList '/i','\"$path\"','/passive','/norestart','MSIINSTALLPERUSER=1','ALLUSERS=\"\"','/L*v','\"$logPath\"' -Wait; ")
                            append("\$candidate = @(\"\$env:LOCALAPPDATA\\Programs\\SakayoriMusic\\SakayoriMusic.exe\",\"\$env:ProgramFiles\\SakayoriMusic\\SakayoriMusic.exe\") | Where-Object { Test-Path \$_ } | Select-Object -First 1; ")
                            append("if (\$candidate) { Start-Process -FilePath \$candidate }")
                        }
                        ProcessBuilder(
                            "powershell",
                            "-WindowStyle", "Hidden",
                            "-NoProfile",
                            "-Command", cmd,
                        ).start()
                    }
                    path.endsWith(".exe", ignoreCase = true) -> {
                        val cmd = buildString {
                            append("Start-Sleep -Seconds 2; ")
                            append("\$procs = Get-Process -Name 'SakayoriMusic' -ErrorAction SilentlyContinue; ")
                            append("if (\$procs) { \$procs | Stop-Process -Force -ErrorAction SilentlyContinue; Start-Sleep -Seconds 2 }; ")
                            append("Start-Process -FilePath '$path'")
                        }
                        ProcessBuilder(
                            "powershell",
                            "-WindowStyle", "Hidden",
                            "-NoProfile",
                            "-Command", cmd,
                        ).start()
                    }
                    else -> {
                        Desktop.getDesktop().open(file)
                    }
                }
            }
            os.contains("mac") || os.contains("darwin") -> {
                ProcessBuilder("open", path).start()
            }
            os.contains("nux") || os.contains("nix") -> {
                val cmd = when {
                    path.endsWith(".deb", ignoreCase = true) ->
                        arrayOf("bash", "-lc", "pkexec apt-get install -y '$path' && sleep 1 && pkill -f 'SakayoriMusic'")
                    path.endsWith(".rpm", ignoreCase = true) ->
                        arrayOf("bash", "-lc", "pkexec dnf install -y '$path' && sleep 1 && pkill -f 'SakayoriMusic'")
                    else ->
                        arrayOf("xdg-open", path)
                }
                ProcessBuilder(*cmd).start()
            }
            else -> {
                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                    Desktop.getDesktop().open(file)
                }
            }
        }
    } catch (_: Throwable) {
        com.sakayori.music.update.UpdateInstallLock.end()
        return
    }
    kotlin.concurrent.thread(name = "UpdateInstallLock-Timeout", isDaemon = true) {
        Thread.sleep(90_000)
        com.sakayori.music.update.UpdateInstallLock.end()
    }
}

private fun detectRpmBased(): Boolean {
    return try {
        val osRelease = File("/etc/os-release")
        if (osRelease.exists()) {
            val content = osRelease.readText().lowercase(Locale.ROOT)
            content.contains("fedora") ||
                content.contains("rhel") ||
                content.contains("centos") ||
                content.contains("opensuse") ||
                content.contains("rocky") ||
                content.contains("alma")
        } else {
            File("/etc/redhat-release").exists()
        }
    } catch (_: Throwable) {
        false
    }
}
