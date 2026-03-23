package com.charles.pocketassistant.ai.nano

import android.content.Context
import android.util.Log
import com.google.ai.edge.aicore.GenerativeModel
import com.google.ai.edge.aicore.generationConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Gemini Nano on-device inference via Google AICore.
 *
 * Only available on Pixel 8+ with Android 14+ and AICore installed.
 * This is the highest-quality on-device model option when available.
 *
 * Falls back gracefully — callers should check [isAvailable] before use.
 */
@Singleton
class GeminiNanoEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "GeminiNano"
    }

    private val _available = MutableStateFlow<Boolean?>(null) // null = not checked yet
    val available: StateFlow<Boolean?> = _available

    private var model: GenerativeModel? = null

    /**
     * Check if Gemini Nano is available on this device.
     * Caches the result after first check.
     */
    suspend fun isAvailable(): Boolean {
        _available.value?.let { return it }
        return try {
            val testModel = GenerativeModel(
                generationConfig = generationConfig { maxOutputTokens = 16 }
            )
            // Try to create the model — this will throw if AICore isn't available
            testModel.generateContent("test").text
            model = GenerativeModel(
                generationConfig = generationConfig {
                    maxOutputTokens = 1024
                    temperature = 0.3f
                    topK = 16
                }
            )
            _available.value = true
            Log.d(TAG, "Gemini Nano available on this device")
            true
        } catch (e: Exception) {
            _available.value = false
            Log.d(TAG, "Gemini Nano not available: ${e.message}")
            false
        }
    }

    /**
     * Generate text using Gemini Nano.
     * Returns null if Gemini Nano is not available.
     */
    suspend fun generate(prompt: String): String? {
        if (!isAvailable()) return null
        return try {
            val response = model?.generateContent(prompt)
            response?.text?.also {
                Log.d(TAG, "Generated ${it.length} chars")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed", e)
            null
        }
    }

    /**
     * Summarize text using Gemini Nano.
     * Returns null if not available or on failure.
     */
    suspend fun summarize(text: String): String? {
        val prompt = buildString {
            append("Summarize the following text concisely in 1-2 sentences:\n\n")
            append(text.take(3000))
        }
        return generate(prompt)
    }

    /**
     * Answer a question using context, powered by Gemini Nano.
     * Returns null if not available.
     */
    suspend fun answerQuestion(question: String, context: String): String? {
        val prompt = buildString {
            append("Based on the following context, answer the question concisely.\n\n")
            append("Context:\n")
            append(context.take(3000))
            append("\n\nQuestion: ")
            append(question)
            append("\n\nAnswer:")
        }
        return generate(prompt)
    }
}
