package com.charles.pocketassistant.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class AiExtractionResult(
    val classification: String = "unknown",
    val summary: String = "",
    val entities: Entities = Entities(),
    val tasks: List<ExtractedTask> = emptyList(),
    val billInfo: BillInfo = BillInfo(),
    val appointmentInfo: AppointmentInfo = AppointmentInfo()
)

@Serializable
data class Entities(
    val people: List<String> = emptyList(),
    val organizations: List<String> = emptyList(),
    val amounts: List<String> = emptyList(),
    val dates: List<String> = emptyList(),
    val times: List<String> = emptyList(),
    val locations: List<String> = emptyList()
)

@Serializable
data class ExtractedTask(
    val title: String = "",
    val details: String = "",
    val dueDate: String = ""
)

@Serializable
data class BillInfo(
    val vendor: String = "",
    val amount: String = "",
    val dueDate: String = ""
)

@Serializable
data class AppointmentInfo(
    val title: String = "",
    val date: String = "",
    val time: String = "",
    val location: String = ""
)

@Serializable
data class AssistantChatResult(
    val reply: String = "",
    val actions: List<AssistantActionSuggestion> = emptyList(),
    val references: List<AssistantItemReference> = emptyList()
)

@Serializable
data class AssistantActionSuggestion(
    val type: String = "",
    val title: String = "",
    val details: String = "",
    val scheduledFor: String = "",
    val confirmationLabel: String = "",
    val fallbackNote: String = ""
)

@Serializable
data class AssistantItemReference(
    val itemId: String = "",
    val label: String = ""
)

@Serializable
data class StoredAssistantAction(
    val type: String = "",
    val title: String = "",
    val details: String = "",
    val scheduledForIso: String = "",
    val confirmationLabel: String = "",
    val fallbackNote: String = "",
    val status: String = "",
    val feedback: String = ""
)
