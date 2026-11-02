package com.sakayori.innertube.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Search response parsing is main-safe, and cancellation must never become a cached failure. */
internal suspend fun <T> runOnIoCatching(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
    try {
        val value = block()
        currentCoroutineContext().ensureActive()
        Result.success(value)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
