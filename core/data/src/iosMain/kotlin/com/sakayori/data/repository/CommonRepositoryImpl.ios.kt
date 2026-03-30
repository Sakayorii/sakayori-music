package com.sakayori.data.repository

import com.sakayori.domain.data.model.cookie.CookieItem

actual fun getCookies(url: String, packageName: String): CookieItem = CookieItem(url, emptyList())
