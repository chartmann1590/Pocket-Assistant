package com.charles.pocketassistant.ai.parser

import android.util.Log
import com.charles.pocketassistant.domain.model.AppointmentInfo
import com.charles.pocketassistant.domain.model.AssistantActionSuggestion
import com.charles.pocketassistant.domain.model.AssistantChatResult
import com.charles.pocketassistant.domain.model.AssistantItemReference
import com.charles.pocketassistant.domain.model.AiExtractionResult
import com.charles.pocketassistant.domain.model.BillInfo
import com.charles.pocketassistant.domain.model.Entities
import com.charles.pocketassistant.domain.model.ExtractedTask
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class AiJsonParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ── Extraction parsing ──────────────────────────────────────────────

    fun parseOrFallback(raw: String): AiExtractionResult {
        val trimmed = raw.trim()
        // Attempt 1: direct parse
        tryDeserializeExtraction(trimmed)?.let { return it }
        // Attempt 2: extract JSON block and repair
        val jsonBlock = extractJsonBlock(trimmed)
        if (jsonBlock != null) {
            tryDeserializeExtraction(jsonBlock)?.let { return it }
            val repaired = repairJson(jsonBlock)
            if (repaired != jsonBlock) {
                tryDeserializeExtraction(repaired)?.let {
                    Log.d(TAG, "Parsed after JSON repair")
                    return it
                }
            }
        }
        // Attempt 3: field-level regex recovery
        val recovered = recoverExtractionFields(trimmed)
        if (recovered.classification != "unknown" || recovered.summary.length > 30) {
            Log.d(TAG, "Used field-level recovery: class=${recovered.classification}")
            return recovered
        }
        return fallback(raw)
    }

    private fun tryDeserializeExtraction(text: String): AiExtractionResult? {
        return try {
            // Try full nested schema first
            json.decodeFromString(AiExtractionResult.serializer(), text)
        } catch (e1: Exception) {
            // Try flat schema (local model output) → map to nested
            try {
                val flat = json.decodeFromString(FlatExtractionResult.serializer(), text)
                flat.toNested()
            } catch (_: Exception) {
                Log.w(TAG, "Deserialize failed: ${e1.message?.take(100)}")
                null
            }
        }
    }

    /**
     * Flat schema that mirrors the local model's simplified output.
     * Maps to [AiExtractionResult] after deserialization.
     */
    @kotlinx.serialization.Serializable
    private data class FlatExtractionResult(
        val classification: String = "unknown",
        val summary: String = "",
        val people: List<String> = emptyList(),
        val orgs: List<String> = emptyList(),
        val amounts: List<String> = emptyList(),
        val dates: List<String> = emptyList(),
        val times: List<String> = emptyList(),
        val locations: List<String> = emptyList(),
        val tasks: List<FlatTask> = emptyList(),
        val billInfo: BillInfo = BillInfo(),
        val appointmentInfo: AppointmentInfo = AppointmentInfo()
    ) {
        fun toNested() = AiExtractionResult(
            classification = classification,
            summary = summary,
            entities = Entities(
                people = people,
                organizations = orgs,
                amounts = amounts,
                dates = dates,
                times = times,
                locations = locations
            ),
            tasks = tasks.map { ExtractedTask(title = it.title, details = "", dueDate = it.due) },
            billInfo = billInfo,
            appointmentInfo = appointmentInfo
        )
    }

    @kotlinx.serialization.Serializable
    private data class FlatTask(
        val title: String = "",
        val due: String = ""
    )

    // ── Assistant chat parsing ───────────────────────────────────────────

    fun parseAssistantChatOrFallback(raw: String): AssistantChatResult {
        val trimmed = raw.trim()
        // Attempt 1: direct parse
        tryDeserializeAssistant(trimmed)?.let { return it }
        // Attempt 2: extract JSON block and repair
        val jsonBlock = extractJsonBlock(trimmed)
        if (jsonBlock != null) {
            tryDeserializeAssistant(jsonBlock)?.let { return it }
            val repaired = repairJson(jsonBlock)
            if (repaired != jsonBlock) {
                tryDeserializeAssistant(repaired)?.let {
                    Log.d(TAG, "Assistant parsed after JSON repair")
                    return it
                }
            }
        }
        // Attempt 3: regex recovery
        return recoverAssistantFields(trimmed)
    }

    private fun tryDeserializeAssistant(text: String): AssistantChatResult? {
        return try {
            sanitizeAssistant(json.decodeFromString(AssistantChatResult.serializer(), text))
        } catch (_: Exception) {
            null
        }
    }

    // ── JSON repair ─────────────────────────────────────────────────────

    /**
     * Fix common small-model JSON errors:
     * - trailing commas before } or ]
     * - unbalanced braces/brackets (truncated output)
     * - single-quoted strings
     */
    private fun repairJson(raw: String): String {
        var s = raw
        // Trailing commas: ,} or ,]
        s = s.replace(Regex(",\\s*([}\\]])"), "$1")
        // Single quotes → double quotes (but not inside strings — best-effort)
        if (!s.contains('"') && s.contains('\'')) {
            s = s.replace('\'', '"')
        }
        // Balance unclosed brackets/braces
        val openBraces = s.count { it == '{' } - s.count { it == '}' }
        val openBrackets = s.count { it == '[' } - s.count { it == ']' }
        repeat(openBrackets.coerceAtLeast(0)) { s += "]" }
        repeat(openBraces.coerceAtLeast(0)) { s += "}" }
        return s
    }

    // ── Field-level regex recovery ──────────────────────────────────────

    /**
     * Extract individual fields from broken JSON when full deserialization
     * fails. Recovers as much structured data as possible.
     */
    private fun recoverExtractionFields(raw: String): AiExtractionResult {
        val classification = extractQuotedField(raw, "classification")
            ?.lowercase()
            ?.let { if (it in VALID_CLASSIFICATIONS) it else null }
            ?: "unknown"
        val summary = extractQuotedField(raw, "summary")
            ?: raw.take(300).replace(Regex("[{}\\[\\]\"]"), " ").trim()
        return AiExtractionResult(
            classification = classification,
            summary = summary,
            entities = Entities(
                people = extractArrayField(raw, "people"),
                organizations = extractArrayField(raw, "orgs")
                    .ifEmpty { extractArrayField(raw, "organizations") },
                amounts = extractArrayField(raw, "amounts"),
                dates = extractArrayField(raw, "dates"),
                times = extractArrayField(raw, "times"),
                locations = extractArrayField(raw, "locations")
            ),
            billInfo = BillInfo(
                vendor = extractQuotedField(raw, "vendor").orEmpty(),
                amount = extractQuotedField(raw, "amount").orEmpty(),
                dueDate = extractQuotedField(raw, "dueDate").orEmpty()
            ),
            appointmentInfo = AppointmentInfo(
                title = extractQuotedField(raw, "title").orEmpty(),
                date = extractQuotedField(raw, "date").orEmpty(),
                time = extractQuotedField(raw, "time").orEmpty(),
                location = extractQuotedField(raw, "location").orEmpty()
            )
        )
    }

    /**
     * Extract reply and any recoverable actions from broken assistant JSON.
     */
    private fun recoverAssistantFields(raw: String): AssistantChatResult {
        val reply = extractQuotedField(raw, "reply")
            ?: extractPlainReply(raw)
            ?: if (raw.trimStart().startsWith("{")) {
                "I found related information, but the response format was broken."
            } else {
                raw.take(220).replace(Regex("[{}\\[\\]\"]"), " ").trim()
                    .ifBlank { "I could not parse the assistant response." }
            }
        // Try to recover actions from partial JSON
        val actions = recoverActions(raw)
        return AssistantChatResult(reply = reply, actions = actions)
    }

    /**
     * Look for action-like structures in broken JSON.
     */
    private fun recoverActions(raw: String): List<AssistantActionSuggestion> {
        val typePattern = Regex(""""type"\s*:\s*"(create_task|create_reminder)"""")
        val actions = mutableListOf<AssistantActionSuggestion>()
        typePattern.findAll(raw).forEach { match ->
            // Search nearby text for title and scheduledFor
            val nearby = raw.substring(
                (match.range.first - 20).coerceAtLeast(0),
                (match.range.last + 300).coerceIn(0, raw.length)
            )
            val type = match.groupValues[1]
            val title = extractQuotedField(nearby, "title").orEmpty()
            val scheduledFor = extractQuotedField(nearby, "scheduledFor").orEmpty()
            val details = extractQuotedField(nearby, "details").orEmpty()
            if (title.isNotBlank()) {
                actions.add(
                    AssistantActionSuggestion(
                        type = type,
                        title = title,
                        details = details,
                        scheduledFor = scheduledFor
                    )
                )
            }
        }
        return actions.take(3)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun fallback(raw: String): AiExtractionResult {
        return AiExtractionResult(
            classification = "unknown",
            summary = raw.take(300).replace(Regex("[{}\\[\\]\"]"), " ").trim()
                .ifBlank { "Could not parse model output." }
        )
    }

    private fun sanitizeAssistant(result: AssistantChatResult): AssistantChatResult {
        return result.copy(
            reply = result.reply.trim(),
            actions = result.actions
                .map {
                    it.copy(
                        type = it.type.trim().lowercase(),
                        title = it.title.trim(),
                        details = it.details.trim(),
                        scheduledFor = it.scheduledFor.trim(),
                        confirmationLabel = it.confirmationLabel.trim(),
                        fallbackNote = it.fallbackNote.trim()
                    )
                }
                .filter { it.type in setOf("create_task", "create_reminder") && it.title.isNotBlank() }
                .take(3),
            references = result.references
                .map {
                    AssistantItemReference(
                        itemId = it.itemId.trim(),
                        label = it.label.trim()
                    )
                }
                .filter { it.itemId.isNotBlank() }
                .take(3)
        )
    }

    private fun extractJsonBlock(raw: String): String? =
        Regex("\\{[\\s\\S]*\\}").find(raw)?.value

    private fun extractQuotedField(raw: String, fieldName: String): String? {
        val match = Regex("\"$fieldName\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", RegexOption.DOT_MATCHES_ALL)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?: return null
        return runCatching { json.decodeFromString(String.serializer(), "\"$match\"") }
            .getOrElse {
                match
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
            }
            .trim()
            .takeIf { it.isNotBlank() }
    }

    /**
     * Extract a JSON array of strings: "field": ["a", "b", "c"]
     * Handles both quoted strings and unquoted numbers/values.
     */
    private fun extractArrayField(raw: String, fieldName: String): List<String> {
        val arrayPattern = Regex("\"$fieldName\"\\s*:\\s*\\[(.*?)\\]", RegexOption.DOT_MATCHES_ALL)
        val arrayContent = arrayPattern.find(raw)?.groupValues?.getOrNull(1) ?: return emptyList()
        if (arrayContent.isBlank()) return emptyList()
        // Match quoted strings and bare values
        return Regex("""(?:"((?:\\.|[^"\\])*)"|([^,\[\]"\s]+))""")
            .findAll(arrayContent)
            .mapNotNull { m ->
                (m.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
                    ?: m.groupValues.getOrNull(2)?.takeIf { it.isNotBlank() })
                    ?.trim()
            }
            .toList()
    }

    private fun extractPlainReply(raw: String): String? {
        return Regex("""\breply\b\s*[:=-]\s*(.+)""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.lineSequence()
            ?.firstOrNull()
            ?.trim()
            ?.trim('"', '{', '}', ',')
            ?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val TAG = "AiJsonParser"
        val VALID_CLASSIFICATIONS = setOf("bill", "message", "appointment", "note")
    }
}
