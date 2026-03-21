package com.charles.pocketassistant.ai.routing

import com.charles.pocketassistant.data.datastore.AiMode

data class RoutingDecision(
    val mode: AiMode,
    val reason: String
)

class AiRouter {
    fun decide(
        selectedMode: AiMode,
        localAvailable: Boolean,
        ollamaConfigured: Boolean
    ): RoutingDecision {
        return when (selectedMode) {
            AiMode.LOCAL -> {
                if (localAvailable) RoutingDecision(AiMode.LOCAL, "Local only mode")
                else RoutingDecision(AiMode.LOCAL, "Local model unavailable; setup required")
            }
            AiMode.OLLAMA -> RoutingDecision(AiMode.OLLAMA, "Ollama only mode")
            AiMode.AUTO -> {
                when {
                    ollamaConfigured -> RoutingDecision(AiMode.OLLAMA, "Auto: try Ollama first")
                    localAvailable -> RoutingDecision(AiMode.LOCAL, "Auto: local (Ollama not configured)")
                    else -> RoutingDecision(AiMode.LOCAL, "Auto fallback to local")
                }
            }
        }
    }
}
