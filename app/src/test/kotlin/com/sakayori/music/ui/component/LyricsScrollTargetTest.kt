package com.sakayori.music.ui.component

import android.app.Application
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import com.sakayori.music.lyrics.LyricsEntry
import com.sakayori.music.lyrics.LyricsUtils.findActiveLineIndices
import com.sakayori.music.lyrics.WordTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LyricsScrollTargetTest {
    private val before = cue(164170, 164.172, 174.471)
    private val early = cue(177970, 177.972, 188.121)
    private val late = cue(219060, 219.061, 225.262)
    private val following = cue(232640, 232.649, 238.932)
    private val lines = listOf(LyricsEntry.HEAD_LYRICS_ENTRY, before, early, late, following)

    @Test
    fun `real paused backwards movement admits the earlier active cue`() {
        val player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Application>()).build()
        try {
            player.setMediaItem(MediaItem.Builder().setMediaId("fixture").setUri("https://example.test/audio").build())
            player.seekTo(219605)
            val oldRaw = player.currentPosition
            val oldActive = findActiveLineIndices(lines, oldRaw)
            assertEquals(setOf(lines.indexOf(late)), oldActive)
            val target = resolveLyricsScrollTarget(
                lines, oldActive, oldActive.maxOrNull()!!, -1, emptySet(), -1,
                oldRaw, oldRaw, oldRaw, false,
            )
            assertEquals(lines.indexOf(late), target)

            player.seekTo(179605)
            val newRaw = player.currentPosition
            assertTrue(newRaw < oldRaw)
            val active = findActiveLineIndices(lines, newRaw)
            assertEquals(setOf(lines.indexOf(early)), active)
            assertEquals(
                "Native backwards seek must rebase the production cursor, without slider preview",
                lines.indexOf(early),
                resolveLyricsScrollTarget(
                    lines, active, active.maxOrNull()!!, target, oldActive, target,
                    newRaw, oldRaw, newRaw, false,
                ),
            )
        } finally {
            player.release()
        }
    }

    @Test
    fun `backwards gap selects the local next main rather than the stale future`() {
        val active = findActiveLineIndices(lines, 174605)
        assertTrue(active.isEmpty())
        assertEquals(
            lines.indexOf(early),
            resolveLyricsScrollTarget(
                lines, active, -1, lines.indexOf(late), setOf(lines.indexOf(late)), lines.indexOf(late),
                174605, 219605, 174605, false,
            ),
        )
        assertEquals(lines.indexOf(before), lastStartedLyricsMainIndex(lines, 174605))
    }

    @Test
    fun `forward raw clock is not a rewind when effective interpolation or offset is lower`() {
        val active = findActiveLineIndices(lines, 179605)
        assertEquals(
            lines.indexOf(late),
            resolveLyricsScrollTarget(
                lines, active, active.maxOrNull()!!, lines.indexOf(late), setOf(lines.indexOf(late)), lines.indexOf(late),
                179605, 219605, 219610, false,
            ),
        )
    }

    @Test
    fun `slider preview still admits a lower active cue without native movement`() {
        val active = findActiveLineIndices(lines, 179605)
        assertEquals(
            lines.indexOf(early),
            resolveLyricsScrollTarget(
                lines, active, active.maxOrNull()!!, lines.indexOf(late), setOf(lines.indexOf(late)), lines.indexOf(late),
                179605, 219605, 219605, true,
            ),
        )
    }

    @Test
    fun `native rewind resolves the effective lyric clock with its offset`() {
        val active = findActiveLineIndices(lines, 182605)
        assertEquals(
            lines.indexOf(early),
            resolveLyricsScrollTarget(
                lines, active, active.maxOrNull()!!, lines.indexOf(late), setOf(lines.indexOf(late)), lines.indexOf(late),
                182605, 219605, 179605, false,
            ),
        )
    }

    @Test
    fun `rewind before first visible cue does not anchor on the blank head`() {
        val active = findActiveLineIndices(lines, 0)
        assertEquals(
            lines.indexOf(before),
            resolveLyricsScrollTarget(
                lines, active, active.maxOrNull() ?: -1, lines.indexOf(late), setOf(lines.indexOf(late)), lines.indexOf(late),
                0, 219605, 0, false,
            ),
        )
    }

    @Test
    fun `rewind in the tail retains the last main cue`() {
        assertEquals(
            lines.indexOf(following),
            resolveLyricsScrollTarget(
                lines, emptySet(), -1, lines.indexOf(late), setOf(lines.indexOf(late)), lines.indexOf(late),
                240000, 260000, 240000, false,
            ),
        )
    }

    @Test
    fun `ordinary forward progress keeps its existing admission`() {
        val active = findActiveLineIndices(lines, 233000)
        assertEquals(
            lines.indexOf(following),
            resolveLyricsScrollTarget(
                lines, active, active.maxOrNull()!!, lines.indexOf(late), setOf(lines.indexOf(late)), lines.indexOf(late),
                233000, 219605, 233000, false,
            ),
        )
    }

    @Test
    fun `overlap rewind keeps the active target but skips an expired later cue in the next gap`() {
        val longActive = cue(100000, 100.0, 300.0)
        val expiredLater = cue(150000, 150.0, 200.0)
        val future = cue(400000, 400.0, 410.0)
        val overlap = listOf(LyricsEntry.HEAD_LYRICS_ENTRY, longActive, expiredLater, future)
        val active = findActiveLineIndices(overlap, 250000)
        assertEquals(setOf(overlap.indexOf(longActive)), active)
        val target = resolveLyricsScrollTarget(
            overlap, active, active.maxOrNull()!!, overlap.indexOf(future), setOf(overlap.indexOf(future)), overlap.indexOf(future),
            250000, 450000, 250000, false,
        )
        assertEquals(overlap.indexOf(longActive), target)
        val frontier = resolveLyricsMainFrontier(
            overlap, active.maxOrNull()!!, overlap.indexOf(future), 250000, true,
        )
        val gap = findActiveLineIndices(overlap, 301000)
        assertTrue(gap.isEmpty())
        assertEquals(
            "After the active overlap ends, the next gap must not revisit an expired later cue",
            overlap.indexOf(future),
            resolveLyricsScrollTarget(
                overlap, gap, -1, target, active, frontier,
                301000, 250000, 301000, false,
            ),
        )
    }

    private fun cue(time: Long, start: Double, end: Double) = LyricsEntry(
        time, "cue-$time", words = listOf(WordTimestamp("cue", start, end)),
    )
}
