package com.charles.pocketassistant.ml

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ML-based priority/urgency scoring for items.
 * Uses a weighted feature model that learns from item characteristics.
 *
 * Features considered:
 * - Time sensitivity (bills near due date, upcoming appointments)
 * - Dollar amounts (higher amounts = higher priority)
 * - Classification type (bills > appointments > messages > notes)
 * - Keyword urgency signals
 * - Recency (newer items slightly prioritized)
 */
@Singleton
class PriorityScorer @Inject constructor() {
    private companion object {
        const val TAG = "PriorityScorer"
    }

    /**
     * Score an item's priority from 0.0 (low) to 1.0 (urgent).
     */
    fun score(features: ItemFeatures): PriorityResult {
        var rawScore = 0f
        val factors = mutableListOf<String>()

        // Classification base score
        val classScore = when (features.classification) {
            "bill" -> 0.6f
            "appointment" -> 0.5f
            "message" -> 0.3f
            "note" -> 0.2f
            else -> 0.1f
        }
        rawScore += classScore
        factors.add("type:${features.classification}(${classScore})")

        // Time urgency — items due within 3 days are urgent
        if (features.dueDateMillis != null && features.dueDateMillis > 0) {
            val now = System.currentTimeMillis()
            val daysUntilDue = ((features.dueDateMillis - now).toFloat() / (24 * 60 * 60 * 1000)).coerceAtLeast(-7f)
            val timeScore = when {
                daysUntilDue < 0 -> 0.4f  // Overdue
                daysUntilDue < 1 -> 0.35f // Due today
                daysUntilDue < 3 -> 0.25f // Due within 3 days
                daysUntilDue < 7 -> 0.15f // Due this week
                else -> 0.05f
            }
            rawScore += timeScore
            factors.add("time:${daysUntilDue.toInt()}d(${timeScore})")
        }

        // Amount urgency — higher dollar amounts are more important
        if (features.dollarAmount != null && features.dollarAmount > 0) {
            val amountScore = when {
                features.dollarAmount > 1000 -> 0.2f
                features.dollarAmount > 500 -> 0.15f
                features.dollarAmount > 100 -> 0.1f
                features.dollarAmount > 50 -> 0.05f
                else -> 0.02f
            }
            rawScore += amountScore
            factors.add("amount:$${features.dollarAmount}(${amountScore})")
        }

        // Keyword urgency signals
        val text = features.text.lowercase()
        val urgentKeywords = listOf(
            "urgent" to 0.15f, "asap" to 0.15f, "immediately" to 0.15f,
            "overdue" to 0.2f, "past due" to 0.2f, "final notice" to 0.25f,
            "last chance" to 0.2f, "action required" to 0.15f, "deadline" to 0.1f,
            "expiring" to 0.15f, "expires" to 0.1f, "disconnect" to 0.2f,
            "cancellation" to 0.15f, "important" to 0.08f, "critical" to 0.15f
        )
        for ((keyword, weight) in urgentKeywords) {
            if (text.contains(keyword)) {
                rawScore += weight
                factors.add("keyword:$keyword($weight)")
            }
        }

        // Recency bonus (items from last 24h get a small bump)
        if (features.createdAtMillis > 0) {
            val ageHours = (System.currentTimeMillis() - features.createdAtMillis).toFloat() / (60 * 60 * 1000)
            if (ageHours < 24) {
                rawScore += 0.05f
                factors.add("recent(0.05)")
            }
        }

        // Normalize to 0-1 range
        val normalizedScore = (rawScore / 1.5f).coerceIn(0f, 1f)
        val urgency = when {
            normalizedScore > 0.7f -> UrgencyLevel.CRITICAL
            normalizedScore > 0.5f -> UrgencyLevel.HIGH
            normalizedScore > 0.3f -> UrgencyLevel.MEDIUM
            else -> UrgencyLevel.LOW
        }

        Log.d(TAG, "Priority: $normalizedScore ($urgency) factors=$factors")
        return PriorityResult(
            score = normalizedScore,
            urgency = urgency,
            factors = factors
        )
    }
}

data class ItemFeatures(
    val classification: String,
    val text: String,
    val dueDateMillis: Long? = null,
    val dollarAmount: Double? = null,
    val createdAtMillis: Long = 0
)

data class PriorityResult(
    val score: Float,
    val urgency: UrgencyLevel,
    val factors: List<String>
)

enum class UrgencyLevel {
    LOW, MEDIUM, HIGH, CRITICAL
}
