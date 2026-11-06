/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.discord

import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.sakayori.music.utils.YouTubeArtworkInterceptor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** External asset services need the available URL, not only Coil's decoded fallback. */
internal suspend fun resolveDiscordArtworkUrl(
    url: String,
    load: suspend (String) -> ImageResult,
): String? {
    if (!YouTubeArtworkInterceptor.supports(url)) return url
    val result = load(url)
    currentCoroutineContext().ensureActive()
    return when (result) {
        is SuccessResult -> result.request.data.toString()
        is ErrorResult -> {
            if (result.throwable is CancellationException) throw result.throwable
            null
        }
    }
}
