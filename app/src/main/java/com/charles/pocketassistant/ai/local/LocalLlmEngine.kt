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
        val modelPath = localModelManager.modelFile().absolutePath

        // First attempt with local-optimized prompt
        val prompt = PromptFactory.generalLocal(text)
        Log.d(TAG, "Extraction prompt length=${prompt.length}, input preview: ${text.take(120)}")
        val output = runPrompt(modelPath, prompt).getOrElse { error ->
            Log.e(TAG, "Local extraction failed", error)
            return AiExtractionResult(summary = readableFailure(error), classification = "unknown")
        }
        val cleanedOutput = cleanModelOutput(output)
        Log.d(TAG, "Raw model output (${output.length} chars): ${output.take(300)}")

        if (cleanedOutput.isNotBlank()) {
            val result = parser.parseOrFallback(cleanedOutput)
            // If result is good enough, return it
            if (!isLowQualityExtraction(result, text)) {
                return result
            }
            // Retry with a more directive prompt
            Log.d(TAG, "Low quality extraction (class=${result.classification}, summary=${result.summary.take(60)}), retrying...")
            val retryPrompt = PromptFactory.generalLocalRetry(text, result.classification.takeIf { it != "unknown" })
            val retryOutput = runPrompt(modelPath, retryPrompt).getOrNull()
            if (retryOutput != null) {
                val retryClean = cleanModelOutput(retryOutput)
                Log.d(TAG, "Retry output (${retryOutput.length} chars): ${retryOutput.take(300)}")
                if (retryClean.isNotBlank()) {
                    val retryResult = parser.parseOrFallback(retryClean)
                    if (!isLowQualityExtraction(retryResult, text)) {
                        return retryResult
                    }
                    // Return whichever result is better
                    return if (retryResult.summary.length > result.summary.length) retryResult else result
                }
            }
            return result
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
        val modelPath = localModelManager.modelFile().absolutePath

        // Use local-optimized prompt
        val prompt = PromptFactory.assistantChatLocal(question, context, allowActions)
        val output = runPrompt(modelPath, prompt).getOrElse { error ->
            Log.e(TAG, "Local assistant response failed", error)
            return AssistantChatResult(reply = readableFailure(error))
        }
        val cleanedOutput = cleanModelOutput(output)
        if (cleanedOutput.isBlank()) {
            return AssistantChatResult(reply = "The local model did not return a response.")
        }
        val result = parser.parseAssistantChatOrFallback(cleanedOutput)
        // If the reply is usable, return it
        if (result.reply.length >= 10 && !result.reply.startsWith("{")) {
            return result
        }
        // Retry with the simpler retry prompt
        Log.d(TAG, "Low quality assistant reply (${result.reply.length} chars), retrying with simpler prompt...")
        val retryPrompt = PromptFactory.assistantChatLocalRetry(question, context, allowActions)
        val retryOutput = runPrompt(modelPath, retryPrompt).getOrElse { error ->
            Log.e(TAG, "Local assistant retry failed", error)
            return result // Return whatever we got the first time
        }
        val retryClean = cleanModelOutput(retryOutput)
        if (retryClean.isBlank()) return result
        val retryResult = parser.parseAssistantChatOrFallback(retryClean)
        return if (retryResult.reply.length > result.reply.length) retryResult else result
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

    /**
     * Check if extraction result is too low-quality to accept.
     */
    private fun isLowQualityExtraction(result: AiExtractionResult, inputText: String): Boolean {
        // Classification is unknown and summary is very short
        if (result.classification == "unknown" && result.summary.length < 30) return true
        // Summary is just raw JSON that leaked through
        if (result.summary.trimStart().startsWith("{")) return true
        // Input has dollar signs but no amounts were extracted
        if (inputText.contains("$") && result.entities.amounts.isEmpty()
            && result.billInfo.amount.isBlank()) return true
        // Input has dates but none were extracted
        val hasDateWords = listOf("due", "jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec", "/202")
            .any { inputText.lowercase().contains(it) }
        if (hasDateWords && result.entities.dates.isEmpty()
            && result.billInfo.dueDate.isBlank()
            && result.appointmentInfo.date.isBlank()) return true
        return false
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
