/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.playback

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSourceException

/** The current byte stream cannot safely resume; playback must make a new selection. */
internal class RenditionUnavailableException(message: String) :
    DataSourceException(message, null, PlaybackException.ERROR_CODE_REMOTE_ERROR)
