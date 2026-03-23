package com.charles.pocketassistant.ml

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Neural text embeddings via MediaPipe's TextEmbedder + Universal Sentence Encoder.
 *
 * This understands *meaning*, not keywords. "When is my cruise payment?" and
 * "Royal Caribbean booking confirmation" are semantically close in embedding space
 * even though they share zero words.
 *
 * The USE model (~28MB) is downloaded on first use and cached locally.
 * Until downloaded, [isReady] returns false and callers should fall back
 * to the TF-IDF SemanticSearchEngine.
 */
@Singleton
class NeuralEmbeddingEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private companion object {
        const val TAG = "NeuralEmbed"
        const val MODEL_FILENAME = "universal_sentence_encoder.tflite"
        const val MODEL_URL = "https://storage.googleapis.com/mediapipe-models/text_embedder/universal_sentence_encoder/float32/latest/universal_sentence_encoder.tflite"
    }

    private var embedder: TextEmbedder? = null
    private val initMutex = Mutex()
    private var initAttempted = false
    private var downloadInProgress = false

    /** True when the neural model is loaded and ready to embed. */
    fun isReady(): Boolean = embedder != null

    /**
     * Initialize the embedder if the model file exists.
     * Call this at startup — it's fast if the model is already downloaded.
     * Returns true if ready.
     */
    suspend fun tryInit(): Boolean = initMutex.withLock {
        if (embedder != null) return true
        if (initAttempted) return false
        initAttempted = true
        val modelFile = modelFile()
        if (!modelFile.exists()) {
            Log.d(TAG, "Model not downloaded yet, starting background download")
            downloadModelAsync()
            return false
        }
        return loadModel(modelFile)
    }

    /**
     * Embed text into a dense vector. Returns null if the engine isn't ready.
     */
    fun embed(text: String): FloatArray? {
        val e = embedder ?: return null
        return try {
            val result = e.embed(text)
            val embedding = result.embeddingResult().embeddings()[0]
            embedding.floatEmbedding()
        } catch (ex: Exception) {
            Log.e(TAG, "Embed failed: ${ex.message}")
            null
        }
    }

    /**
     * Compute cosine similarity between two texts using neural embeddings.
     * Returns null if the engine isn't ready.
     */
    fun similarity(textA: String, textB: String): Float? {
        val e = embedder ?: return null
        return try {
            val resultA = e.embed(textA)
            val resultB = e.embed(textB)
            TextEmbedder.cosineSimilarity(
                resultA.embeddingResult().embeddings()[0],
                resultB.embeddingResult().embeddings()[0]
            ).toFloat()
        } catch (ex: Exception) {
            Log.e(TAG, "Similarity failed: ${ex.message}")
            null
        }
    }

    /**
     * Rank items by semantic relevance to the query using neural embeddings.
     * Returns list of (index, similarity) pairs sorted by relevance.
     */
    fun rankBySimilarity(query: String, texts: List<String>, topK: Int = 10): List<ScoredItem>? {
        val e = embedder ?: return null
        return try {
            val queryResult = e.embed(query)
            val queryEmb = queryResult.embeddingResult().embeddings()[0]
            texts.mapIndexed { index, text ->
                val textResult = e.embed(text)
                val textEmb = textResult.embeddingResult().embeddings()[0]
                val sim = TextEmbedder.cosineSimilarity(queryEmb, textEmb).toFloat()
                ScoredItem(index, sim)
            }
                .sortedByDescending { it.similarity }
                .take(topK)
        } catch (ex: Exception) {
            Log.e(TAG, "rankBySimilarity failed: ${ex.message}")
            null
        }
    }

    private fun modelFile(): File = File(context.filesDir, MODEL_FILENAME)

    private fun loadModel(file: File): Boolean {
        return try {
            // Read model into a direct ByteBuffer (required for files outside assets/)
            val buffer = file.inputStream().channel.use { channel ->
                channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
            }
            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetBuffer(buffer)
                        .build()
                )
                .setL2Normalize(true)
                .setQuantize(false)
                .build()
            embedder = TextEmbedder.createFromOptions(context, options)
            Log.d(TAG, "Neural embedding model loaded successfully (${file.length() / 1024}KB)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}", e)
            false
        }
    }

    private fun downloadModelAsync() {
        if (downloadInProgress) return
        downloadInProgress = true
        Thread {
            try {
                Log.d(TAG, "Downloading USE model (~28MB)...")
                val modelFile = modelFile()
                val tmpFile = File(modelFile.parentFile, "${MODEL_FILENAME}.tmp")
                val url = java.net.URL(MODEL_URL)
                val connection = url.openConnection()
                connection.connectTimeout = 15_000
                connection.readTimeout = 60_000
                connection.getInputStream().use { input ->
                    tmpFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                tmpFile.renameTo(modelFile)
                Log.d(TAG, "USE model downloaded (${modelFile.length() / 1024}KB)")
                // Load it now so it's ready for the next query
                loadModel(modelFile)
                initAttempted = false // Allow re-init
            } catch (e: Exception) {
                Log.e(TAG, "Model download failed: ${e.message}")
            } finally {
                downloadInProgress = false
            }
        }.start()
    }
}
