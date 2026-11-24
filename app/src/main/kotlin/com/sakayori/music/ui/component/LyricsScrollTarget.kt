package com.sakayori.music.ui.component

import com.sakayori.music.lyrics.LyricsEntry

/** Temporal cursor admission used by the positioned renderer, not row geometry. */
internal fun resolveLyricsScrollTarget(
    lines: List<LyricsEntry>,
    activeIndices: Set<Int>,
    scrollMax: Int,
    target: Int,
    previousActiveIndices: Set<Int>,
    lastMainMaxSeen: Int,
    effectivePositionMs: Long,
    previousRawPositionMs: Long,
    rawPositionMs: Long,
    sliderSeeking: Boolean,
): Int {
    if (nativeLyricsPositionMovedBackwards(previousRawPositionMs, rawPositionMs, sliderSeeking)) {
        if (scrollMax in lines.indices && lines[scrollMax].text.isNotBlank()) return scrollMax
        val preceding = lastStartedLyricsMainIndex(lines, effectivePositionMs)
        return (preceding + 1 until lines.size).firstOrNull {
            !lines[it].isBackground && lines[it].text.isNotBlank()
        } ?: preceding.takeIf { it >= 0 } ?: target
    }
    val targetStillActive = target in activeIndices
    val anyActive = activeIndices.isNotEmpty()
    val shouldScroll = when {
        sliderSeeking -> true
        !targetStillActive && anyActive && scrollMax > target -> true
        !targetStillActive && !anyActive && previousActiveIndices.isNotEmpty() -> true
        target == -1 && anyActive -> true
        previousActiveIndices.isEmpty() && anyActive && scrollMax > target -> true
        else -> false
    }
    if (!shouldScroll) return target
    val candidate = when {
        sliderSeeking -> scrollMax
        !targetStillActive && anyActive -> scrollMax
        !targetStillActive && !anyActive ->
            (lastMainMaxSeen + 1 until lines.size).firstOrNull {
                !lines[it].isBackground
            } ?: target
        else -> scrollMax
    }
    return if (candidate != -1 && (sliderSeeking || candidate > target)) candidate else target
}

internal fun nativeLyricsPositionMovedBackwards(previousRaw: Long, raw: Long, sliderSeeking: Boolean): Boolean =
    !sliderSeeking && raw < previousRaw

internal fun lastStartedLyricsMainIndex(lines: List<LyricsEntry>, effectivePositionMs: Long): Int =
    lines.indexOfLast { !it.isBackground && it.text.isNotBlank() && it.time <= effectivePositionMs }

internal fun resolveLyricsMainFrontier(
    lines: List<LyricsEntry>, scrollMax: Int, previous: Int,
    effectivePositionMs: Long, nativeBackwards: Boolean,
): Int = when {
    nativeBackwards -> lastStartedLyricsMainIndex(lines, effectivePositionMs)
    scrollMax > previous && scrollMax != -1 -> scrollMax
    else -> previous
}
