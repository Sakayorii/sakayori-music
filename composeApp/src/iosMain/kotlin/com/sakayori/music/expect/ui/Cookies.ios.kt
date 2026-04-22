package com.sakayori.music.expect.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState

private object NoopWebViewCookieManager : WebViewCookieManager {
    override fun getCookie(url: String): String = ""
    override fun removeAllCookies() {}
}

actual fun createWebViewCookieManager(): WebViewCookieManager = NoopWebViewCookieManager

actual fun clearWebViewCacheAndCookies() {}

@Composable
actual fun PlatformWebView(
    state: MutableState<WebViewState>,
    initUrl: String,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onPageFinished: (String) -> Unit,
) {}

@Composable
actual fun DiscordWebView(
    state: MutableState<WebViewState>,
    aboveContent: @Composable (BoxScope.() -> Unit),
    onLoginDone: (token: String) -> Unit,
) {}
