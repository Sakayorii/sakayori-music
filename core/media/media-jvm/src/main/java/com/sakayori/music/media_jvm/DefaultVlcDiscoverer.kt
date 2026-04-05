package com.sakayori.music.media_jvm

import com.sakayori.logger.Logger
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy
import java.io.File
import java.net.URISyntaxException

class DefaultVlcDiscoverer : NativeDiscoveryStrategy {

    private val tag = "DefaultVlcDiscoverer"

    override fun supported(): Boolean {
        val os = System.getProperty("os.name", "").lowercase()
        return !os.contains("mac")
    }

    override fun discover(): String? {
        return findBundledVlcPath()
    }

    override fun onFound(path: String): Boolean {
        Logger.i(tag, "Found native VLC libraries in $path")
        return true
    }

    override fun onSetPluginPath(path: String): Boolean {
        Logger.i(tag, "VLC plugin path set to $path")
        return true
    }

    companion object {
        private const val TAG = "DefaultVlcDiscoverer"

        fun findBundledVlcPath(): String? {
            val resourcesDir = System.getProperty("compose.application.resources.dir")
            if (resourcesDir != null) {
                val found = findVlcInDirectory(File(resourcesDir))
                if (found != null) return found
            }

            val bundledPath = System.getProperty("vlc.bundled.path")
            if (bundledPath != null) {
                val dir = File(bundledPath)
                if (dir.exists() && hasVlcLib(dir)) {
                    Logger.i(TAG, "Found VLC via vlc.bundled.path: $bundledPath")
                    return dir.absolutePath
                }
            }

            try {
                val jarFile = File(DefaultVlcDiscoverer::class.java.protectionDomain.codeSource.location.toURI())
                val jarDir = jarFile.parentFile
                if (jarDir != null && jarDir.isDirectory) {
                    val appDir = jarDir.parentFile
                    if (appDir != null) {
                        val foundInAppWin = findVlcInDirectory(File(appDir, "windows"))
                        if (foundInAppWin != null) return foundInAppWin
                    }
                    val foundInJarDirWin = findVlcInDirectory(File(jarDir, "windows"))
                    if (foundInJarDirWin != null) return foundInJarDirWin

                    val foundInJarDir = findVlcInDirectory(jarDir)
                    if (foundInJarDir != null) return foundInJarDir
                }
            } catch (e: URISyntaxException) {
                Logger.e(TAG, "Failed to get JAR location: ${e.message}")
            }

            val osName = System.getProperty("os.name", "").lowercase()
            val subDir = when {
                osName.contains("win") -> "windows"
                osName.contains("mac") -> "macos"
                else -> "linux"
            }
            val fallbackDir = File("vlc-natives/$subDir")
            if (fallbackDir.exists() && hasVlcLib(fallbackDir)) return fallbackDir.absolutePath

            return null
        }

        private fun findVlcInDirectory(dir: File): String? {
            if (!dir.exists() || !dir.isDirectory) return null
            if (hasVlcLib(dir)) return dir.absolutePath
            dir.listFiles()?.filter { it.isDirectory }?.forEach { subDir ->
                if (hasVlcLib(subDir)) return subDir.absolutePath
            }
            return null
        }

        private fun hasVlcLib(dir: File): Boolean =
            dir.listFiles()?.any {
                it.name.startsWith("libvlc") || it.name == "vlc.dll"
            } == true
    }
}
