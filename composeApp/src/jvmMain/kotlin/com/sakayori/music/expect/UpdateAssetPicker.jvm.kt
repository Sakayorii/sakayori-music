package com.sakayori.music.expect

import java.awt.Desktop
import java.io.File
import java.util.Locale

actual fun pickUpdateAssetName(versionTag: String): List<String> {
    val version = versionTag.removePrefix("v")
    val os = System.getProperty("os.name", "").lowercase(Locale.ROOT)
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
                        val updatesDir = file.parentFile.absolutePath
                        val cmd = buildString {
                            append("\$perUserKeys = Get-ChildItem 'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall' -ErrorAction SilentlyContinue; ")
                            append("foreach (\$k in \$perUserKeys) { ")
                            append("\$pr = Get-ItemProperty \$k.PSPath -ErrorAction SilentlyContinue; ")
                            append("if (\$pr.DisplayName -like '*SakayoriMusic*' -and \$pr.UninstallString -match '\\{[0-9A-Fa-f\\-]+\\}') { ")
                            append("Start-Process msiexec -ArgumentList '/x',\$matches[0],'/qn','/norestart' -Wait -NoNewWindow -ErrorAction SilentlyContinue ")
                            append("} }; ")
                            append("if (Test-Path \"\$env:LOCALAPPDATA\\Programs\\SakayoriMusic\") { Remove-Item -Recurse -Force \"\$env:LOCALAPPDATA\\Programs\\SakayoriMusic\" -ErrorAction SilentlyContinue }; ")
                            append("if (Test-Path \"\$env:APPDATA\\Microsoft\\Windows\\Start Menu\\Programs\\SakayoriMusic\") { Remove-Item -Recurse -Force \"\$env:APPDATA\\Microsoft\\Windows\\Start Menu\\Programs\\SakayoriMusic\" -ErrorAction SilentlyContinue }; ")
                            append("\$p = Start-Process msiexec -ArgumentList '/i','\"$path\"','/qn','/norestart','MSIRMSHUTDOWN=2','/L*v','\"$logPath\"' -Verb RunAs -PassThru; ")
                            append("\$p.WaitForExit(240000) | Out-Null; ")
                            append("Start-Sleep -Seconds 1; ")
                            append("Get-Process -Name 'SakayoriMusic' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue; ")
                            append("Start-Sleep -Seconds 1; ")
                            append("if (\$p.ExitCode -eq 0) { Remove-Item -Path '$path' -Force -ErrorAction SilentlyContinue; Remove-Item -Path '$logPath' -Force -ErrorAction SilentlyContinue; Get-ChildItem -Path '$updatesDir' -Filter 'install-*.log' -ErrorAction SilentlyContinue | Remove-Item -Force -ErrorAction SilentlyContinue }; ")
                            append("\$candidate = @(\"\$env:ProgramFiles\\SakayoriMusic\\SakayoriMusic.exe\",\"\$env:LOCALAPPDATA\\Programs\\SakayoriMusic\\SakayoriMusic.exe\") | Where-Object { Test-Path \$_ } | Select-Object -First 1; ")
                            append("if (\$candidate) { Start-Process -FilePath \$candidate }")
                        }
                        ProcessBuilder(
                            "powershell",
                            "-WindowStyle", "Hidden",
                            "-NoProfile",
                            "-ExecutionPolicy", "Bypass",
                            "-Command", cmd,
                        ).start()
                    }
                    path.endsWith(".exe", ignoreCase = true) -> {
                        val cmd = buildString {
                            append("\$p = Start-Process -FilePath '$path' -ArgumentList '/quiet','/norestart' -Verb RunAs -PassThru; ")
                            append("\$p.WaitForExit(240000) | Out-Null; ")
                            append("Start-Sleep -Seconds 1; ")
                            append("Get-Process -Name 'SakayoriMusic' -ErrorAction SilentlyContinue | Stop-Process -Force -ErrorAction SilentlyContinue; ")
                            append("Start-Sleep -Seconds 1; ")
                            append("if (\$p.ExitCode -eq 0) { Remove-Item -Path '$path' -Force -ErrorAction SilentlyContinue }; ")
                            append("\$candidate = @(\"\$env:ProgramFiles\\SakayoriMusic\\SakayoriMusic.exe\",\"\$env:LOCALAPPDATA\\Programs\\SakayoriMusic\\SakayoriMusic.exe\") | Where-Object { Test-Path \$_ } | Select-Object -First 1; ")
                            append("if (\$candidate) { Start-Process -FilePath \$candidate }")
                        }
                        ProcessBuilder(
                            "powershell",
                            "-WindowStyle", "Hidden",
                            "-NoProfile",
                            "-ExecutionPolicy", "Bypass",
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
                        arrayOf("bash", "-lc", "pkexec apt-get install -y '$path' && rm -f '$path' && sleep 1 && pkill -f 'SakayoriMusic'")
                    path.endsWith(".rpm", ignoreCase = true) ->
                        arrayOf("bash", "-lc", "pkexec dnf install -y '$path' && rm -f '$path' && sleep 1 && pkill -f 'SakayoriMusic'")
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
        Thread.sleep(300_000)
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
