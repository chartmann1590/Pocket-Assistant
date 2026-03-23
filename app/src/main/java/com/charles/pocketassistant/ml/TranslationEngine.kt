package com.charles.pocketassistant.ml

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * On-device translation using ML Kit.
 * Downloads ~30MB language models on demand.
 * Supports 59 languages.
 */
@Singleton
class TranslationEngine @Inject constructor() {
    private companion object {
        const val TAG = "Translation"
    }

    private val _downloadingModel = MutableStateFlow(false)
    val downloadingModel: StateFlow<Boolean> = _downloadingModel

    /**
     * Translate text from source language to target language.
     * Will download the required model if not already present.
     *
     * @param text Input text
     * @param sourceLanguage BCP-47 code (e.g., "es", "fr", "zh")
     * @param targetLanguage BCP-47 code (default "en")
     * @return Translated text, or original text on failure
     */
    suspend fun translate(
        text: String,
        sourceLanguage: String,
        targetLanguage: String = TranslateLanguage.ENGLISH
    ): String = withContext(Dispatchers.IO) {
        val sourceLang = TranslateLanguage.fromLanguageTag(sourceLanguage)
        val targetLang = TranslateLanguage.fromLanguageTag(targetLanguage)
        if (sourceLang == null || targetLang == null) {
            Log.w(TAG, "Unsupported language pair: $sourceLanguage -> $targetLanguage")
            return@withContext text
        }

        val options = TranslatorOptions.Builder()
            .setSourceLanguage(sourceLang)
            .setTargetLanguage(targetLang)
            .build()
        val translator = Translation.getClient(options)

        try {
            _downloadingModel.value = true
            val conditions = DownloadConditions.Builder()
                .requireWifi()
                .build()
            translator.downloadModelIfNeeded(conditions).await()
            _downloadingModel.value = false

            val result = translator.translate(text).await()
            Log.d(TAG, "Translated ${text.length} chars $sourceLanguage->$targetLanguage")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            _downloadingModel.value = false
            text
        } finally {
            translator.close()
        }
    }

    /**
     * Get list of available translation language codes.
     */
    fun availableLanguages(): List<String> = TranslateLanguage.getAllLanguages()
}
