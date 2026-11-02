package com.sakayori.music.playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

class OptionalAudioSelectionTest {
    @Test
    fun `a ready optional stream wins without waiting for normal resolution`() = runBlocking {
        val optional = CompletableDeferred<String?>("flac")
        val result = withTimeout(1_000) { selectOptionalAudio(optional, { it }) { awaitCancellation() } }
        assertEquals("flac", result)
    }

    @Test
    fun `ready normal audio never waits for a slow optional lookup`() = runBlocking {
        val optional = CompletableDeferred<String?>()
        try {
            val result = withTimeout(1_000) { selectOptionalAudio(optional, { it }) { "normal" } }
            assertEquals("normal", result)
            assertFalse(optional.isCancelled)
        } finally {
            optional.cancel()
        }
    }

    @Test
    fun `missing optional audio falls back to the normal source`() = runBlocking {
        assertEquals("normal", selectOptionalAudio(CompletableDeferred<String?>(value = null), { it }) { "normal" })
    }

    @Test
    fun `failed or cancelled optional lookup does not fail normal playback`() = runBlocking {
        val failed = CompletableDeferred<String?>().apply { completeExceptionally(IOException("fixture")) }
        assertEquals("normal", selectOptionalAudio(failed, { it }) { "normal" })
        val cancelled = CompletableDeferred<String?>().apply { cancel() }
        assertEquals("normal", selectOptionalAudio(cancelled, { it }) { "normal" })
    }

    @Test
    fun `a normal-source failure does not cancel a usable optional result`() = runBlocking {
        assertEquals("flac", selectOptionalAudio(CompletableDeferred<String?>("flac"), { it }) {
            throw IOException("normal source unavailable")
        })
    }
}
