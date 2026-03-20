package com.charles.pocketassistant.ai.local

import android.util.Log
import com.charles.pocketassistant.ai.parser.AiJsonParser
import com.charles.pocketassistant.ai.prompt.PromptFactory
import com.charles.pocketassistant.domain.model.AssistantChatResult
import com.charles.pocketassistant.domain.model.AiExtractionResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

@Singleton
class LocalLlmEngine @Inject constructor(
    private val localModelManager: LocalModelManager,
    private val parser: AiJsonParser,
    private val liteRtLmBridge: LiteRtLmBridge
) {
    private companion object {
        const val TAG = "LocalLlmEngine"
    }

    fun isAvailable(): Boolean = localModelManager.isModelInstalled()

    suspend fun summarizeAndExtract(text: String): AiExtractionResult {
        if (!isAvailable()) {
            return AiExtractionResult(summary = "Local model is not installed.", classification = "unknown")
        }
        val prompt = PromptFactory.general(text)
        val modelPath = localModelManager.modelFile().absolutePath
        val output = runPrompt(modelPath, prompt).getOrElse { error ->
            val message = readableFailure(error)
            Log.e(TAG, "Local extraction failed", error)
            return AiExtractionResult(summary = message, classification = "unknown")
        }
        val cleanedOutput = cleanModelOutput(output)
        if (cleanedOutput.isNotBlank()) {
            return parser.parseOrFallback(cleanedOutput)
        }
        return AiExtractionResult(
            summary = "Local model did not return a response.",
            classification = "unknown"
        )
    }

    suspend fun answerQuestion(question: String, context: String): String {
        return answerQuestionStructured(question, context, allowActions = false).reply.ifBlank {
            "The local model did not return a response."
        }
    }

    suspend fun answerQuestionStructured(question: String, context: String, allowActions: Boolean): AssistantChatResult {
        if (!isAvailable()) {
            return AssistantChatResult(
                reply = "Local model is not installed. I can only answer from stored context after local AI is set up."
            )
        }
        val prompt = PromptFactory.assistantChat(question, context, allowActions)
        val modelPath = localModelManager.modelFile().absolutePath
        val output = runPrompt(modelPath, prompt).getOrElse { error ->
            Log.e(TAG, "Local assistant response failed", error)
            return AssistantChatResult(reply = readableFailure(error))
        }
        val cleanedOutput = cleanModelOutput(output)
        return if (cleanedOutput.isBlank()) {
            AssistantChatResult(reply = "The local model did not return a response.")
        } else {
            parser.parseAssistantChatOrFallback(cleanedOutput)
        }
    }

    suspend fun selfTest(): String {
        if (!isAvailable()) {
            return "Local model is not installed."
        }
        val modelPath = localModelManager.modelFile().absolutePath
        val prompt = "Reply with one short line that starts with READY and confirms you can answer locally."
        val output = runPrompt(modelPath, prompt).getOrElse { error ->
            Log.e(TAG, "Local model self-test failed", error)
            return readableFailure(error)
        }
        val cleanedOutput = cleanModelOutput(output)
        val backend = liteRtLmBridge.currentBackend().ifBlank { "unknown backend" }
        return if (cleanedOutput.isBlank()) {
            "Local model self-test failed: the runtime returned an empty response."
        } else {
            "Local model OK on $backend: $cleanedOutput"
        }
    }

    private suspend fun runPrompt(modelPath: String, prompt: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching { liteRtLmBridge.generate(modelPath, prompt) }
    }

    private fun readableFailure(error: Throwable): String {
        val detail = error.message?.trim().orEmpty()
        return if (detail.isBlank()) {
            "Local model failed to run on this device."
        } else {
            "Local model failed: $detail"
        }
    }

    private fun cleanModelOutput(raw: String): String {
        val withoutThinking = raw.replace(Regex("(?is)<think>.*?</think>"), " ").trim()
        val unfenced = Regex("(?is)^```(?:json)?\\s*(.*?)\\s*```$")
            .matchEntire(withoutThinking)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?: withoutThinking
        return unfenced
            .replace("\r", "")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }
}
