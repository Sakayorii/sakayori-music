/*
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakayori.music.db.entities.LyricsEntity
import kotlinx.coroutines.flow.StateFlow

/** Use the current row immediately; remounting must not invent a new loading state. */
@Composable
internal fun StateFlow<LyricsEntity?>.collectLyricsAsState(): State<LyricsEntity?> =
    collectAsStateWithLifecycle()

/** A remounted pane must see the existing queue and prefetch owner immediately. */
@Composable
internal fun <T> StateFlow<List<T>>.collectLyricsQueueAsState(): State<List<T>> =
    collectAsStateWithLifecycle()

@Composable
internal fun StateFlow<Int>.collectLyricsWindowAsState(): State<Int> =
    collectAsStateWithLifecycle()
