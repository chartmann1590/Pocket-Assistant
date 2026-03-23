package com.charles.pocketassistant.ml

import android.util.Log
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * On-device language identification using ML Kit.
 * Detects 100+ languages in ~10ms.
 */
@Singleton
class LanguageDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "LanguageDetector"
        const val CONFIDENCE_THRESHOLD = 0.5f
    }

    private val identifier = LanguageIdentification.getClient(
        LanguageIdentificationOptions.Builder()
            .setConfidenceThreshold(CONFIDENCE_THRESHOLD)
            .build()
    )

    /**
     * Identify the most likely language of the given text.
     * Returns BCP-47 language code (e.g., "en", "es", "zh") or "und" if undetermined.
     */
    suspend fun identifyLanguage(text: String): String {
        return try {
            val code = identifier.identifyLanguage(text).await()
            Log.d(TAG, "Detected language: $code for text length ${text.length}")
            code
        } catch (e: Exception) {
            Log.e(TAG, "Language identification failed", e)
            "und"
        }
    }

    /**
     * Get possible languages with confidence scores.
     * Returns list of (languageCode, confidence) pairs sorted by confidence desc.
     */
    suspend fun identifyPossibleLanguages(text: String): List<LanguageResult> {
        return try {
            val results = identifier.identifyPossibleLanguages(text).await()
            results.map { LanguageResult(it.languageTag, it.confidence) }
                .sortedByDescending { it.confidence }
                .also { Log.d(TAG, "Possible languages: ${it.take(3)}") }
        } catch (e: Exception) {
            Log.e(TAG, "Language identification failed", e)
            emptyList()
        }
    }

    fun isEnglish(languageCode: String): Boolean = languageCode == "en"
}

data class LanguageResult(
    val languageCode: String,
    val confidence: Float
)
