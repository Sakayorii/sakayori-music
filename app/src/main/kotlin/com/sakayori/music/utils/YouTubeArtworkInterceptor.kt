/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.utils

import coil3.intercept.Interceptor
import coil3.network.HttpException
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import coil3.size.Dimension
import coil3.size.Size
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Resolve optional ytimg variants without corrupting the source URL in metadata. */
internal class YouTubeArtworkInterceptor : Interceptor {
    private val availableFallbacks = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 128
    }

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val original = chain.request.data.toString()
        val match = thumbnailPattern.matchEntire(original) ?: return chain.proceed()
        val prefix = match.groupValues[1]
        val variant = match.groupValues[2]
        val extension = match.groupValues[3]
        val large = chain.size == Size.ORIGINAL || maxOf(
            (chain.size.width as? Dimension.Pixels)?.px ?: 0,
            (chain.size.height as? Dimension.Pixels)?.px ?: 0,
        ) >= 544
        val cacheKey = "$large:$original"
        val cached = synchronized(availableFallbacks) { availableFallbacks[cacheKey] }
        val candidates = buildList {
            cached?.let(::add)
            if (!large || variant == "maxresdefault") add(original)
            if (large || variant == "maxresdefault") add("${prefix}maxresdefault.$extension")
            if (large && variant == "sddefault") add(original)
            if (large || variant == "maxresdefault" || variant == "sddefault") add("${prefix}sddefault.$extension")
            if (large && variant == "hqdefault") add(original)
            add("${prefix}hqdefault.$extension")
            if (large && variant == "mqdefault") add(original)
            add("${prefix}mqdefault.$extension")
            add(original)
            add("${prefix}default.$extension")
        }.distinct()

        var failure: ImageResult? = null
        for (url in candidates) {
            currentCoroutineContext().ensureActive()
            val request = if (url == original) chain.request else chain.request.newBuilder().data(url).build()
            val result = chain.withRequest(request).proceed()
            currentCoroutineContext().ensureActive()
            if (result is SuccessResult) {
                if (url != candidates.first() || cached != null) {
                    synchronized(availableFallbacks) { availableFallbacks[cacheKey] = url }
                }
                return result
            }
            val error = (result as? ErrorResult)?.throwable
            if (error is CancellationException) throw error
            failure = result
            // Only an unavailable asset permits changing variants. Do not turn
            // authentication, network or decoder errors into unrelated requests.
            if (error !is HttpException || error.response.code !in setOf(404, 410)) return result
        }
        return checkNotNull(failure)
    }

    companion object {
        private val thumbnailPattern = Regex(
            "^(https://i\\.ytimg\\.com/(?:vi|vi_webp)/[A-Za-z0-9_-]{11}/)(default|mqdefault|hqdefault|sddefault|maxresdefault)\\.(jpg|webp)(?:\\?.*)?$",
        )

        fun supports(url: String?): Boolean = url != null && thumbnailPattern.matches(url)
    }
}
