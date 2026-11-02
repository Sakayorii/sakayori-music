/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback.flac

import java.io.IOException

/** The optional transport should hand off to the normal source, rather than retrying in-place. */
internal class LosslessStreamException(cause: IOException) : IOException("Optional lossless stream unavailable", cause)

internal fun Throwable.isLosslessStreamFailure(): Boolean {
    var error: Throwable? = this
    repeat(16) {
        if (error is LosslessStreamException) return true
        error = error?.cause ?: return false
    }
    return false
}
