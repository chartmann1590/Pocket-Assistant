package com.charles.pocketassistant.ml

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fast on-device text classification using keyword/pattern matching with weighted scoring.
 * Runs in <1ms — provides instant classification before the LLM even starts.
 *
 * This is a lightweight ML approach using TF-IDF-style keyword weighting.
 * No external model file needed — the "model" is the scored keyword dictionaries
 * which can be updated based on user corrections over time.
 */
@Singleton
class TextClassifier @Inject constructor() {
    private companion object {
        const val TAG = "TextClassifier"
    }

    private val billKeywords = mapOf(
        "invoice" to 3.0f, "bill" to 3.0f, "statement" to 2.5f, "payment" to 2.5f,
        "due" to 2.0f, "amount due" to 3.5f, "total" to 1.5f, "balance" to 2.0f,
        "account" to 1.5f, "overdue" to 3.0f, "past due" to 3.0f, "pay by" to 3.0f,
        "minimum payment" to 3.0f, "utility" to 2.0f, "electric" to 1.5f,
        "water" to 1.0f, "gas" to 1.0f, "internet" to 1.0f, "phone bill" to 3.0f,
        "credit card" to 2.0f, "subscription" to 1.5f, "auto-pay" to 2.0f,
        "billing" to 2.5f, "charge" to 1.5f, "fee" to 1.5f, "premium" to 1.5f,
        "renewal" to 1.5f, "plan" to 1.0f
    )

    private val appointmentKeywords = mapOf(
        "appointment" to 3.5f, "meeting" to 3.0f, "schedule" to 2.5f, "calendar" to 2.0f,
        "reservation" to 3.0f, "booking" to 3.0f, "conference" to 2.5f, "event" to 2.0f,
        "attend" to 2.0f, "rsvp" to 3.0f, "location" to 1.5f, "venue" to 2.0f,
        "arrives" to 1.5f, "departs" to 1.5f, "flight" to 2.5f, "hotel" to 2.0f,
        "check-in" to 2.5f, "checkout" to 2.0f, "doctor" to 2.5f, "dentist" to 2.5f,
        "clinic" to 2.0f, "consultation" to 2.5f, "interview" to 2.5f,
        "dinner" to 1.5f, "lunch" to 1.5f, "brunch" to 1.5f, "party" to 1.5f,
        "concert" to 2.0f, "show" to 1.0f, "cruise" to 2.0f, "trip" to 1.5f
    )

    private val messageKeywords = mapOf(
        "hi " to 1.5f, "hello" to 1.5f, "hey " to 1.5f, "dear " to 1.5f,
        "thanks" to 1.5f, "thank you" to 1.5f, "regards" to 2.0f, "sincerely" to 2.0f,
        "reply" to 2.0f, "respond" to 1.5f, "forwarded" to 2.0f, "sent from" to 2.5f,
        "subject:" to 3.0f, "from:" to 2.0f, "to:" to 1.0f, "cc:" to 2.5f,
        "re:" to 2.5f, "fwd:" to 2.5f, "please" to 1.0f, "let me know" to 1.5f,
        "get back to" to 1.5f, "follow up" to 1.5f, "checking in" to 2.0f,
        "update" to 1.0f, "confirm" to 1.5f, "notification" to 1.5f
    )

    private val noteKeywords = mapOf(
        "note" to 2.0f, "reminder" to 2.0f, "todo" to 2.5f, "to-do" to 2.5f,
        "grocery" to 2.0f, "shopping" to 1.5f, "list" to 1.5f, "ideas" to 2.0f,
        "thoughts" to 2.0f, "brainstorm" to 2.0f, "draft" to 1.5f, "memo" to 2.5f,
        "recipe" to 2.0f, "ingredients" to 2.0f, "steps" to 1.0f, "instructions" to 1.5f,
        "remember" to 1.5f, "don't forget" to 2.0f, "pick up" to 1.5f, "buy" to 1.0f,
        "password" to 1.5f, "code" to 1.0f, "serial" to 1.5f, "reference" to 1.0f
    )

    /**
     * Classify text into bill/message/appointment/note with confidence scores.
     * Returns the classification and confidence (0.0 to 1.0).
     */
    fun classify(text: String): ClassificationResult {
        val lower = text.lowercase()
        val scores = mapOf(
            "bill" to scoreCategory(lower, billKeywords),
            "appointment" to scoreCategory(lower, appointmentKeywords),
            "message" to scoreCategory(lower, messageKeywords),
            "note" to scoreCategory(lower, noteKeywords)
        )

        val totalScore = scores.values.sum()
        if (totalScore < 2.0f) {
            return ClassificationResult("unknown", 0f, scores)
        }

        val best = scores.maxByOrNull { it.value }!!
        val confidence = (best.value / totalScore).coerceIn(0f, 1f)
        Log.d(TAG, "Classified as '${best.key}' (conf=${confidence}) scores=$scores")
        return ClassificationResult(best.key, confidence, scores)
    }

    private fun scoreCategory(text: String, keywords: Map<String, Float>): Float {
        var score = 0f
        for ((keyword, weight) in keywords) {
            if (text.contains(keyword)) {
                score += weight
                // Bonus for early occurrence (more relevant if near the top)
                val firstIndex = text.indexOf(keyword)
                if (firstIndex < 100) score += weight * 0.3f
            }
        }
        return score
    }

    /**
     * Check if the LLM classification matches the fast classifier.
     * Useful for detecting when the LLM might have gotten it wrong.
     */
    fun validateClassification(text: String, llmClassification: String): ValidationResult {
        val result = classify(text)
        val agrees = result.classification == llmClassification || result.classification == "unknown"
        return ValidationResult(
            fastClassification = result.classification,
            fastConfidence = result.confidence,
            llmClassification = llmClassification,
            agrees = agrees
        )
    }
}

data class ClassificationResult(
    val classification: String,
    val confidence: Float,
    val allScores: Map<String, Float>
)

data class ValidationResult(
    val fastClassification: String,
    val fastConfidence: Float,
    val llmClassification: String,
    val agrees: Boolean
)
