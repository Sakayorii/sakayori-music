package org.SakayoriMusic.aiservice

import com.sakayori.domain.data.model.metadata.Lyrics

class AiClient {
    private var aiService: AiService? = null
    var host = AIHost.GEMINI
        set(value) {
            field = value
            rebuildAiService()
        }
    var apiKey: String? = null
        set(value) {
            field = value
            rebuildAiService()
        }
    var customModelId: String? = null
        set(value) {
            field = value
            rebuildAiService()
        }
    var customBaseUrl: String? = null
        set(value) {
            field = value
            rebuildAiService()
        }
    var customHeaders: Map<String, String>? = null
        set(value) {
            field = value
            rebuildAiService()
        }

    private fun rebuildAiService() {
        aiService =
            if (apiKey != null) {
                AiService(
                    aiHost = host,
                    apiKey = apiKey ?: return,
                    customModelId = customModelId,
                    customBaseUrl = customBaseUrl,
                    customHeaders = customHeaders,
                )
            } else {
                null
            }
    }

    suspend fun translateLyrics(
        inputLyrics: Lyrics,
        targetLanguage: String,
    ): Result<Lyrics> =
        runCatching {
            aiService?.translateLyrics(inputLyrics, targetLanguage).also { data ->
                if (data?.lines?.map { it.words }?.containsAll(
                        inputLyrics.lines?.map { it.words } ?: emptyList(),
                    ) == true
                ) {
                    throw IllegalStateException("Translation failed or returned empty lyrics.")
                }
            }
                ?: throw IllegalStateException("AI service is not initialized. Please set host and apiKey.")
        }
}
