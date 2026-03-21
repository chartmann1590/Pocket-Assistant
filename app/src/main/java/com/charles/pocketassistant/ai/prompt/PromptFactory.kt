package com.charles.pocketassistant.ai.prompt

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object PromptFactory {

    // ── Full schema used by larger models (Ollama) ──────────────────────
    private const val SCHEMA = """
{
  "classification": "bill | message | appointment | note | unknown",
  "summary": "short summary",
  "entities": {
    "people": [],
    "organizations": [],
    "amounts": [],
    "dates": [],
    "times": [],
    "locations": []
  },
  "tasks": [
    { "title": "", "details": "", "dueDate": "" }
  ],
  "billInfo": { "vendor": "", "amount": "", "dueDate": "" },
  "appointmentInfo": { "title": "", "date": "", "time": "", "location": "" }
}
"""

    // ── Compact schema for small on-device models ───────────────────────
    // Flat, fewer fields, easier for 0.6B–3B models to follow.
    private const val LOCAL_SCHEMA = """
{"classification":"bill|message|appointment|note","summary":"one sentence","people":[],"orgs":[],"amounts":[],"dates":[],"times":[],"locations":[],"tasks":[{"title":"","due":""}],"billInfo":{"vendor":"","amount":"","dueDate":""},"appointmentInfo":{"title":"","date":"","time":"","location":""}}
"""

    // One concrete example anchors the model's output format.
    private const val LOCAL_EXTRACTION_EXAMPLE = """
Example:
Input: "Your Verizon bill of $89.50 is due March 28, 2026. Account holder: John Smith."
Output: {"classification":"bill","summary":"Verizon bill $89.50 due March 28, 2026 for John Smith","people":["John Smith"],"orgs":["Verizon"],"amounts":["$89.50"],"dates":["March 28, 2026"],"times":[],"locations":[],"tasks":[{"title":"Pay Verizon bill","due":"2026-03-28"}],"billInfo":{"vendor":"Verizon","amount":"$89.50","dueDate":"2026-03-28"},"appointmentInfo":{"title":"","date":"","time":"","location":""}}
"""

    // ── Extraction prompts ──────────────────────────────────────────────

    /** Full extraction prompt for Ollama / larger models. */
    fun general(text: String): String = """
You are an extraction engine. Read the input text carefully and extract information from it.
Return strict JSON only with no extra text.
Use this exact schema:
$SCHEMA

Important:
- Read the ENTIRE input below before classifying. Base your summary and classification ONLY on what the text actually says.
- "summary" must describe the specific content of this document (names, amounts, dates mentioned).
- "classification" must match the content: "bill" for payments/invoices, "appointment" for scheduled events, "message" for messages/emails, "note" for everything else.
- Extract ALL people, organizations, amounts, dates, times, and locations mentioned in the text.
- Stop immediately after the closing brace.

Input:
$text
""".trimIndent()

    /**
     * Compact extraction prompt optimized for small on-device models.
     * Shorter instructions, flat schema, and a few-shot example.
     */
    fun generalLocal(text: String): String = """
Extract information from the input below. Return JSON only.
Schema:
$LOCAL_SCHEMA
$LOCAL_EXTRACTION_EXAMPLE
Rules:
- classification: "bill" for payments/invoices/due dates, "appointment" for events with a date/time, "message" for emails/texts, "note" for other.
- summary: one sentence describing the document with specific names, amounts, dates.
- Extract every person, organization, dollar amount, date, time, location.
- If it is a bill, fill billInfo with vendor, amount, dueDate.
- If it is an appointment, fill appointmentInfo with title, date, time, location.
- Output JSON only. Stop after the closing brace.

Input:
$text
""".trimIndent()

    /**
     * Retry extraction prompt — shorter and more directive, used when
     * the first attempt produced low-quality output.
     */
    fun generalLocalRetry(text: String, previousClassification: String?): String {
        val hint = if (previousClassification != null && previousClassification != "unknown") {
            "This document is likely a $previousClassification. "
        } else ""
        return """
${hint}Read the text and return JSON matching this schema:
$LOCAL_SCHEMA
Focus on: classification, summary (one sentence with key details), amounts, dates, organizations.
JSON only, no other text.

Input:
$text
""".trimIndent()
    }

    fun bill(text: String): String = general("Focus on bill fields.\n$text")
    fun message(text: String): String = general("Focus on action items and follow-up.\n$text")
    fun appointment(text: String): String = general("Focus on appointment title/date/time/location.\n$text")

    // ── Date/time helper ────────────────────────────────────────────────

    private fun currentDateTime(): String {
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US))
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
        return "$date at $time"
    }

    // ── Assistant chat schemas ──────────────────────────────────────────

    private const val ASSISTANT_SCHEMA = """
{
  "reply": "short, direct answer for the user",
  "actions": [
    {
      "type": "create_task | create_reminder",
      "title": "short title",
      "details": "optional details",
      "scheduledFor": "ISO-8601 local date/time like 2026-03-25T14:30, or empty",
      "confirmationLabel": "button label like Add reminder",
      "fallbackNote": "optional warning if information is incomplete"
    }
  ],
  "references": [
    {
      "itemId": "exact item id from the provided app context",
      "label": "short button label like View bill or Open appointment"
    }
  ]
}
"""

    private const val ASSISTANT_QA_SCHEMA = """
{
  "reply": "short, direct answer for the user",
  "references": [
    {
      "itemId": "exact item id from the provided app context",
      "label": "short button label like View bill or Open appointment"
    }
  ]
}
"""

    // Stripped-down schema for local models — no confirmationLabel/fallbackNote
    private const val LOCAL_ASSISTANT_ACTION_SCHEMA = """
{"reply":"your answer","actions":[{"type":"create_task or create_reminder","title":"short title","details":"details","scheduledFor":"2026-03-25T14:30 or empty"}],"references":[{"itemId":"exact id from context","label":"View bill"}]}
"""
    private const val LOCAL_ASSISTANT_QA_SCHEMA = """
{"reply":"your answer","references":[{"itemId":"exact id from context","label":"View bill"}]}
"""

    private const val LOCAL_ASSISTANT_EXAMPLE = """
Example:
User: "When is my electric bill due?"
Context: #abc [bill] National Grid bill $120.00 due April 5, 2026
Output: {"reply":"Your National Grid electric bill of $120.00 is due April 5, 2026.","actions":[],"references":[{"itemId":"abc","label":"View bill"}]}
"""

    // ── Assistant chat prompts ──────────────────────────────────────────

    /** Full assistant prompt for Ollama / larger models. */
    fun assistantChat(question: String, context: String, allowActions: Boolean): String = """
You are Pocket Assistant, a privacy-first Android organizer assistant.
Today is ${currentDateTime()}.
Return strict JSON only with no markdown and no extra text.
Use this exact schema:
${if (allowActions) ASSISTANT_SCHEMA else ASSISTANT_QA_SCHEMA}

Rules:
- Answer the user's question using ONLY the app context below. Summarize what is relevant.
- If the user asks about their schedule, tasks, or upcoming items, list the relevant ones from context.
- If the answer is not in context, say what is missing instead of inventing details.
${if (allowActions) """- IMPORTANT: You CANNOT directly add, create, or modify anything. You can only PROPOSE actions via the "actions" array.
- When the user asks to add, create, or track something, you MUST include it in the "actions" array. The user will see a confirmation button.
- In "reply", say what you are proposing (e.g. "I can add that for you"), NEVER say you already did it.
- For bills, payments, or due dates: use type "create_reminder" with the due date in "scheduledFor" and bill details in "title" and "details".
- For todos, tasks, or checklists: use type "create_task".
- For appointments or time-based reminders: use type "create_reminder".
- Always include "title", "details", and "scheduledFor" (ISO-8601 like 2026-03-25T14:30) in every action. Leave "scheduledFor" empty only if truly unknown.""" else "- Do not include any actions."}
- For references, copy the exact "itemId" from context. Never invent ids.
- Keep "reply" concise and helpful.
- Stop immediately after the final closing brace.

App context:
$context

User question:
$question
""".trimIndent()

    /**
     * Compact assistant prompt optimized for small on-device models.
     * Fewer rules, simpler schema, includes a concrete example.
     */
    fun assistantChatLocal(question: String, context: String, allowActions: Boolean): String = """
You are Pocket Assistant. Today is ${currentDateTime()}.
Answer using ONLY the context below. Return JSON only.
Schema: ${if (allowActions) LOCAL_ASSISTANT_ACTION_SCHEMA else LOCAL_ASSISTANT_QA_SCHEMA}
$LOCAL_ASSISTANT_EXAMPLE
Rules:
- Use facts from context. Do not invent information.
- Copy itemId exactly from context for references.
${if (allowActions) """- To suggest creating something, add it to "actions" with type ("create_task" or "create_reminder"), title, details, scheduledFor (ISO-8601 or empty).
- In "reply", say what you propose. Never say you already did it.""" else "- Do not include actions."}
- JSON only. Stop after closing brace.

Context:
$context

Question: $question
""".trimIndent()

    /**
     * Retry variant for local assistant — even shorter, recency-biased.
     * Places the format reminder at the very end so the model sees it last.
     */
    fun assistantChatLocalRetry(question: String, context: String, allowActions: Boolean): String = """
Answer the question using only the context provided. Be specific with names, amounts, dates.

Context:
$context

Question: $question

Reply as JSON: {"reply":"your answer","actions":[],"references":[]}
""".trimIndent()
}
