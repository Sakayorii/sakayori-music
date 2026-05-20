package com.sakayori.music.expect.ui

import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

actual fun createWebViewCookieManager(): WebViewCookieManager =
    object : WebViewCookieManager {
        override fun getCookie(url: String): String {
            val cookie = CookieManager.getInstance()
            return if (cookie.hasCookies()) {
                cookie.getCookie(url)
            } else {
                ""
            }
        }

        override fun removeAllCookies() {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        }
    }

actual fun clearWebViewCacheAndCookies() {
    try {
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().removeSessionCookies(null)
        CookieManager.getInstance().flush()
    } catch (_: Throwable) {
    }
    try {
        val context: androidx.appcompat.app.AppCompatActivity = org.koin.mp.KoinPlatform.getKoin().get()
        WebView(context).apply {
            clearCache(true)
            clearHistory()
            clearFormData()
            android.webkit.WebStorage.getInstance().deleteAllData()
        }
    } catch (_: Throwable) {
    }
}

@Composable
actual fun PlatformWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onPageFinished: (String) -> Unit,
) {
    Box {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams =
                        ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                    webViewClient =
                        object : WebViewClient() {
                            override fun onPageFinished(
                                view: WebView?,
                                url: String?,
                            ) {
                                url?.let {
                                    onPageFinished(it)
                                }
                            }
                        }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true

                    loadUrl(initUrl)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        aboveContent()
    }
}

@Composable
actual fun DiscordWebView(
    state: MutableState<WebViewState>,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onLoginDone: (token: String) -> Unit
) {
    val startUrl = "https://discord.com/login"
    Box {
        AndroidView(factory = {
            WebView(it).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                var authorizeStarted = false
                var tokenCaptured = false
                webViewClient = object : WebViewClient() {

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        if (url == null || view == null || tokenCaptured) return
                        val isLoggedIn = url.contains("/channels/") ||
                            url.endsWith("/app") ||
                            url.contains("/discovery") ||
                            url.contains("/store")
                        val isAuthorizedPage = url.contains("/oauth2/authorized") ||
                            url.contains("/oauth2/authorize/callback")
                        if (isAuthorizedPage) {
                            tokenCaptured = true
                            view.loadUrl("https://discord.com/app")
                            view.postDelayed({ view.loadUrl(JS_SNIPPET) }, 600)
                            return
                        }
                        if (isLoggedIn && !authorizeStarted) {
                            authorizeStarted = true
                            view.loadUrl(SAKAYORI_BOT_AUTHORIZE_URL)
                        }
                    }
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true

                if (android.os.Build.MANUFACTURER.equals(MOTOROLA, ignoreCase = true)) {
                    settings.userAgentString = SAMSUNG_USER_AGENT
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(
                        view: WebView,
                        url: String,
                        message: String,
                        result: JsResult,
                    ): Boolean {
                        if (!tokenCaptured) tokenCaptured = true
                        onLoginDone(message)
                        result.confirm()
                        return true
                    }
                }
                loadUrl(startUrl)
            }
        })
        aboveContent()
    }
}

const val JS_SNIPPET =
    "javascript:(function()%7Bvar%20i%3Ddocument.createElement('iframe')%3Bdocument.body.appendChild(i)%3Btry%7Balert(i.contentWindow.localStorage.token.slice(1%2C-1))%7Dcatch(e)%7Balert('')%7D%7D)()"
private const val SAKAYORI_DISCORD_APP_ID = "1493865560013017160"
private const val SAKAYORI_BOT_AUTHORIZE_URL =
    "https://discord.com/oauth2/authorize?client_id=$SAKAYORI_DISCORD_APP_ID&integration_type=1&scope=applications.commands+bot"
private const val MOTOROLA = "motorola"
private const val SAMSUNG_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; SM-S921U; Build/UP1A.231005.007) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Mobile Safari/537.363"
