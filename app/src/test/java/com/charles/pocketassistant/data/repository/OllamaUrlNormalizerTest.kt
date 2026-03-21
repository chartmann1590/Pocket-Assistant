package com.charles.pocketassistant.data.repository

import com.charles.pocketassistant.data.remote.ollama.OllamaModelListResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class OllamaUrlNormalizerTest {
    @Test
    fun addsSchemeAndTrailingSlash() {
        assertEquals("http://192.168.1.5:11434/", OllamaUrlNormalizer.normalize("192.168.1.5:11434"))
        assertEquals("http://host/", OllamaUrlNormalizer.normalize("http://host"))
    }

    @Test
    fun stripsPastedApiPathSoRetrofitResolvesApiTagsCorrectly() {
        assertEquals(
            "http://192.168.1.5:11434/",
            OllamaUrlNormalizer.normalize("http://192.168.1.5:11434/api/tags")
        )
        assertEquals(
            "http://192.168.1.5:11434/",
            OllamaUrlNormalizer.normalize("http://192.168.1.5:11434/api")
        )
    }

    @Test
    fun ollamaModelJsonUsesNameOrModelField() {
        val json = Json { ignoreUnknownKeys = true }
        val onlyModel = """{"models":[{"model":"gemma3:latest","size":1}]}"""
        val parsed = json.decodeFromString<OllamaModelListResponse>(onlyModel)
        assertEquals("gemma3:latest", parsed.models.orEmpty().first().resolvedName())
    }
}
