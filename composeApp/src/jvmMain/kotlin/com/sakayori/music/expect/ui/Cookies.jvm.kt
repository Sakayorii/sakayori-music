package com.sakayori.music.expect.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import java.io.File
import java.net.CookieHandler
import java.net.CookieManager
import java.net.URI

actual fun createWebViewCookieManager(): WebViewCookieManager =
    object : WebViewCookieManager {
        override fun getCookie(url: String): String =
            try {
                CookieHandler
                    .getDefault()
                    ?.get(URI(url), emptyMap())
                    ?.get("Cookie")
                    ?.joinToString("; ") ?: ""
            } catch (_: Exception) {
                ""
            }

        override fun removeAllCookies() {
            CookieHandler.setDefault(CookieManager())
        }
    }

@Volatile
private var kcefInitialized = false

private suspend fun ensureKcefInitialized() {
    if (kcefInitialized) return
    withContext(Dispatchers.Default) {
        try {
            KCEF.init(
                builder = {
                    installDir(File(System.getProperty("user.home"), ".sakayori-music/kcef-bundle"))
                },
                onError = { },
                onRestartRequired = { },
            )
            kcefInitialized = true
        } catch (_: Throwable) {
        }
    }
}

@Composable
private fun KcefBrowserView(
    url: String,
    onPageFinished: (String) -> Unit,
    onConsoleMessage: (String) -> Unit = {},
    onBrowserReady: (CefBrowser) -> Unit = {},
) {
    var ready by remember { mutableStateOf(kcefInitialized) }
    var browser by remember { mutableStateOf<CefBrowser?>(null) }

    LaunchedEffect(Unit) {
        if (!kcefInitialized) {
            ensureKcefInitialized()
        }
        ready = kcefInitialized
        if (ready) {
            try {
                val client = KCEF.newClientOrNullBlocking()
                val b = client?.createBrowser(url, org.cef.browser.CefRendering.DEFAULT, false)
                browser = b
                if (b != null) onBrowserReady(b)
            } catch (_: Throwable) {
            }
        }
    }

    DisposableEffect(browser) {
        val b = browser
        val client = b?.client
        val loadHandler = object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cb: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                cb?.url?.let { onPageFinished(it) }
            }
        }
        val displayHandler = object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(
                cb: CefBrowser?,
                level: org.cef.CefSettings.LogSeverity?,
                message: String?,
                source: String?,
                line: Int,
            ): Boolean {
                if (!message.isNullOrEmpty()) onConsoleMessage(message)
                return false
            }
        }
        client?.addLoadHandler(loadHandler)
        client?.addDisplayHandler(displayHandler)
        onDispose {
            try {
                client?.removeLoadHandler()
                client?.removeDisplayHandler()
                b?.close(true)
            } catch (_: Throwable) {
            }
        }
    }

    if (!ready) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(com.sakayori.music.ui.theme.md_theme_dark_background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp), color = Color.White)
        }
    } else {
        val b = browser
        if (b != null) {
            SwingPanel(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    val comp = b.uiComponent
                    comp.background = java.awt.Color(10, 10, 10)
                    comp
                },
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Failed to initialize browser", color = Color.White)
            }
        }
    }
}

@Composable
actual fun PlatformWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onPageFinished: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        KcefBrowserView(
            url = initUrl,
            onPageFinished = {
                state.value = WebViewState.Finished
                onPageFinished(it)
            },
        )
        aboveContent()
    }
}

@Composable
actual fun DiscordWebView(
    state: MutableState<WebViewState>,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onLoginDone: (token: String) -> Unit,
) {
    var browserRef by remember { mutableStateOf<CefBrowser?>(null) }
    Box(modifier = Modifier.fillMaxSize()) {
        KcefBrowserView(
            url = "https://discord.com/login",
            onPageFinished = { url ->
                state.value = WebViewState.Finished
                if (url.endsWith("/app") || url.endsWith("/channels/@me")) {
                    browserRef?.executeJavaScript(
                        "(function(){var i=document.createElement('iframe');document.body.appendChild(i);console.log('SAKAYORI_TOKEN:'+i.contentWindow.localStorage.token.slice(1,-1));})();",
                        url,
                        0,
                    )
                }
            },
            onConsoleMessage = { msg ->
                if (msg.startsWith("SAKAYORI_TOKEN:")) {
                    onLoginDone(msg.removePrefix("SAKAYORI_TOKEN:"))
                }
            },
            onBrowserReady = { browserRef = it },
        )
        aboveContent()
    }
}
