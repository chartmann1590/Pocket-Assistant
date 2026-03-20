package com.charles.pocketassistant.data.repository

import com.charles.pocketassistant.ai.parser.AiJsonParser
import com.charles.pocketassistant.ai.prompt.PromptFactory
import com.charles.pocketassistant.data.datastore.SettingsStore
import com.charles.pocketassistant.data.remote.ollama.OllamaChatRequest
import com.charles.pocketassistant.data.remote.ollama.OllamaMessage
import com.charles.pocketassistant.domain.model.AssistantChatResult
import com.charles.pocketassistant.domain.model.AiExtractionResult
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class OllamaRepositoryImpl @Inject constructor(
    private val settingsStore: SettingsStore,
    private val parser: AiJsonParser,
    private val apiFactory: OllamaServiceFactory
) {
    suspend fun testConnection(): Result<List<String>> {
        return runCatching {
            val settings = settingsStore.settings.first()
            val api = apiFactory.create(
                settings.ollamaBaseUrl,
                settings.ollamaApiToken,
                settings.allowSelfSignedCertificates
            )
            api.listModels().models.map { it.name }
        }
    }

    suspend fun summarizeAndExtract(text: String): Result<AiExtractionResult> = runCatching {
        val settings = settingsStore.settings.first()
        val api = apiFactory.create(
            settings.ollamaBaseUrl,
            settings.ollamaApiToken,
            settings.allowSelfSignedCertificates
        )
        val response = api.chat(
            OllamaChatRequest(
                model = settings.ollamaModelName,
                messages = listOf(OllamaMessage("user", PromptFactory.general(text))),
                stream = false
            )
        )
        parser.parseOrFallback(response.message?.content.orEmpty())
    }

    suspend fun answerQuestion(question: String, context: String): Result<String> = runCatching {
        answerQuestionStructured(question, context, allowActions = false).getOrThrow().reply.ifBlank { "Ollama returned an empty response." }
    }

    suspend fun answerQuestionStructured(question: String, context: String, allowActions: Boolean): Result<AssistantChatResult> = runCatching {
        val settings = settingsStore.settings.first()
        val api = apiFactory.create(
            settings.ollamaBaseUrl,
            settings.ollamaApiToken,
            settings.allowSelfSignedCertificates
        )
        val response = api.chat(
            OllamaChatRequest(
                model = settings.ollamaModelName,
                messages = listOf(OllamaMessage("user", PromptFactory.assistantChat(question, context, allowActions))),
                stream = false
            )
        )
        parser.parseAssistantChatOrFallback(response.message?.content.orEmpty())
    }
}
