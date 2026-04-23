@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.sakayori.music.expect.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import platform.Foundation.NSHTTPCookie
import platform.Foundation.NSHTTPCookieStorage
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

private class IosCookieManager : WebViewCookieManager {
    override fun getCookie(url: String): String {
        val nsUrl = NSURL.URLWithString(url) ?: return ""
        val cookies = NSHTTPCookieStorage.sharedHTTPCookieStorage.cookiesForURL(nsUrl) ?: return ""
        return cookies.joinToString(separator = "; ") { cookie ->
            val c = cookie as? NSHTTPCookie ?: return@joinToString ""
            "${c.name}=${c.value}"
        }
    }

    override fun removeAllCookies() {
        val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
        val cookies = storage.cookies ?: return
        cookies.forEach { cookie ->
            val c = cookie as? NSHTTPCookie ?: return@forEach
            storage.deleteCookie(c)
        }
    }
}

actual fun createWebViewCookieManager(): WebViewCookieManager = IosCookieManager()

actual fun clearWebViewCacheAndCookies() {
    val storage = NSHTTPCookieStorage.sharedHTTPCookieStorage
    val cookies = storage.cookies ?: return
    cookies.forEach { cookie ->
        val c = cookie as? NSHTTPCookie ?: return@forEach
        storage.deleteCookie(c)
    }
}

private class WebViewNavDelegate(
    private val state: MutableState<WebViewState>,
    private val onPageFinished: (String) -> Unit,
) : NSObject(), WKNavigationDelegateProtocol {
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        state.value = WebViewState.Finished
        onPageFinished(webView.URL?.absoluteString ?: "")
    }
}

@Composable
actual fun PlatformWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onPageFinished: (String) -> Unit,
) {
    val delegate = remember(state, onPageFinished) { WebViewNavDelegate(state, onPageFinished) }
    Box(modifier = Modifier.fillMaxSize()) {
        UIKitView(
            factory = {
                val config = WKWebViewConfiguration()
                val webView = WKWebView(frame = platform.CoreGraphics.CGRectMake(0.0, 0.0, 1.0, 1.0), configuration = config)
                webView.navigationDelegate = delegate
                val nsUrl = NSURL.URLWithString(initUrl)
                if (nsUrl != null) webView.loadRequest(NSURLRequest(uRL = nsUrl))
                webView
            },
            modifier = Modifier.fillMaxSize(),
        )
        aboveContent()
        DisposableEffect(delegate) {
            onDispose { }
        }
    }
}

@Composable
actual fun DiscordWebView(
    state: MutableState<WebViewState>,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onLoginDone: (token: String) -> Unit,
) {
    PlatformWebView(
        state = state,
        initUrl = "https://discord.com/login",
        aboveContent = aboveContent,
        onPageFinished = { finishedUrl ->
            if (finishedUrl.contains("/channels/@me") || finishedUrl.contains("/app")) {
                val tokenCookie = NSHTTPCookieStorage.sharedHTTPCookieStorage.cookies?.firstOrNull { raw ->
                    val c = raw as? NSHTTPCookie ?: return@firstOrNull false
                    c.name == "__Secure-authToken" || c.name == "token"
                } as? NSHTTPCookie
                val tokenValue = tokenCookie?.value
                if (!tokenValue.isNullOrEmpty()) {
                    onLoginDone(tokenValue)
                }
            }
        },
    )
}
