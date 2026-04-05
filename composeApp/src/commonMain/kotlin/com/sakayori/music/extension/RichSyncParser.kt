package com.sakayori.music.extension

import com.sakayori.domain.extension.decodeHtmlEntities

data class WordTiming(
    val text: String,
    val startTimeMs: Long,
)

data class ParsedRichSyncLine(
    val words: List<WordTiming>,
    val lineStartTimeMs: Long,
    val lineEndTimeMs: Long,
)

fun parseRichSyncWords(
    words: String,
    lineStartTimeMs: String,
    lineEndTimeMs: String,
): ParsedRichSyncLine? {
    if (words.isBlank()) return null

    val timestampRegex = Regex("""<(\d{2}):(\d{2})\.(\d{2,3})>""")
    val wordTimings = mutableListOf<WordTiming>()
    val timestamps = timestampRegex.findAll(words).toList()

    timestamps.forEachIndexed { index, match ->
        val (minutes, seconds, fraction) = match.destructured

        val fractionMs = fraction.toLongOrNull() ?: 0L
        val timeMs =
            (minutes.toLongOrNull() ?: 0L) * 60000L +
                (seconds.toLongOrNull() ?: 0L) * 1000L +
                if (fraction.length == 2) fractionMs * 10L else fractionMs

        val startPos = match.range.last + 1
        val endPos =
            if (index < timestamps.size - 1) {
                timestamps[index + 1].range.first
            } else {
                words.length
            }

        val textBetween = words.substring(startPos, endPos).trim()

        if (textBetween.isNotBlank()) {
            wordTimings.add(WordTiming(text = decodeHtmlEntities(textBetween), startTimeMs = timeMs))
        }
    }

    if (wordTimings.isEmpty()) return null

    val lineStart = lineStartTimeMs.toLongOrNull() ?: 0L
    val lineEnd = lineEndTimeMs.toLongOrNull() ?: Long.MAX_VALUE

    return ParsedRichSyncLine(
        words = wordTimings,
        lineStartTimeMs = lineStart,
        lineEndTimeMs = lineEnd,
    )
}
