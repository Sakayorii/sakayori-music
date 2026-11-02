package com.sakayori.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class PreloadReadBudgetTest {
    @Test
    fun `reads are limited by remaining bytes across transfers`() {
        val budget = PreloadReadBudget(8)
        assertEquals(8, budget.awaitReadLength(100))
        budget.onBytesRead(5)
        assertEquals(3, budget.awaitReadLength(100))
        budget.onBytesRead(-1)
        assertEquals(3, budget.awaitReadLength(100))
    }

    @Test
    fun `a depleted preload resumes without a false EOF when promoted`() {
        val budget = PreloadReadBudget(1)
        budget.onBytesRead(1)
        val executor = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        try {
            val read = executor.submit<Int> {
                entered.countDown()
                budget.awaitReadLength(1024)
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertFalse(read.isDone)
            budget.promote()
            assertEquals(1024, read.get(1, TimeUnit.SECONDS).toInt())
        } finally {
            budget.cancel()
            executor.shutdownNow()
        }
    }

    @Test
    fun `foreground loading pauses speculative reads and then releases them`() {
        val budget = PreloadReadBudget(8)
        budget.setForegroundLoading(true)
        val executor = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        try {
            val read = executor.submit<Int> {
                entered.countDown()
                budget.awaitReadLength(4)
            }
            assertTrue(entered.await(1, TimeUnit.SECONDS))
            assertFalse(read.isDone)
            budget.setForegroundLoading(false)
            assertEquals(4, read.get(1, TimeUnit.SECONDS).toInt())
        } finally {
            budget.cancel()
            executor.shutdownNow()
        }
    }

    @Test
    fun `cancelling a depleted preload releases its waiting loader`() {
        val budget = PreloadReadBudget(1)
        budget.onBytesRead(1)
        val executor = Executors.newSingleThreadExecutor()
        try {
            val read = executor.submit<Boolean> {
                try {
                    budget.awaitReadLength(4)
                    false
                } catch (_: InterruptedIOException) {
                    true
                }
            }
            budget.cancel()
            assertTrue(read.get(1, TimeUnit.SECONDS))
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `a zero length read does not wait on an exhausted budget`() {
        val budget = PreloadReadBudget(1)
        budget.onBytesRead(1)
        assertEquals(0, budget.awaitReadLength(0))
    }
}
