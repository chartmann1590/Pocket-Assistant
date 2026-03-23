package com.charles.pocketassistant.ai

import android.util.Log
import com.charles.pocketassistant.data.db.entity.AiResultEntity
import com.charles.pocketassistant.data.db.entity.ItemEntity
import com.charles.pocketassistant.data.db.entity.TaskEntity
import com.charles.pocketassistant.data.db.entity.ReminderEntity
import com.charles.pocketassistant.domain.model.AiExtractionResult
import com.charles.pocketassistant.ml.SemanticSearchEngine
import com.charles.pocketassistant.util.AssistantDateTimeParser
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * Smart data lookup engine that finds answers directly from structured data.
 *
 * This serves two purposes:
 * 1. Build focused, structured context for the LLM so even a 0.6B model can answer
 * 2. Provide a direct fallback answer when the LLM fails
 *
 * The LLM is still tried first — this just makes sure it gets the right data
 * and that the user always gets a useful answer.
 */
@Singleton
class DirectAnswerEngine @Inject constructor(
    private val semanticSearchEngine: SemanticSearchEngine
) {
    private companion object {
        const val TAG = "DirectAnswer"
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Update the semantic search engine's IDF scores with current item corpus.
     * Only needed for TF-IDF fallback (while neural model downloads).
     */
    fun updateSearchIndex(items: List<ItemEntity>, aiResults: Map<String, AiResultEntity>) {
        if (semanticSearchEngine.isNeuralReady()) return // Neural doesn't need IDF
        val corpus = items.map { item ->
            val result = aiResults[item.id]
            buildString {
                append(item.classification.orEmpty()).append(" ")
                append(result?.summary.orEmpty()).append(" ")
                append(item.rawText.take(500))
            }
        }
        semanticSearchEngine.updateIdf(corpus)
    }

    /**
     * Find items relevant to the question using neural semantic search.
     *
     * The neural embedder (Universal Sentence Encoder) understands *meaning*,
     * so "whens my cruise payment" matches "Royal Caribbean booking confirmation"
     * without needing any keyword lists. It learned language from a massive corpus.
     *
     * For temporal questions ("next payment", "upcoming appointment"), we also
     * boost items whose due dates are soonest — because "next" means chronologically next.
     */
    fun findRelevantItems(
        question: String,
        items: List<ItemEntity>,
        aiResults: Map<String, AiResultEntity>
    ): List<RelevantItem> {
        if (items.isEmpty()) return emptyList()

        // Step 1: Parse each item's structured data
        data class ItemData(
            val item: ItemEntity,
            val result: AiResultEntity?,
            val parsed: AiExtractionResult?,
            val embeddingText: String // full text the neural model will embed
        )
        val now = System.currentTimeMillis()
        val itemData = items.map { item ->
            val result = aiResults[item.id]
            val parsed = result?.let { parseExtraction(it) }
            // Feed the neural model EVERYTHING we know about this item.
            // The USE model handles up to ~512 tokens well. More text = more signal.
            // Don't curate or truncate — let the model decide what's relevant.
            val text = buildString {
                val summary = result?.summary?.takeIf { it.isNotBlank() && !it.trimStart().startsWith("{") }
                if (summary != null) {
                    appendLine(summary)
                }
                // Raw text has the actual document content — crucial for matching
                appendLine(item.rawText.take(1500))
            }.trim()
            ItemData(item, result, parsed, text)
        }

        // Step 2: Neural semantic ranking — the model understands meaning, no word lists
        val embeddingTexts = itemData.map { it.embeddingText }
        val semanticScores = semanticSearchEngine.rankBySimilarity(question, embeddingTexts, topK = items.size)
        val scoreByIndex = mutableMapOf<Int, Float>()
        for (scored in semanticScores) {
            scoreByIndex[scored.index] = scored.similarity
        }

        // Step 3: Date awareness — use the neural model to detect temporal intent,
        // then let actual dates from the data decide.
        // We embed the question against temporal phrases and check similarity.
        // If the question is temporally-oriented, past-due items are penalized
        // and future items are boosted by proximity.
        val temporalSim = semanticSearchEngine.rankBySimilarity(
            question,
            listOf("what is my next upcoming future payment or appointment"),
            topK = 1
        ).firstOrNull()?.similarity ?: 0f
        val isTemporalQuestion = temporalSim > 0.5f
        Log.d(TAG, "Temporal intent: sim=$temporalSim → temporal=$isTemporalQuestion")

        val results = itemData.mapIndexed { index, data ->
            var score = scoreByIndex[index] ?: 0f

            if (isTemporalQuestion && data.parsed != null) {
                val dueDateStr = data.parsed.billInfo.dueDate.ifBlank {
                    data.parsed.appointmentInfo.date.ifBlank {
                        data.parsed.entities.dates.firstOrNull() ?: ""
                    }
                }
                if (dueDateStr.isNotBlank()) {
                    val dueMillis = AssistantDateTimeParser.parseToEpochMillis(dueDateStr)
                    if (dueMillis != null) {
                        if (dueMillis < now) {
                            // Past due — this is NOT the "next" anything
                            score -= 0.5f
                            Log.d(TAG, "Past date penalty ${data.item.id}: -0.5")
                        } else {
                            // Future — nearer dates get bigger boost
                            val daysUntil = ((dueMillis - now) / 86_400_000.0).coerceAtLeast(1.0)
                            val boost = (0.5f / daysUntil.toFloat().coerceAtLeast(1f)).coerceAtMost(0.5f)
                            score += boost
                            Log.d(TAG, "Future date boost ${data.item.id}: +$boost (${daysUntil.toInt()}d)")
                        }
                    }
                }
            }

            Log.d(TAG, "Final ${data.item.id} [${data.item.classification}]: score=$score")

            RelevantItem(
                item = data.item,
                aiResult = data.result,
                extraction = data.parsed,
                relevanceScore = score
            )
        }

        return results
            .filter { it.relevanceScore > 0.01f }
            .sortedByDescending { it.relevanceScore }
    }

    /**
     * Build structured context that's easy for even a small LLM to use.
     * Instead of raw OCR text, presents data as clear key-value fields.
     */
    fun buildStructuredContext(
        question: String,
        relevantItems: List<RelevantItem>,
        allItems: List<ItemEntity>,
        aiResults: Map<String, AiResultEntity>,
        tasks: List<TaskEntity>,
        reminders: List<ReminderEntity>,
        threadHistory: List<Pair<String, String>> // role to text
    ): String = buildString {
        // Chat history (compact)
        if (threadHistory.isNotEmpty()) {
            appendLine("Chat history:")
            for ((role, text) in threadHistory.takeLast(4)) {
                appendLine("$role: ${text.take(200).replace('\n', ' ')}")
            }
            appendLine()
        }

        // Relevant items — structured format the LLM can easily read
        if (relevantItems.isNotEmpty()) {
            appendLine("RELEVANT DATA (most likely answers are here):")
            for ((i, ri) in relevantItems.take(3).withIndex()) {
                appendLine("--- Item ${i + 1} [${ri.item.classification ?: "unknown"}] ---")
                appendLine("ID: ${ri.item.id}")

                val ext = ri.extraction
                if (ext != null) {
                    if (ext.summary.isNotBlank()) appendLine("Summary: ${ext.summary}")

                    // Bill info
                    if (ext.billInfo.vendor.isNotBlank() || ext.billInfo.amount.isNotBlank() || ext.billInfo.dueDate.isNotBlank()) {
                        if (ext.billInfo.vendor.isNotBlank()) appendLine("Vendor: ${ext.billInfo.vendor}")
                        if (ext.billInfo.amount.isNotBlank()) appendLine("Amount: ${ext.billInfo.amount}")
                        if (ext.billInfo.dueDate.isNotBlank()) {
                            val parsed = AssistantDateTimeParser.parseToEpochMillis(ext.billInfo.dueDate)
                            val display = AssistantDateTimeParser.formatForDisplay(parsed)
                            appendLine("Due date: ${ext.billInfo.dueDate} ($display)")
                        }
                    }

                    // Appointment info
                    if (ext.appointmentInfo.title.isNotBlank()) {
                        appendLine("Appointment: ${ext.appointmentInfo.title}")
                        if (ext.appointmentInfo.date.isNotBlank()) appendLine("Date: ${ext.appointmentInfo.date}")
                        if (ext.appointmentInfo.time.isNotBlank()) appendLine("Time: ${ext.appointmentInfo.time}")
                        if (ext.appointmentInfo.location.isNotBlank()) appendLine("Location: ${ext.appointmentInfo.location}")
                    }

                    // Key entities
                    if (ext.entities.amounts.isNotEmpty()) appendLine("Amounts: ${ext.entities.amounts.joinToString(", ")}")
                    if (ext.entities.dates.isNotEmpty()) appendLine("Dates: ${ext.entities.dates.joinToString(", ")}")
                    if (ext.entities.organizations.isNotEmpty()) appendLine("Organizations: ${ext.entities.organizations.joinToString(", ")}")
                    if (ext.entities.people.isNotEmpty()) appendLine("People: ${ext.entities.people.joinToString(", ")}")
                    if (ext.entities.locations.isNotEmpty()) appendLine("Locations: ${ext.entities.locations.joinToString(", ")}")
                } else {
                    // No extraction — use summary or raw text
                    val summary = ri.aiResult?.summary?.takeIf { it.isNotBlank() && !it.trimStart().startsWith("{") }
                    appendLine(summary ?: ri.item.rawText.take(300).replace('\n', ' '))
                }
                appendLine()
            }
        }

        // Other items (compact, for general awareness)
        val otherItems = allItems.filter { item -> relevantItems.none { it.item.id == item.id } }
        if (otherItems.isNotEmpty()) {
            appendLine("Other items:")
            for (item in otherItems.take(5)) {
                val result = aiResults[item.id]
                val summary = result?.summary?.takeIf { it.isNotBlank() && !it.trimStart().startsWith("{") }
                    ?: item.rawText.take(80).replace('\n', ' ')
                appendLine("#${item.id} [${item.classification ?: "unknown"}] $summary")
            }
            appendLine()
        }

        // Tasks
        if (tasks.isNotEmpty()) {
            appendLine("Tasks:")
            for (task in tasks.take(10)) {
                val due = task.dueAt?.let { " (due ${AssistantDateTimeParser.formatForDisplay(it)})" } ?: ""
                appendLine("- ${task.title}${due}")
            }
            appendLine()
        }

        // Reminders
        if (reminders.isNotEmpty()) {
            appendLine("Reminders:")
            for (reminder in reminders.take(5)) {
                appendLine("- ${reminder.title} at ${AssistantDateTimeParser.formatForDisplay(reminder.remindAt)}")
            }
        }
    }.trim()

    /**
     * Try to construct a direct answer from structured data.
     * Called when the LLM produces a useless response.
     * Returns null if we can't construct a good answer either.
     */
    fun tryDirectAnswer(
        question: String,
        relevantItems: List<RelevantItem>,
        tasks: List<TaskEntity>,
        reminders: List<ReminderEntity>
    ): DirectAnswer? {
        if (relevantItems.isEmpty() && tasks.isEmpty() && reminders.isEmpty()) return null

        val lq = question.lowercase()

        // Detect question intent
        val isWhenQuestion = lq.startsWith("when") || lq.contains("when is") || lq.contains("what date") || lq.contains("what time")
        val isHowMuchQuestion = lq.contains("how much") || lq.contains("what amount") || lq.contains("cost") || lq.contains("price") || lq.contains("owe")
        val isWhoQuestion = lq.startsWith("who") || lq.contains("who is") || lq.contains("from whom")
        val isWhereQuestion = lq.startsWith("where") || lq.contains("where is") || lq.contains("location") || lq.contains("address")
        val isListQuestion = lq.startsWith("list") || lq.startsWith("show") || lq.contains("what are my") || lq.contains("do i have")
        val isDueQuestion = lq.contains("due") || lq.contains("payment") || lq.contains("pay") || lq.contains("bill")
        val isNextQuestion = lq.contains("next") || lq.contains("upcoming")

        val topItem = relevantItems.firstOrNull()
        val ext = topItem?.extraction

        // Bill questions
        if (topItem != null && ext != null && (topItem.item.classification == "bill" || isDueQuestion)) {
            val bill = ext.billInfo
            val vendor = bill.vendor.ifBlank { ext.entities.organizations.firstOrNull() ?: "" }
            val amount = bill.amount.ifBlank { ext.entities.amounts.firstOrNull() ?: "" }
            val dueDate = bill.dueDate.ifBlank { ext.entities.dates.firstOrNull() ?: "" }

            if (isWhenQuestion || isDueQuestion) {
                if (dueDate.isNotBlank()) {
                    val parsed = AssistantDateTimeParser.parseToEpochMillis(dueDate)
                    val display = if (parsed != null) AssistantDateTimeParser.formatForDisplay(parsed) else dueDate
                    val vendorStr = if (vendor.isNotBlank()) "Your $vendor payment" else "Your payment"
                    val amountStr = if (amount.isNotBlank()) " of $amount" else ""
                    return DirectAnswer(
                        reply = "$vendorStr$amountStr is due $display.",
                        itemId = topItem.item.id,
                        classification = "bill"
                    )
                }
            }
            if (isHowMuchQuestion && amount.isNotBlank()) {
                val vendorStr = if (vendor.isNotBlank()) "for $vendor " else ""
                return DirectAnswer(
                    reply = "The amount ${vendorStr}is $amount.",
                    itemId = topItem.item.id,
                    classification = "bill"
                )
            }
        }

        // Appointment questions
        if (topItem != null && ext != null && topItem.item.classification == "appointment") {
            val appt = ext.appointmentInfo
            val title = appt.title.ifBlank { ext.summary.take(50) }
            val date = appt.date.ifBlank { ext.entities.dates.firstOrNull() ?: "" }
            val time = appt.time.ifBlank { ext.entities.times.firstOrNull() ?: "" }
            val location = appt.location.ifBlank { ext.entities.locations.firstOrNull() ?: "" }

            if (isWhenQuestion && date.isNotBlank()) {
                val parsed = AssistantDateTimeParser.parseToEpochMillis(date)
                val display = if (parsed != null) AssistantDateTimeParser.formatForDisplay(parsed) else date
                val timeStr = if (time.isNotBlank()) " at $time" else ""
                return DirectAnswer(
                    reply = "Your appointment \"$title\" is on $display$timeStr.",
                    itemId = topItem.item.id,
                    classification = "appointment"
                )
            }
            if (isWhereQuestion && location.isNotBlank()) {
                return DirectAnswer(
                    reply = "\"$title\" is at $location.",
                    itemId = topItem.item.id,
                    classification = "appointment"
                )
            }
        }

        // General item: if we found something relevant, summarize it
        if (topItem != null && ext != null && topItem.relevanceScore > 0.2f) {
            val summary = ext.summary.ifBlank { topItem.aiResult?.summary ?: "" }
            if (summary.isNotBlank()) {
                val dates = ext.entities.dates
                val amounts = ext.entities.amounts

                // Build a structured answer from whatever we have
                val details = buildString {
                    append(summary)
                    if (dates.isNotEmpty() && isWhenQuestion) {
                        append(" Date(s): ${dates.joinToString(", ")}.")
                    }
                    if (amounts.isNotEmpty() && isHowMuchQuestion) {
                        append(" Amount(s): ${amounts.joinToString(", ")}.")
                    }
                }
                return DirectAnswer(
                    reply = details,
                    itemId = topItem.item.id,
                    classification = topItem.item.classification
                )
            }
        }

        // Task/reminder lookup
        if (isListQuestion || isNextQuestion) {
            if (lq.contains("task") && tasks.isNotEmpty()) {
                val taskList = tasks.take(5).joinToString("\n") { t ->
                    val due = t.dueAt?.let { " (due ${AssistantDateTimeParser.formatForDisplay(it)})" } ?: ""
                    "- ${t.title}$due"
                }
                return DirectAnswer(reply = "Here are your open tasks:\n$taskList", itemId = null, classification = null)
            }
            if (lq.contains("reminder") && reminders.isNotEmpty()) {
                val reminderList = reminders.take(5).joinToString("\n") { r ->
                    "- ${r.title} at ${AssistantDateTimeParser.formatForDisplay(r.remindAt)}"
                }
                return DirectAnswer(reply = "Upcoming reminders:\n$reminderList", itemId = null, classification = null)
            }
        }

        Log.d(TAG, "No direct answer for: $question (topScore=${topItem?.relevanceScore})")
        return null
    }

    private fun parseExtraction(result: AiResultEntity): AiExtractionResult? {
        if (result.extractedJson.isBlank()) return null
        return try {
            json.decodeFromString(AiExtractionResult.serializer(), result.extractedJson)
        } catch (e: Exception) {
            Log.d(TAG, "Failed to parse extractedJson for ${result.itemId}: ${e.message}")
            null
        }
    }
}

data class RelevantItem(
    val item: ItemEntity,
    val aiResult: AiResultEntity?,
    val extraction: AiExtractionResult?,
    val relevanceScore: Float
)

data class DirectAnswer(
    val reply: String,
    val itemId: String?,
    val classification: String?
)
