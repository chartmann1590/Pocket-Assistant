package com.charles.pocketassistant.data.repository

import android.util.Log
import com.charles.pocketassistant.ai.parser.AiJsonParser
import com.charles.pocketassistant.ai.prompt.PromptFactory
import com.charles.pocketassistant.data.datastore.SettingsStore
import com.charles.pocketassistant.data.remote.ollama.OllamaChatRequest
import com.charles.pocketassistant.data.remote.ollama.OllamaChatResponse
import com.charles.pocketassistant.data.remote.ollama.OllamaMessage
import com.charles.pocketassistant.domain.model.AssistantChatResult
import com.charles.pocketassistant.domain.model.AiExtractionResult
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ResponseBody

class OllamaRepositoryImpl @Inject constructor(
    private val settingsStore: SettingsStore,
    private val parser: AiJsonParser,
    private val apiFactory: OllamaServiceFactory
) {
    private companion object {
        const val TAG = "OllamaRepo"
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun testConnection(): Result<List<String>> {
        return runCatching {
            val settings = settingsStore.settings.first()
            val api = apiFactory.create(
                settings.ollamaBaseUrl,
                settings.ollamaApiToken,
                settings.allowSelfSignedCertificates
            )
            api.listModels().models.orEmpty()
                .map { it.resolvedName() }
                .filter { it.isNotBlank() }
                .distinct()
        }
    }

    suspend fun applyDefaultOllamaModelIfNeeded(models: List<String>) {
        val settings = settingsStore.settings.first()
        val current = settings.ollamaModelName.trim()
        val next = when {
            models.isEmpty() -> current
            current.isBlank() -> models.first()
            current !in models -> models.first()
            else -> current
        }
        if (settings.ollamaModelName != next) {
            settingsStore.update { it.copy(ollamaModelName = next) }
        }
    }

    suspend fun summarizeAndExtract(text: String): Result<AiExtractionResult> = runCatching {
        val settings = settingsStore.settings.first()
        val api = apiFactory.create(
            settings.ollamaBaseUrl,
            settings.ollamaApiToken,
            settings.allowSelfSignedCertificates
        )
        val responseBody = api.chat(
            OllamaChatRequest(
                model = settings.ollamaModelName.trim(),
                messages = listOf(OllamaMessage("user", PromptFactory.general(text))),
                stream = false
            )
        )
        val content = readChatResponse(responseBody)
        parser.parseOrFallback(content)
    }

    suspend fun answerQuestion(question: String, context: String): Result<String> = runCatching {
        answerQuestionStructured(question, context, allowActions = false).getOrThrow().reply.ifBlank { "Ollama returned an empty response." }
    }

    suspend fun answerQuestionStructured(question: String, context: String, allowActions: Boolean): Result<AssistantChatResult> = runCatching {
        val settings = settingsStore.settings.first()
        val url = settings.ollamaBaseUrl
        val model = settings.ollamaModelName.trim()
        Log.d(TAG, "Ollama chat: url=$url model=$model")
        val api = apiFactory.create(
            url,
            settings.ollamaApiToken,
            settings.allowSelfSignedCertificates
        )
        val responseBody = api.chat(
            OllamaChatRequest(
                model = model,
                messages = listOf(OllamaMessage("user", PromptFactory.assistantChat(question, context, allowActions))),
                stream = false
            )
        )
        val content = readChatResponse(responseBody)
        Log.d(TAG, "Ollama chat response: ${content.length} chars")
        parser.parseAssistantChatOrFallback(content)
    }.also { result ->
        result.exceptionOrNull()?.let { e ->
            Log.e(TAG, "Ollama chat failed: ${e.javaClass.simpleName}: ${e.message}", e)
        }
    }

    /**
     * Reads an Ollama chat response body. Handles both:
     * - Single JSON object (non-streaming, `stream: false` honored)
     * - NDJSON stream (multiple JSON lines, some servers ignore `stream: false`)
     *
     * For streamed responses, concatenates all `message.content` fragments.
     */
    private suspend fun readChatResponse(body: ResponseBody): String = withContext(Dispatchers.IO) {
        body.use { responseBody ->
            val raw = responseBody.string()
            // Try parsing as a single JSON object first (non-streaming response)
            try {
                val single = json.decodeFromString(OllamaChatResponse.serializer(), raw)
                return@withContext single.message?.content.orEmpty()
            } catch (_: Exception) {
                // Fall through to NDJSON parsing
            }
            // Parse as NDJSON: each line is a separate JSON object with a message fragment
            val contentBuilder = StringBuilder()
            raw.lineSequence()
                .filter { it.isNotBlank() }
                .forEach { line ->
                    try {
                        val chunk = json.decodeFromString(OllamaChatResponse.serializer(), line)
                        contentBuilder.append(chunk.message?.content.orEmpty())
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping unparseable NDJSON line: ${line.take(100)}", e)
                    }
                }
            contentBuilder.toString()
        }
    }
}
