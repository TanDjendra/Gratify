package com.tan.gratify.aiservice

import com.tan.domain.data.model.metadata.Line
import com.tan.domain.data.model.metadata.Lyrics
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    private val httpClient = HttpClient()

    private val model: String
        get() = if (!customModelId.isNullOrEmpty()) {
            customModelId
        } else {
            when (aiHost) {
                AIHost.GEMINI -> "gemini-2.0-flash"
                AIHost.OPENAI -> "gpt-4o"
                AIHost.CUSTOM_OPENAI -> "gpt-4o"
            }
        }

    private val baseUrl: String
        get() = when (aiHost) {
            AIHost.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/"
            AIHost.OPENAI -> "https://api.openai.com/v1/"
            AIHost.CUSTOM_OPENAI -> customBaseUrl ?: "https://api.openai.com/v1/"
        }

    suspend fun translateLyrics(
        inputLyrics: Lyrics,
        targetLanguage: String,
    ): Lyrics {
        val lines = inputLyrics.lines ?: throw IllegalStateException("No lyrics lines to translate")

        // Build key-value map: index -> words (only non-empty lines)
        val indexToWords = mutableMapOf<String, String>()
        lines.forEachIndexed { index, line ->
            val words = line.words.trim()
            if (words.isNotEmpty() && words != "♫") {
                indexToWords[index.toString()] = words
            }
        }

        if (indexToWords.isEmpty()) {
            throw IllegalStateException("No translatable lyrics lines found")
        }

        val inputJson = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), indexToWords)

        val systemPrompt = "You are a song lyrics translation assistant.\n" +
                "\n" +
                "TASK:\n" +
                "- You will receive a JSON object where keys are line indices and values are lyrics text.\n" +
                "- FIRST, detect the dominant language of the input lyrics.\n" +
                "- If the detected language is the SAME as the target language code, return an EMPTY \"translations\" object. Do NOT translate. Do NOT paraphrase.\n" +
                "- Otherwise, translate ONLY the values to the target language.\n" +
                "- When translating: keep ALL keys exactly the same, output MUST have the EXACT same number of entries as the input, do NOT merge/split/add/remove any entries, and preserve the song's meaning, tone, and emotion.\n" +
                "\n" +
                "OUTPUT:\n" +
                "- A JSON object with the \"translations\" field containing the same keys mapped to translated values (or an empty object when the input is already in the target language)."

        val userPrompt = "Target language: $targetLanguage\nInput lyrics: $inputJson"

        val requestBody = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userPrompt)
                })
            }
            putJsonObject("response_format") {
                put("type", "json_object")
            }
        }

        val url = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"

        val httpResponse = httpClient.post(url) {
            header("Content-Type", "application/json")
            header("Authorization", "Bearer $apiKey")
            customHeaders?.forEach { (key, value) ->
                header(key, value)
            }
            setBody(requestBody.toString())
        }

        val responseBodyString = httpResponse.body<String>()
        if (httpResponse.status.value !in 200..299) {
            throw IllegalStateException("AI API request failed: ${httpResponse.status.value} - $responseBodyString")
        }

        val responseJson = json.parseToJsonElement(responseBodyString).jsonObject
        val choices = responseJson["choices"]?.jsonArray
        val firstChoice = choices?.firstOrNull()?.jsonObject
        val message = firstChoice?.get("message")?.jsonObject
        val jsonContent = message?.get("content")?.jsonPrimitive?.content 
            ?: throw IllegalStateException("No response content from AI: $responseBodyString")

        val jsonData = Regex("```json\\s*([\\s\\S]*?)```").find(jsonContent)?.groups?.get(1)?.value ?: jsonContent
        val cleanedJson = jsonData.replace("```json", "").replace("```", "").trim()
        val translationResponse = json.decodeFromString<TranslationResponse>(cleanedJson)
        val translatedMap = translationResponse.translations
        if (translatedMap.isEmpty()) {
            throw IllegalStateException(
                "Input lyrics are already in the target language ($targetLanguage). Translation aborted.",
            )
        }

        // Map translated text back to original lines, preserving all timestamps
        val translatedLines = lines.mapIndexed { index, originalLine ->
            val translatedWords = translatedMap[index.toString()]
            if (translatedWords != null) {
                Line(
                    startTimeMs = originalLine.startTimeMs,
                    endTimeMs = originalLine.endTimeMs,
                    words = translatedWords,
                    syllables = null,
                )
            } else {
                // Non-translatable line (empty or ♫): keep original
                Line(
                    startTimeMs = originalLine.startTimeMs,
                    endTimeMs = originalLine.endTimeMs,
                    words = originalLine.words,
                    syllables = originalLine.syllables,
                )
            }
        }

        return Lyrics(
            error = false,
            lines = translatedLines,
            syncType = inputLyrics.syncType,
        )
    }
}

@Serializable
data class TranslationResponse(
    val translations: Map<String, String> = emptyMap(),
)

enum class AIHost {
    GEMINI,
    OPENAI,
    CUSTOM_OPENAI,
}