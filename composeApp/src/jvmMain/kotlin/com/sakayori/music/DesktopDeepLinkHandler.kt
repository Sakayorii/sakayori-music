package com.sakayori.music

import com.eygraber.uri.Uri
import com.sakayori.domain.data.model.intent.GenericIntent
import com.sakayori.logger.Logger
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

object DesktopDeepLinkHandler {
    private const val TAG = "DesktopDeepLinkHandler"
    private val URI_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9+.\\-]*://.+")

    private val pendingUriFile: File by lazy {
        File(System.getProperty("java.io.tmpdir"), "SakayoriMusic_pending_deeplink.txt")
    }

    private var cached: String? = null

    var listener: ((GenericIntent) -> Unit)? = null
        set(value) {
            field = value
            if (value != null) {
                cached?.let { uri ->
                    Logger.d(TAG, "Delivering cached URI: $uri")
                    value.invoke(parseToIntent(uri))
                    cached = null
                }
            }
        }

    fun onNewUri(uri: String) {
        Logger.d(TAG, "Received URI: $uri")
        val intent = parseToIntent(uri)
        val currentListener = listener
        if (currentListener != null) {
            currentListener.invoke(intent)
            cached = null
        } else {
            Logger.d(TAG, "Listener not ready, caching URI: $uri")
            cached = uri
        }
    }

    fun writePendingUri(uri: String) {
        try {
            val path = pendingUriFile.toPath()
            Files.deleteIfExists(path)
            try {
                val permissions = PosixFilePermissions.fromString("rw-------")
                val attrs = PosixFilePermissions.asFileAttribute(permissions)
                Files.createFile(path, attrs)
            } catch (_: UnsupportedOperationException) {
                Files.createFile(path)
                pendingUriFile.setReadable(false, false)
                pendingUriFile.setWritable(false, false)
                pendingUriFile.setReadable(true, true)
                pendingUriFile.setWritable(true, true)
            }
            Files.writeString(path, uri)
            Logger.d(TAG, "Wrote pending URI to file: $uri")
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to write pending URI: ${e.message}")
        }
    }

    fun consumePendingUri() {
        try {
            if (pendingUriFile.exists()) {
                val uri = pendingUriFile.readText().trim()
                pendingUriFile.delete()
                if (uri.isNotEmpty() && isValidUri(uri)) {
                    Logger.d(TAG, "Consumed pending URI from file: $uri")
                    onNewUri(uri)
                } else if (uri.isNotEmpty()) {
                    Logger.e(TAG, "Invalid URI format in pending file, discarding")
                }
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Failed to read pending URI: ${e.message}")
            pendingUriFile.delete()
        }
    }

    private fun isValidUri(uri: String): Boolean {
        if (uri.length > 2048) return false
        if (uri.contains('\n') || uri.contains('\r')) return false
        return URI_PATTERN.matches(uri)
    }

    private fun parseToIntent(uri: String): GenericIntent {
        val parsed = Uri.parse(uri)

        val actualUri = when {
            parsed.scheme == "SakayoriMusic" && parsed.host == "open-app" -> {
                val urlParam = parsed.getQueryParameter("url")
                if (urlParam != null) {
                    Logger.d(TAG, "Extracted URL from open-app: $urlParam")
                    Uri.parse(urlParam)
                } else {
                    Logger.d(TAG, "open-app without URL param, just opening app")
                    null
                }
            }

            parsed.scheme == "SakayoriMusic" && parsed.host != null -> {
                val host = parsed.host!!
                val query = parsed.query?.let { "?$it" } ?: ""
                val pathSuffix = parsed.pathSegments.joinToString("/").let {
                    if (it.isNotEmpty()) "/$it" else ""
                }
                val convertedUrl = "https://SakayoriMusic.org/app/$host$pathSuffix$query"
                Logger.d(TAG, "Converted SakayoriMusic:// to: $convertedUrl")
                Uri.parse(convertedUrl)
            }

            else -> parsed
        }

        return if (actualUri != null) {
            GenericIntent(
                action = "android.intent.action.VIEW",
                data = actualUri,
            )
        } else {
            GenericIntent(action = "android.intent.action.VIEW")
        }
    }
}
