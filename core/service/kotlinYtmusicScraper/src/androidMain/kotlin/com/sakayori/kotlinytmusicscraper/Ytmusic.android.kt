package com.sakayori.kotlinytmusicscraper

import java.util.*

actual fun getCountry(): String = Locale.getDefault().country
actual fun getLanguage(): String = Locale.getDefault().language
