package org.SakayoriMusic.aiservice

import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatResponseFormat
import com.aallam.openai.api.chat.JsonSchema
import com.aallam.openai.api.chat.chatCompletionRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import com.aallam.openai.client.OpenAIHost.Companion.Gemini
import com.sakayori.domain.data.model.metadata.Lyrics
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

class AiService(
    private val aiHost: AIHost = AIHost.GEMINI,
    private val apiKey: String,
    private val customModelId: String? = null,
    private val customBaseUrl: String? = null,
    private val customHeaders: Map<String, String>? = null,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            explicitNulls = false
        }
    private val openAI: OpenAI by lazy {
        when (aiHost) {
            AIHost.GEMINI -> {
                OpenAI(host = Gemini, token = apiKey)
            }

            AIHost.OPENAI -> {
                OpenAI(token = apiKey)
            }

            AIHost.CUSTOM_OPENAI -> {
                val baseUrl = customBaseUrl ?: "https://api.openai.com/v1/"
                val config =
                    OpenAIConfig(
                        token = apiKey,
                        host = OpenAIHost(baseUrl = baseUrl),
                        headers = customHeaders ?: emptyMap(),
                    )
                OpenAI(config)
            }
        }
    }

    private val model by lazy {
        if (!customModelId.isNullOrEmpty()) {
            ModelId(customModelId)
        } else {
            when (aiHost) {
                AIHost.GEMINI -> ModelId("gemini-2.0-flash")
                AIHost.OPENAI -> ModelId("gpt-4o")
                AIHost.CUSTOM_OPENAI -> ModelId("gpt-4o")
            }
        }
    }

    suspend fun translateLyrics(
        inputLyrics: Lyrics,
        targetLanguage: String,
    ): Lyrics {
        val request =
            chatCompletionRequest {
                this.model = this@AiService.model
                responseFormat = ChatResponseFormat.jsonSchema(aiResponseJsonSchema)
                messages {
                    system {
                        content =
                            "You are a translation assistant.\n" +
                            "\n" +
                            "TASK:\n" +
                            "- Return the SAME JSON structure and values as the input, except:\n" +
                            "  * Translate ONLY string text fields named exactly \"words\" and \"syllables\" items.\n" +
                            "- DO NOT modify, create, remove, reorder, or reformat any other fields or values.\n" +
                            "- DO NOT change numbers or numeric strings. Copy these EXACTLY:\n" +
                            "  keys: startTimeMs, endTimeMs, syncType, error.\n" +
                            "- Keep array lengths and item order IDENTICAL.\n" +
                            "- Preserve whitespace, punctuation, timestamps, and all metadata UNCHANGED.\n" +
                            "\n" +
                            "OUTPUT:\n" +
                            "- Valid JSON, same structure and keys, no commentary."
                    }
                    user {
                        content {
                            text("Target language: $targetLanguage")
                        }
                        content {
                            text("Input lyrics: ${json.encodeToString(inputLyrics)}")
                        }
                    }
                }
            }
        val completion: ChatCompletion = openAI.chatCompletion(request)
        val jsonContent =
            completion.choices
                .firstOrNull()
                ?.message
                ?.content ?: throw IllegalStateException("No response from AI")
        val jsonData =
            Regex(
                "```json\\s*([\\s\\S]*?)```",
            ).find(jsonContent)
                ?.groups
                ?.firstOrNull()
                ?.value ?: jsonContent
        val aiResponse =
            json.decodeFromString<Lyrics>(
                jsonData
                    .replace("```json", "")
                    .replace("```", ""),
            )
        return aiResponse
    }

    companion object {
        private val translationJsonSchema: JsonObject =
            buildJsonObject {
                put("type", "object")
                putJsonObject("properties") {
                    putJsonObject("lines") {
                        put("type", "array")
                        putJsonObject("items") {
                            put("type", "object")
                            putJsonObject("properties") {
                                putJsonObject("startTimeMs") {
                                    put("type", "string")
                                }
                                putJsonObject("endTimeMs") {
                                    put("type", "string")
                                }
                                putJsonObject("syllables") {
                                    put("type", "array")
                                    putJsonObject("items") {
                                        put("type", "string")
                                    }
                                }
                                putJsonObject("words") {
                                    put("type", "string")
                                }
                            }
                            putJsonArray("required") {
                                add("startTimeMs")
                                add("endTimeMs")
                                add("words")
                            }
                        }
                    }
                    putJsonObject("syncType") {
                        put("type", "string")
                    }
                    putJsonObject("error") {
                        put("type", "boolean")
                    }
                }
                putJsonArray("required") {
                    add("lines")
                    add("syncType")
                    add("error")
                }
            }
        private val aiResponseJsonSchema =
            JsonSchema(
                name = "ai_translation_schema",
                schema = translationJsonSchema,
                strict = true,
            )
    }
}

enum class AIHost {
    GEMINI,
    OPENAI,
    CUSTOM_OPENAI,
}
