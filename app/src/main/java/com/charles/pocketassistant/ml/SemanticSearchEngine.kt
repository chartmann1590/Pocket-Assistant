package com.charles.pocketassistant.ml

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Semantic search engine with two backends:
 *   1. Neural (MediaPipe Universal Sentence Encoder) — understands meaning,
 *      no keywords needed. "money I owe" matches "invoice from vendor".
 *   2. TF-IDF fallback — used while the neural model is downloading (~28MB).
 *
 * Neural mode is strictly better: it uses a pretrained model that learned
 * language understanding from a massive corpus. TF-IDF is just a stopgap.
 */
@Singleton
class SemanticSearchEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val neuralEngine: NeuralEmbeddingEngine
) {
    private companion object {
        const val TAG = "SemanticSearch"
        const val TFIDF_DIM = 512
    }

    // TF-IDF fallback state
    private val idf = mutableMapOf<String, Float>()

    /**
     * Rank items by semantic relevance to query.
     * Uses neural embeddings when available, TF-IDF otherwise.
     */
    fun rankBySimilarity(
        query: String,
        itemTexts: List<String>,
        topK: Int = 10
    ): List<ScoredItem> {
        // Try neural embeddings first — these understand meaning
        val neuralResult = neuralEngine.rankBySimilarity(query, itemTexts, topK)
        if (neuralResult != null) {
            Log.d(TAG, "Using neural embeddings for ranking")
            return neuralResult
        }

        // Fallback: TF-IDF (keyword-based, used while neural model downloads)
        Log.d(TAG, "Neural not ready, using TF-IDF fallback")
        val queryEmbedding = tfidfEmbed(query)
        return itemTexts.mapIndexed { index, text ->
            ScoredItem(index, cosineSimilarity(queryEmbedding, tfidfEmbed(text)))
        }
            .sortedByDescending { it.similarity }
            .take(topK)
    }

    /**
     * Update IDF scores from a corpus of documents (TF-IDF fallback only).
     */
    fun updateIdf(documents: List<String>) {
        idf.clear()
        val docFreq = mutableMapOf<String, Int>()
        for (doc in documents) {
            val uniqueTokens = tokenize(doc).toSet()
            for (token in uniqueTokens) {
                docFreq[token] = (docFreq[token] ?: 0) + 1
            }
        }
        val n = documents.size
        for ((term, freq) in docFreq) {
            idf[term] = kotlin.math.ln((n.toFloat() + 1) / (freq + 1)) + 1.0f
        }
        Log.d(TAG, "Updated IDF with $n docs, ${idf.size} terms")
    }

    /** Whether the neural backend is active (vs TF-IDF fallback). */
    fun isNeuralReady(): Boolean = neuralEngine.isReady()

    // ── TF-IDF fallback implementation ──────────────────────────────

    private fun tfidfEmbed(text: String): FloatArray {
        val tokens = tokenize(text)
        val vector = FloatArray(TFIDF_DIM)
        val tf = mutableMapOf<String, Int>()
        for (token in tokens) {
            tf[token] = (tf[token] ?: 0) + 1
        }
        for ((term, count) in tf) {
            val termFreq = count.toFloat() / tokens.size.coerceAtLeast(1)
            val idfScore = idf[term] ?: 1.0f
            val weight = termFreq * idfScore
            val bucket = (term.hashCode() and 0x7FFFFFFF) % TFIDF_DIM
            vector[bucket] += weight
        }
        // L2 normalize
        val norm = sqrt(vector.sumOf { (it * it).toDouble() }).toFloat()
        if (norm > 0f) {
            for (i in vector.indices) vector[i] /= norm
        }
        return vector
    }

    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        var normA = 0f
        var normB = 0f
        for (i in a.indices) {
            dot += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom > 0f) dot / denom else 0f
    }

    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 1 }
    }
}

data class ScoredItem(
    val index: Int,
    val similarity: Float
)
