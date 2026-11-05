/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

/** Request identity and successful initial commit, not transport next capability. */
internal data class QueueLoadState(
    val generation: Int = 0,
    val committed: Boolean = false,
)
