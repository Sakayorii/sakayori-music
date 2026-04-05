package com.sakayori.music.media_jvm

import com.sakayori.logger.Logger
import com.sun.jna.NativeLibrary
import uk.co.caprica.vlcj.binding.lib.LibC
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil
import uk.co.caprica.vlcj.factory.discovery.strategy.BaseNativeDiscoveryStrategy
import java.io.File

class MacOsVlcDiscoverer :
    BaseNativeDiscoveryStrategy(
        FILENAME_PATTERNS,
        PLUGIN_PATH_FORMATS,
    ) {
    private val tag = "MacOsVlcDiscoverer"

    override fun supported(): Boolean {
        val os = System.getProperty("os.name", "").lowercase()
        return os.contains("mac")
    }

    override fun discoveryDirectories(): List<String> {
        val path = DefaultVlcDiscoverer.findBundledVlcPath()
        return if (path != null) listOf(path) else emptyList()
    }

    override fun onFound(path: String): Boolean {
        Logger.i(tag, "Found native VLC libraries in $path")
        forceLoadLibVlcCore(path)
        return true
    }

    override fun setPluginPath(pluginPath: String?): Boolean = LibC.INSTANCE.setenv(PLUGIN_ENV_NAME, pluginPath, 1) == 0

    private fun forceLoadLibVlcCore(path: String) {
        NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcCoreLibraryName(), path)
        NativeLibrary.getInstance(RuntimeUtil.getLibVlcCoreLibraryName())
        Logger.i(tag, "Force-loaded libvlccore from $path")
    }

    companion object {
        private val FILENAME_PATTERNS = arrayOf("libvlc\\.dylib", "libvlccore\\.dylib")
        private val PLUGIN_PATH_FORMATS = arrayOf("%s/plugins")
    }
}
