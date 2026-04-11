package com.charles.pocketassistant.ai.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiJsonParserTest {

    private val parser = AiJsonParser()

    @Test
    fun parseNestedExtractionJson() {
        val raw = """
            {
              "classification": "bill",
              "summary": "Pay utility bill",
              "entities": {
                "people": [],
                "organizations": ["ACME"],
                "amounts": ["42.00 USD"],
                "dates": ["2026-04-15"],
                "times": [],
                "locations": []
              },
              "tasks": [],
              "billInfo": { "vendor": "ACME", "amount": "42", "dueDate": "2026-04-15" },
              "appointmentInfo": {}
            }
        """.trimIndent()

        val result = parser.parseOrFallback(raw)
        assertEquals("bill", result.classification)
        assertEquals("Pay utility bill", result.summary)
        assertEquals(listOf("ACME"), result.entities.organizations)
        assertEquals("ACME", result.billInfo.vendor)
    }

    @Test
    fun parseFlatLocalSchemaWhenNestedDeserializeFails() {
        // Nested AiExtractionResult decode fails when "entities" is not an object; flat schema then maps people/orgs.
        val raw = """{"classification":"note","summary":"Remember milk","people":["Alice"],"orgs":[],"amounts":[],"dates":[],"times":[],"locations":[],"tasks":[],"entities":[]}"""
        val result = parser.parseOrFallback(raw)
        assertEquals("note", result.classification)
        assertEquals("Remember milk", result.summary)
        assertEquals(listOf("Alice"), result.entities.people)
    }

    @Test
    fun parseAssistantChatJson() {
        val raw = """{"reply":"Here is the answer.","actions":[]}"""
        val result = parser.parseAssistantChatOrFallback(raw)
        assertEquals("Here is the answer.", result.reply)
        assertTrue(result.actions.isEmpty())
    }

    @Test
    fun parseAssistantChatWithMarkdownFence() {
        val raw = """
            Sure:
            ```json
            {"reply":"Done."}
            ```
        """.trimIndent()
        val result = parser.parseAssistantChatOrFallback(raw)
        assertEquals("Done.", result.reply)
    }

    @Test
    fun extractionFallbackNeverReturnsEmptySummaryForGarbage() {
        val raw = "not json at all but some model text"
        val result = parser.parseOrFallback(raw)
        assertEquals("unknown", result.classification)
        assertTrue(result.summary.isNotBlank())
    }
}
