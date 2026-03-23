package com.charles.pocketassistant.ml

import android.util.Log
import com.google.mlkit.nl.smartreply.SmartReply
import com.google.mlkit.nl.smartreply.SmartReplySuggestionResult
import com.google.mlkit.nl.smartreply.TextMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * On-device smart reply suggestions using ML Kit.
 * Generates contextual quick-reply options for conversations.
 * English only.
 */
@Singleton
class SmartReplyEngine @Inject constructor() {
    private companion object {
        const val TAG = "SmartReply"
    }

    private val generator = SmartReply.getClient()

    /**
     * Generate up to 3 smart reply suggestions based on conversation history.
     *
     * @param conversation List of messages, each with text, timestamp, and whether it's from the local user
     * @return List of suggested reply strings (up to 3)
     */
    suspend fun suggestReplies(conversation: List<ChatMessage>): List<String> {
        if (conversation.isEmpty()) return emptyList()

        val messages = conversation.map { msg ->
            if (msg.isLocalUser) {
                TextMessage.createForLocalUser(msg.text, msg.timestampMillis)
            } else {
                TextMessage.createForRemoteUser(msg.text, msg.timestampMillis, "assistant")
            }
        }

        return try {
            val result = generator.suggestReplies(messages).await()
            when (result.status) {
                SmartReplySuggestionResult.STATUS_SUCCESS -> {
                    val suggestions = result.suggestions.map { it.text }
                    Log.d(TAG, "Generated ${suggestions.size} suggestions: $suggestions")
                    suggestions
                }
                SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE -> {
                    Log.d(TAG, "Language not supported for smart reply")
                    emptyList()
                }
                else -> {
                    Log.d(TAG, "No smart reply suggestions (status=${result.status})")
                    emptyList()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Smart reply generation failed", e)
            emptyList()
        }
    }

    data class ChatMessage(
        val text: String,
        val timestampMillis: Long,
        val isLocalUser: Boolean
    )
}
