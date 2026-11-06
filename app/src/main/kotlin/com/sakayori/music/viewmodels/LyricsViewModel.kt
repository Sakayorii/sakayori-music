/**
 * Sakayori Music Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.sakayori.music.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakayori.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.sakayori.music.lyrics.LyricsEntry
import com.sakayori.music.lyrics.LyricsUtils
import com.sakayori.music.ui.component.LyricsListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LyricsViewModel @Inject constructor() : ViewModel() {
    companion object {
        private val timestampRegex = Regex("\\[\\d{1,2}:\\d{2}")
    }

    private data class ProcessKey(
        val lyrics: String?,
        val enabledLanguages: List<String>,
        val romanizeCyrillicByLine: Boolean,
        val showIntervalIndicator: Boolean,
    )

    private var lastProcessedKey: ProcessKey? = null
    private var processJob: kotlinx.coroutines.Job? = null

    private val _lines = MutableStateFlow<List<LyricsEntry>>(emptyList())
    val lines: StateFlow<List<LyricsEntry>> = _lines.asStateFlow()

    private val _mergedLyricsList = MutableStateFlow<List<LyricsListItem>>(emptyList())
    val mergedLyricsList: StateFlow<List<LyricsListItem>> = _mergedLyricsList.asStateFlow()

    fun processLyrics(
        lyrics: String?,
        enabledLanguages: List<String>,
        romanizeCyrillicByLine: Boolean,
        showIntervalIndicator: Boolean
    ) {
        val key = ProcessKey(lyrics, enabledLanguages, romanizeCyrillicByLine, showIntervalIndicator)
        if (key == lastProcessedKey) {
            return
        }
        lastProcessedKey = key
        processJob?.cancel()
        processJob = viewModelScope.launch {
            val (processedLines, mergedList) = withContext(Dispatchers.Default) {
                val parsed = if (lyrics == null || lyrics == LYRICS_NOT_FOUND) {
                    emptyList()
                } else {
                    val isLrc = timestampRegex.containsMatchIn(lyrics)
                    val parsedLines = if (isLrc) LyricsUtils.parseLyrics(lyrics) else emptyList()
                    
                    if (parsedLines.isNotEmpty()) {
                        listOf(LyricsEntry.HEAD_LYRICS_ENTRY) + parsedLines
                    } else {
                        // Fallback for unsynced or invalid LRC
                        val baseTime = 1000000L
                        lyrics.lines()
                            .filter { it.isNotBlank() && !timestampRegex.containsMatchIn(it) }
                            .mapIndexed { index, line ->
                                LyricsEntry(baseTime + index, line)
                            }
                    }
                }
                parsed to buildMergedList(parsed, showIntervalIndicator)
            }
            
            _lines.value = processedLines
            _mergedLyricsList.value = mergedList

            // Romanize in the background after the UI has been updated
            if (lyrics != null && lyrics != LYRICS_NOT_FOUND && enabledLanguages.isNotEmpty()) {
                launch(Dispatchers.Default) {
                    processedLines.forEach { entry ->
                        if (entry == LyricsEntry.HEAD_LYRICS_ENTRY) return@forEach
                        entry.romanizedTextFlow.value = LyricsUtils.romanize(
                            text = lyrics,
                            line = entry.text,
                            enabledLanguages = enabledLanguages,
                            romanizeCyrillicByLine = romanizeCyrillicByLine
                        )
                    }
                }
            }
        }
    }

    private fun buildMergedList(lines: List<LyricsEntry>, showIntervalIndicator: Boolean): List<LyricsListItem> {
        val result = mutableListOf<LyricsListItem>()
        if (lines.isEmpty()) {
            return result
        }
        lines.forEachIndexed { i, entry ->
            if (entry.text.isNotBlank()) {
                result.add(LyricsListItem.Line(i, entry))
            }
            if (showIntervalIndicator && i < lines.size - 1) {
                val nextStart = lines[i + 1].time
                val currentEnd = if (!entry.words.isNullOrEmpty()) {
                    (entry.words.last().endTime * 1000).toLong()
                } else if (entry.text.isBlank()) {
                    entry.time
                } else {
                    null
                }

                if (currentEnd != null && currentEnd < nextStart) {
                    val gap = nextStart - currentEnd
                    if (gap > 4000L) {
                        result.add(LyricsListItem.Indicator(i, gap, currentEnd, nextStart, lines[i + 1].agent))
                    }
                }
            }
        }
        return result
    }
}
