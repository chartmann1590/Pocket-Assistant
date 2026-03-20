package com.charles.pocketassistant.ai.prompt

object PromptFactory {
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

    fun general(text: String): String = """
You are an extraction engine. Return strict JSON only with no extra text.
Use this exact schema:
$SCHEMA
Classify input and extract concise useful actions.
Input:
$text
""".trimIndent()

    fun bill(text: String): String = general("Focus on bill fields.\n$text")
    fun message(text: String): String = general("Focus on action items and follow-up.\n$text")
    fun appointment(text: String): String = general("Focus on appointment title/date/time/location.\n$text")

    fun assistantChat(question: String, context: String, allowActions: Boolean): String = """
You are Pocket Assistant, a privacy-first Android organizer assistant.
Return strict JSON only with no markdown and no extra text.
Use this exact schema:
${if (allowActions) ASSISTANT_SCHEMA else ASSISTANT_QA_SCHEMA}

Rules:
- Use the provided app context first.
- If the answer is not fully available in context, say what is missing instead of inventing details.
- ${if (allowActions) "Only include actions when the user is clearly asking you to create, add, schedule, or remind." else "Do not include any actions."}
- Use type "create_reminder" for appointments or reminders.
- Use type "create_task" for todos or checklist items.
- Only include references when the answer depends on a specific stored item from the provided context.
- For references, copy the exact "itemId" from the context. Never invent ids.
- For reminder times, use ISO-8601 local date/time in "scheduledFor" like 2026-03-25T14:30.
- If a date or time is missing, leave "scheduledFor" empty and explain the missing info in "reply".
- Keep "reply" concise and practical.
- Keep "reply" under 60 words.
- Answer simple lookup questions directly instead of restating the full context.
- Stop immediately after the final closing brace.

App context:
$context

User question:
$question
""".trimIndent()
}
