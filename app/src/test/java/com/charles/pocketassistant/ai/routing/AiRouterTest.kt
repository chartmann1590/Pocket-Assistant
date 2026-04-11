package com.charles.pocketassistant.ai.routing

import com.charles.pocketassistant.data.datastore.AiMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AiRouterTest {

    private val router = AiRouter()

    @Test
    fun localModeWhenModelAvailable() {
        val d = router.decide(AiMode.LOCAL, localAvailable = true, ollamaConfigured = false)
        assertEquals(AiMode.LOCAL, d.mode)
    }

    @Test
    fun localModeWhenModelUnavailableStillLocalWithReason() {
        val d = router.decide(AiMode.LOCAL, localAvailable = false, ollamaConfigured = true)
        assertEquals(AiMode.LOCAL, d.mode)
        assertEquals("Local model unavailable; setup required", d.reason)
    }

    @Test
    fun ollamaModeAlwaysOllama() {
        val d = router.decide(AiMode.OLLAMA, localAvailable = false, ollamaConfigured = false)
        assertEquals(AiMode.OLLAMA, d.mode)
        assertEquals("Ollama only mode", d.reason)
    }

    @Test
    fun autoPrefersOllamaWhenConfigured() {
        val d = router.decide(AiMode.AUTO, localAvailable = true, ollamaConfigured = true)
        assertEquals(AiMode.OLLAMA, d.mode)
        assertEquals("Auto: try Ollama first", d.reason)
    }

    @Test
    fun autoUsesLocalWhenOllamaNotConfiguredAndLocalAvailable() {
        val d = router.decide(AiMode.AUTO, localAvailable = true, ollamaConfigured = false)
        assertEquals(AiMode.LOCAL, d.mode)
        assertEquals("Auto: local (Ollama not configured)", d.reason)
    }

    @Test
    fun autoFallbackLocalWhenNeitherConfiguredNorAvailable() {
        val d = router.decide(AiMode.AUTO, localAvailable = false, ollamaConfigured = false)
        assertEquals(AiMode.LOCAL, d.mode)
        assertEquals("Auto fallback to local", d.reason)
    }
}
