package com.sakayori.music.expect.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sakayori.logger.Logger
import org.jetbrains.compose.resources.stringResource
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.failed_to_initialize_browser
import dev.datlag.kcef.KCEF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

actual fun clearWebViewCacheAndCookies() {
    try {
        CookieHandler.setDefault(CookieManager())
    } catch (_: Throwable) {
    }
    try {
        org.cef.network.CefCookieManager.getGlobalManager().deleteCookies("", "")
    } catch (_: Throwable) {
    }
    try {
        val cacheDir = File(System.getProperty("user.home"), ".sakayori-music/kcef-bundle")
        listOf("Cache", "Code Cache", "GPUCache", "Cookies", "Cookies-journal").forEach { name ->
            val target = File(cacheDir, name)
            if (target.exists()) target.deleteRecursively()
        }
    } catch (_: Throwable) {
    }
}

@Volatile
private var kcefInitialized = false

@Volatile
private var kcefInitError: String? = null

@Volatile
private var kcefDownloadProgress: Int = 0

private suspend fun ensureKcefInitialized() {
    if (kcefInitialized) return
    withContext(Dispatchers.Default) {
        try {
            Logger.i("KCef", "Starting KCef init at ${System.getProperty("user.home")}/.sakayori-music/kcef-bundle")
            KCEF.init(
                builder = {
                    installDir(File(System.getProperty("user.home"), ".sakayori-music/kcef-bundle"))
                    progress {
                        onDownloading { percent ->
                            kcefDownloadProgress = percent.toInt()
                            if (percent.toInt() % 10 == 0) {
                                Logger.i("KCef", "Downloading CEF: ${percent.toInt()}%")
                            }
                        }
                        onInitialized {
                            Logger.i("KCef", "KCef initialized successfully")
                        }
                    }
                },
                onError = { t ->
                    val msg = t?.message ?: t?.toString() ?: "unknown KCef error"
                    kcefInitError = msg
                    Logger.e("KCef", "KCef init failed: $msg", t)
                },
                onRestartRequired = {
                    Logger.w("KCef", "KCef requires JVM restart to complete init")
                },
            )
            kcefInitialized = true
            Logger.i("KCef", "kcefInitialized=true")
        } catch (t: Throwable) {
            kcefInitError = "${t::class.simpleName}: ${t.message ?: "no message"}"
            Logger.e("KCef", "KCef init threw exception", t)
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
    var progressPercent by remember { mutableStateOf(kcefDownloadProgress) }
    var errorMessage by remember { mutableStateOf(kcefInitError) }

    LaunchedEffect(Unit) {
        if (!kcefInitialized) {
            val pollJob = launch {
                while (isActive) {
                    progressPercent = kcefDownloadProgress
                    errorMessage = kcefInitError
                    delay(200)
                }
            }
            try {
                ensureKcefInitialized()
            } finally {
                pollJob.cancel()
                progressPercent = kcefDownloadProgress
                errorMessage = kcefInitError
            }
        }
        ready = kcefInitialized
        if (ready && errorMessage == null) {
            try {
                val client = KCEF.newClientOrNullBlocking()
                val b = client?.createBrowser(url, org.cef.browser.CefRendering.DEFAULT, false)
                browser = b
                if (b != null) {
                    onBrowserReady(b)
                } else {
                    Logger.e("KCef", "createBrowser returned null after init success for $url")
                }
            } catch (t: Throwable) {
                Logger.e("KCef", "createBrowser threw exception", t)
                errorMessage = "createBrowser: ${t::class.simpleName}: ${t.message ?: "no message"}"
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

    if (errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(Res.string.failed_to_initialize_browser),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "KCef: $errorMessage",
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                )
            }
        }
    } else if (!ready) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp), color = Color.White)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (progressPercent in 1..99) "Downloading browser engine ${progressPercent}%" else "Initializing browser engine",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                if (progressPercent in 1..99) {
                    LinearProgressIndicator(
                        progress = { progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Color.White,
                    )
                }
            }
        }
    } else {
        val b = browser
        if (b != null) {
            SwingPanel(
                modifier = Modifier.fillMaxSize(),
                factory = { b.uiComponent },
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.failed_to_initialize_browser),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
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
