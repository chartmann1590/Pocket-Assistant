package com.charles.pocketassistant.ai.routing

import com.charles.pocketassistant.data.datastore.AiMode

data class RoutingDecision(
    val mode: AiMode,
    val reason: String
)

class AiRouter(
    private val localThresholdChars: Int = AiRoutingConfig.localTextThresholdChars
) {
    fun decide(
        selectedMode: AiMode,
        textLength: Int,
        localAvailable: Boolean,
        ollamaConfigured: Boolean,
        sourceType: String = "text"
    ): RoutingDecision {
        return when (selectedMode) {
            AiMode.LOCAL -> {
                if (localAvailable) RoutingDecision(AiMode.LOCAL, "Local only mode")
                else RoutingDecision(AiMode.LOCAL, "Local model unavailable; setup required")
            }
            AiMode.OLLAMA -> RoutingDecision(AiMode.OLLAMA, "Ollama only mode")
            AiMode.AUTO -> {
                val heavySource = sourceType == "pdf"
                if (!heavySource && textLength <= localThresholdChars && localAvailable) {
                    RoutingDecision(AiMode.LOCAL, "Auto: local for short input")
                } else if (ollamaConfigured) {
                    RoutingDecision(AiMode.OLLAMA, "Auto: Ollama for heavy input")
                } else {
                    RoutingDecision(AiMode.LOCAL, "Auto fallback to local")
                }
            }
        }
    }
}
