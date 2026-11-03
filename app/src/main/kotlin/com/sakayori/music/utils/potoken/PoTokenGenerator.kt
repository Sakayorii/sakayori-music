package com.sakayori.music.utils.potoken

import android.webkit.CookieManager
import com.sakayori.music.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

class PoTokenGenerator {
    private val TAG = "PoTokenGenerator"

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    @Volatile private var webViewBadImpl = false // whether the system has a bad WebView implementation

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        currentCoroutineContext().ensureActive()
        Timber.tag(TAG).d("getWebClientPoToken called: videoId=$videoId, sessionId=$sessionId")
        Timber.tag(TAG).d("WebView state: supported=$webViewSupported, badImpl=$webViewBadImpl")
        if (!webViewSupported || webViewBadImpl) {
            Timber.tag(TAG).d("WebView not available: supported=$webViewSupported, badImpl=$webViewBadImpl")
            return null
        }

        var attemptedGenerator: PoTokenWebView? = null
        return try {
            withTimeout(POTOKEN_TIMEOUT_MS) {
                getWebClientPoToken(videoId, sessionId, forceRecreate = false) { attemptedGenerator = it }
            }
        } catch (e: TimeoutCancellationException) {
            // A cancelled parent (skip or a winning lossless source) is not our own timeout.
            currentCoroutineContext().ensureActive()
            // The WebView's sandboxed process can be culled by the OS (storage pressure, low
            // memory, etc.) which leaves the PoToken WebView call hung indefinitely. Cap it so
            // playerResponseForPlayback can fall through to non-PoToken fallback clients (e.g.
            // ANDROID_VR) instead of blocking the entire playback path.
            Timber.tag(TAG).w("poToken generation timed out after ${POTOKEN_TIMEOUT_MS}ms; proceeding without PoToken")
            // A caller that timed out while waiting for the shared generator lock never owned
            // an instance. Do not wait for another caller's initialization just to do no cleanup.
            attemptedGenerator?.let { attempted ->
                webPoTokenGenLock.withLock {
                    // A concurrent caller may already have replaced the timed-out instance.
                    // Never tear down a newer successful generator during this caller's cleanup.
                    if (webPoTokenGenerator === attempted) {
                        val stale = webPoTokenGenerator
                        webPoTokenGenerator = null
                        webPoTokenStreamingPot = null
                        webPoTokenSessionId = null
                        try {
                            // close() posts WebView teardown to Main without delaying this fallback.
                            stale?.close()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (closeEx: Exception) {
                            Timber.tag(TAG).e(closeEx, "Exception closing PoTokenWebView during timeout cleanup")
                        }
                    }
                }
            }
            null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "poToken generation exception: ${e.javaClass.simpleName}: ${e.message}")
            when (e) {
                is BadWebViewException -> {
                    Timber.tag(TAG).e(e, "Could not obtain poToken because WebView is broken")
                    webViewBadImpl = true
                    null
                }
                else -> throw e // includes PoTokenException
            }
        }
    }

    private companion object {
        // Healthy cold-start (WebView spin-up + botguard JS + token gen) is ~2–5s in practice;
        // 8s leaves slack for a slow device without making the user wait too long before the
        // fallback chain (ANDROID_VR, etc.) takes over when the WebView hangs.
        const val POTOKEN_TIMEOUT_MS = 8_000L
    }

    /**
     * @param forceRecreate whether to force the recreation of [webPoTokenGenerator], to be used in
     * case the current [webPoTokenGenerator] threw an error last time
     * [PoTokenWebView.generatePoToken] was called
     */
    private suspend fun getWebClientPoToken(
        videoId: String,
        sessionId: String,
        forceRecreate: Boolean,
        onGenerator: (PoTokenWebView) -> Unit,
    ): PoTokenResult {
        currentCoroutineContext().ensureActive()
        Timber.tag(TAG).d("Web poToken requested: videoId=$videoId, sessionId=$sessionId")

        val (poTokenGenerator, streamingPot, hasBeenRecreated) =
            webPoTokenGenLock.withLock {
                val shouldRecreate =
                    forceRecreate || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired ||
                        // Renderer died (OOM kill) — recreate proactively instead of letting the
                        // first post-crash generatePoToken() fail against the dead instance.
                        webPoTokenGenerator!!.isDead ||
                        webPoTokenSessionId != sessionId

                if (shouldRecreate) {
                    Timber.tag(TAG).d("Creating new PoTokenWebView (forceRecreate=$forceRecreate)")

                    // Clear the committed state BEFORE the fallible steps below: if creation or
                    // the streaming-pot mint throws, the next call must compute
                    // shouldRecreate=true instead of pairing the already-updated sessionId with
                    // a null/stale streaming pot at the Triple below.
                    val previous = webPoTokenGenerator
                    webPoTokenGenerator = null
                    webPoTokenStreamingPot = null
                    webPoTokenSessionId = null
                    withContext(Dispatchers.Main) { previous?.close() }

                    val newGenerator = PoTokenWebView.getNewPoTokenGenerator(CipherDeobfuscator.appContext)
                    onGenerator(newGenerator)

                    // The streaming poToken needs to be generated exactly once before generating
                    // any other (player) tokens.
                    val newStreamingPot = try {
                        newGenerator.generatePoToken(sessionId)
                    } catch (t: Throwable) {
                        // Don't leak the freshly created WebView (close() hops to Main itself).
                        runCatching { newGenerator.close() }
                        throw t
                    }

                    webPoTokenGenerator = newGenerator
                    webPoTokenStreamingPot = newStreamingPot
                    webPoTokenSessionId = sessionId
                    Timber.tag(TAG).d("Streaming poToken generated for sessionId=${sessionId.take(20)}...")
                }

                Triple(webPoTokenGenerator!!, webPoTokenStreamingPot!!, shouldRecreate)
            }
        onGenerator(poTokenGenerator)

        val playerPot = try {
            poTokenGenerator.generatePoToken(videoId)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (throwable: Throwable) {
            if (hasBeenRecreated) {
                // the poTokenGenerator has just been recreated (and possibly this is already the
                // second time we try), so there is likely nothing we can do
                throw throwable
            } else {
                // retry, this time recreating the [webPoTokenGenerator] from scratch;
                // this might happen for example if the app goes in the background and the WebView
                // content is lost
                Timber.tag(TAG).e(throwable, "Failed to obtain poToken, retrying")
                return getWebClientPoToken(videoId = videoId, sessionId = sessionId, forceRecreate = true, onGenerator = onGenerator)
            }
        }

        Timber.tag(TAG).d("poToken generated successfully: session=${streamingPot.take(20)}..., video=${playerPot.take(20)}...")

        return PoTokenResult(
            playerRequestPoToken = streamingPot,
            streamingDataPoToken = playerPot,
        )
    }
}
