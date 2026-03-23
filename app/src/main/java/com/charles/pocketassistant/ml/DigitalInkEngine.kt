package com.charles.pocketassistant.ml

import android.util.Log
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.vision.digitalink.DigitalInkRecognition
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModel
import com.google.mlkit.vision.digitalink.DigitalInkRecognitionModelIdentifier
import com.google.mlkit.vision.digitalink.DigitalInkRecognizerOptions
import com.google.mlkit.vision.digitalink.Ink
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * ML Kit Digital Ink Recognition — converts handwritten strokes into text.
 * Downloads the model on first use (~20MB for English).
 */
@Singleton
class DigitalInkEngine @Inject constructor() {
    private companion object {
        const val TAG = "DigitalInk"
    }

    private val modelIdentifier = DigitalInkRecognitionModelIdentifier.fromLanguageTag("en-US")!!
    private val model = DigitalInkRecognitionModel.builder(modelIdentifier).build()
    private val recognizer = DigitalInkRecognition.getClient(
        DigitalInkRecognizerOptions.builder(model).build()
    )

    private val _modelReady = MutableStateFlow(false)
    val modelReady: StateFlow<Boolean> = _modelReady

    suspend fun ensureModelDownloaded() {
        if (_modelReady.value) return
        suspendCoroutine { cont ->
            RemoteModelManager.getInstance()
                .download(model, DownloadConditions.Builder().build())
                .addOnSuccessListener {
                    _modelReady.value = true
                    Log.d(TAG, "Digital ink model downloaded")
                    cont.resume(Unit)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Digital ink model download failed", e)
                    cont.resumeWithException(e)
                }
        }
    }

    /**
     * Recognize handwritten strokes and return the best text result.
     * @param ink The Ink object built from touch strokes.
     */
    suspend fun recognize(ink: Ink): String {
        ensureModelDownloaded()
        return suspendCoroutine { cont ->
            recognizer.recognize(ink)
                .addOnSuccessListener { result ->
                    val text = result.candidates.firstOrNull()?.text.orEmpty()
                    Log.d(TAG, "Recognized: $text (${result.candidates.size} candidates)")
                    cont.resume(text)
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Recognition failed", e)
                    cont.resumeWithException(e)
                }
        }
    }

    /**
     * Get all recognition candidates with their scores.
     */
    suspend fun recognizeWithCandidates(ink: Ink): List<RecognitionCandidate> {
        ensureModelDownloaded()
        return suspendCoroutine { cont ->
            recognizer.recognize(ink)
                .addOnSuccessListener { result ->
                    val candidates = result.candidates.map { c ->
                        RecognitionCandidate(text = c.text, score = c.score?.toFloat())
                    }
                    cont.resume(candidates)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }
    }
}

data class RecognitionCandidate(
    val text: String,
    val score: Float?
)
