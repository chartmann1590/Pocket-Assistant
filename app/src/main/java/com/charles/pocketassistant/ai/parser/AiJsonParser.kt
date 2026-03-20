package com.charles.pocketassistant.ai.parser

import com.charles.pocketassistant.domain.model.AssistantActionSuggestion
import com.charles.pocketassistant.domain.model.AssistantChatResult
import com.charles.pocketassistant.domain.model.AssistantItemReference
import com.charles.pocketassistant.domain.model.AiExtractionResult
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class AiJsonParser {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseOrFallback(raw: String): AiExtractionResult {
        val trimmed = raw.trim()
        return try {
            json.decodeFromString(AiExtractionResult.serializer(), trimmed)
        } catch (_: Exception) {
            val jsonBlock = extractJsonBlock(trimmed)
            if (jsonBlock != null) {
                runCatching { json.decodeFromString(AiExtractionResult.serializer(), jsonBlock) }
                    .getOrElse { fallback(raw) }
            } else {
                fallback(raw)
            }
        }
    }

    fun parseAssistantChatOrFallback(raw: String): AssistantChatResult {
        val trimmed = raw.trim()
        return try {
            sanitizeAssistant(json.decodeFromString(AssistantChatResult.serializer(), trimmed))
        } catch (_: Exception) {
            val jsonBlock = extractJsonBlock(trimmed)
            if (jsonBlock != null) {
                runCatching { sanitizeAssistant(json.decodeFromString(AssistantChatResult.serializer(), jsonBlock)) }
                    .getOrElse { assistantFallback(raw) }
            } else {
                assistantFallback(raw)
            }
        }
    }

    private fun fallback(raw: String): AiExtractionResult {
        return AiExtractionResult(
            classification = "unknown",
            summary = raw.take(300).ifBlank { "Could not parse model output." }
        )
    }

    private fun assistantFallback(raw: String): AssistantChatResult {
        val extractedReply = extractQuotedField(raw, "reply")
        val plainReply = extractPlainReply(raw)
        return AssistantChatResult(
            reply = extractedReply
                ?: plainReply
                ?: if (raw.trimStart().startsWith("{")) {
                    "I found related information, but the model returned it in a broken format."
                } else {
                    raw.take(220).ifBlank { "I could not parse the assistant response." }
                },
            actions = emptyList()
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

    private fun extractJsonBlock(raw: String): String? = Regex("\\{[\\s\\S]*\\}").find(raw)?.value

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
}
