/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.supervisorScope

/** Primary audio is never held for a pending optional result. The optional owner caps its lookup. */
internal suspend fun <T : Any, P : Any> selectOptionalAudio(
    optional: Deferred<P?>?,
    convert: (P) -> T,
    normal: suspend () -> T,
): T = supervisorScope {
    if (optional == null) return@supervisorScope normal()
    val preferred = async(start = CoroutineStart.UNDISPATCHED) {
        try {
            val value = optional.await()
            value?.let(convert)
        } catch (cancelled: CancellationException) {
            currentCoroutineContext().ensureActive()
            null
        } catch (_: Exception) {
            null
        }
    }
    val fallback = async {
        try {
            Result.success(normal())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    try {
        select {
            preferred.onAwait { it ?: fallback.await().getOrThrow() }
            fallback.onAwait { result ->
                if (result.isSuccess) {
                    if (preferred.isCompleted) preferred.await() ?: result.getOrThrow() else result.getOrThrow()
                } else {
                    preferred.await() ?: result.getOrThrow()
                }
            }
        }
    } finally {
        preferred.cancel()
        fallback.cancel()
        // The optional job belongs to a bounded application lookup. It may finish warming this
        // selected track after normal playback wins. An owning service may prepare a separate
        // pinned period for an audible handoff; this reader never mixes encoded renditions.
    }
}
