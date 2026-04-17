package com.sakayori.music.expect.ui

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
import org.jetbrains.compose.resources.stringResource
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.failed_to_initialize_browser
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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(48.dp), color = Color.White)
        }
    } else {
        val b = browser
        if (b != null) {
            SwingPanel(
                modifier = Modifier.fillMaxSize(),
                factory = { b.uiComponent },
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(Res.string.failed_to_initialize_browser), color = Color.White)
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
            onPageFinished = { url ->
                state.value = WebViewState.Finished
                if (url.contains("music.youtube.com") && !url.contains("accounts.google.com")) {
                    Thread {
                        Thread.sleep(1000)
                        try {
                            val cookieManager = org.cef.network.CefCookieManager.getGlobalManager()
                            val cookies = java.util.concurrent.CopyOnWriteArrayList<String>()
                            val latch = java.util.concurrent.CountDownLatch(1)
                            cookieManager.visitUrlCookies(
                                "https://music.youtube.com",
                                true,
                                object : org.cef.callback.CefCookieVisitor {
                                    override fun visit(
                                        cookie: org.cef.network.CefCookie,
                                        count: Int,
                                        total: Int,
                                        delete: org.cef.misc.BoolRef,
                                    ): Boolean {
                                        cookies.add("${cookie.name}=${cookie.value}")
                                        if (count >= total - 1) latch.countDown()
                                        return true
                                    }
                                },
                            )
                            latch.await(3, java.util.concurrent.TimeUnit.SECONDS)
                            val cookieString = cookies.joinToString("; ")
                            if (cookieString.isNotEmpty()) {
                                try {
                                    val handler = CookieHandler.getDefault() as? CookieManager
                                        ?: CookieManager().also { CookieHandler.setDefault(it) }
                                    cookies.forEach { pair ->
                                        val parts = pair.split("=", limit = 2)
                                        if (parts.size == 2) {
                                            handler.cookieStore.add(
                                                URI("https://music.youtube.com"),
                                                java.net.HttpCookie(parts[0].trim(), parts[1].trim()).apply {
                                                    domain = ".youtube.com"
                                                    path = "/"
                                                },
                                            )
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        } catch (_: Exception) {}
                    }.start()
                }
                onPageFinished(url)
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
