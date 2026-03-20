package com.charles.pocketassistant.data.repository

import androidx.room.withTransaction
import com.charles.pocketassistant.data.db.PocketAssistantDatabase
import com.charles.pocketassistant.data.db.dao.AiResultDao
import com.charles.pocketassistant.data.db.dao.ItemDao
import com.charles.pocketassistant.data.db.dao.ReminderDao
import com.charles.pocketassistant.data.db.dao.TaskDao
import com.charles.pocketassistant.data.db.entity.AiResultEntity
import com.charles.pocketassistant.data.db.entity.ItemEntity
import com.charles.pocketassistant.data.db.entity.ReminderEntity
import com.charles.pocketassistant.data.db.entity.TaskEntity
import com.charles.pocketassistant.domain.model.AiExtractionResult
import com.charles.pocketassistant.domain.model.AppointmentInfo
import com.charles.pocketassistant.domain.model.BillInfo
import com.charles.pocketassistant.domain.model.Entities
import com.charles.pocketassistant.domain.model.ExtractedTask
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class DataMaintenanceRepository @Inject constructor(
    private val database: PocketAssistantDatabase,
    private val itemDao: ItemDao,
    private val aiResultDao: AiResultDao,
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao
) {
    private val json = Json

    suspend fun addDemoData(): String = withContext(Dispatchers.IO) {
        if (itemDao.countBySourceApp(DEMO_SOURCE_APP) > 0) {
            return@withContext "Demo items are already in your library."
        }
        database.withTransaction {
            demoEntries().forEach { entry ->
                itemDao.upsert(entry.item)
                aiResultDao.upsert(entry.result)
                entry.tasks.forEach { task ->
                    taskDao.upsert(task)
                }
                entry.reminders.forEach { reminder ->
                    reminderDao.upsert(reminder)
                }
            }
        }
        "Added 3 demo items with sample tasks and reminders."
    }

    suspend fun clearAllLocalData(): String = withContext(Dispatchers.IO) {
        database.clearAllTables()
        "Cleared all local items, AI results, tasks, and reminders."
    }

    private fun demoEntries(): List<DemoEntry> {
        val now = System.currentTimeMillis()

        val billItemId = UUID.randomUUID().toString()
        val billCreatedAt = now - 3_600_000L
        val billResult = AiExtractionResult(
            classification = "bill",
            summary = "City Power bill for $82.40 due April 4.",
            entities = Entities(
                organizations = listOf("City Power"),
                amounts = listOf("$82.40"),
                dates = listOf("April 4")
            ),
            tasks = listOf(
                ExtractedTask(
                    title = "Pay City Power bill",
                    details = "Submit payment before the due date.",
                    dueDate = "2026-04-04"
                )
            ),
            billInfo = BillInfo(
                vendor = "City Power",
                amount = "$82.40",
                dueDate = "2026-04-04"
            )
        )

        val messageItemId = UUID.randomUUID().toString()
        val messageCreatedAt = now - 2_400_000L
        val messageResult = AiExtractionResult(
            classification = "message",
            summary = "Work message asking for a draft by Friday at 3 PM and a review to be scheduled.",
            entities = Entities(
                dates = listOf("Friday"),
                times = listOf("3 PM")
            ),
            tasks = listOf(
                ExtractedTask(
                    title = "Send project draft",
                    details = "Share the latest draft with the team.",
                    dueDate = "Friday 3 PM"
                ),
                ExtractedTask(
                    title = "Schedule review",
                    details = "Set up a follow-up review after sending the draft.",
                    dueDate = ""
                )
            )
        )

        val appointmentItemId = UUID.randomUUID().toString()
        val appointmentCreatedAt = now - 1_200_000L
        val appointmentResult = AiExtractionResult(
            classification = "appointment",
            summary = "Dentist appointment on May 9 at 10:30 AM at 123 Main St.",
            entities = Entities(
                dates = listOf("May 9"),
                times = listOf("10:30 AM"),
                locations = listOf("123 Main St")
            ),
            tasks = listOf(
                ExtractedTask(
                    title = "Prepare for dentist visit",
                    details = "Confirm transportation and arrive a few minutes early.",
                    dueDate = "2026-05-09 10:30"
                )
            ),
            appointmentInfo = AppointmentInfo(
                title = "Dentist checkup",
                date = "2026-05-09",
                time = "10:30 AM",
                location = "123 Main St"
            )
        )

        return listOf(
            DemoEntry(
                item = ItemEntity(
                    id = billItemId,
                    type = "text",
                    sourceApp = DEMO_SOURCE_APP,
                    localUri = null,
                    thumbnailUri = null,
                    rawText = "Electric bill from City Power. Amount due $82.40 by Apr 4.",
                    createdAt = billCreatedAt,
                    classification = billResult.classification
                ),
                result = AiResultEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = billItemId,
                    modeUsed = "demo",
                    summary = billResult.summary,
                    extractedJson = json.encodeToString(AiExtractionResult.serializer(), billResult),
                    modelName = "Demo sample",
                    createdAt = billCreatedAt
                ),
                tasks = listOf(
                    TaskEntity(
                        id = UUID.randomUUID().toString(),
                        itemId = billItemId,
                        title = "Pay City Power bill",
                        details = "Submit payment before April 4.",
                        dueAt = 1_775_264_000_000L,
                        isDone = false,
                        createdAt = billCreatedAt
                    )
                ),
                reminders = emptyList()
            ),
            DemoEntry(
                item = ItemEntity(
                    id = messageItemId,
                    type = "text",
                    sourceApp = DEMO_SOURCE_APP,
                    localUri = null,
                    thumbnailUri = null,
                    rawText = "Team message: send draft by Friday 3pm and schedule review.",
                    createdAt = messageCreatedAt,
                    classification = messageResult.classification
                ),
                result = AiResultEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = messageItemId,
                    modeUsed = "demo",
                    summary = messageResult.summary,
                    extractedJson = json.encodeToString(AiExtractionResult.serializer(), messageResult),
                    modelName = "Demo sample",
                    createdAt = messageCreatedAt
                ),
                tasks = listOf(
                    TaskEntity(
                        id = UUID.randomUUID().toString(),
                        itemId = messageItemId,
                        title = "Send project draft",
                        details = "Share it with the team by Friday at 3 PM.",
                        dueAt = null,
                        isDone = false,
                        createdAt = messageCreatedAt
                    ),
                    TaskEntity(
                        id = UUID.randomUUID().toString(),
                        itemId = messageItemId,
                        title = "Schedule project review",
                        details = "Book follow-up time with the team.",
                        dueAt = null,
                        isDone = false,
                        createdAt = messageCreatedAt
                    )
                ),
                reminders = emptyList()
            ),
            DemoEntry(
                item = ItemEntity(
                    id = appointmentItemId,
                    type = "text",
                    sourceApp = DEMO_SOURCE_APP,
                    localUri = null,
                    thumbnailUri = null,
                    rawText = "Appointment: Dentist checkup on May 9 at 10:30 AM, 123 Main St.",
                    createdAt = appointmentCreatedAt,
                    classification = appointmentResult.classification
                ),
                result = AiResultEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = appointmentItemId,
                    modeUsed = "demo",
                    summary = appointmentResult.summary,
                    extractedJson = json.encodeToString(AiExtractionResult.serializer(), appointmentResult),
                    modelName = "Demo sample",
                    createdAt = appointmentCreatedAt
                ),
                tasks = listOf(
                    TaskEntity(
                        id = UUID.randomUUID().toString(),
                        itemId = appointmentItemId,
                        title = "Dentist checkup",
                        details = "Appointment at 123 Main St.",
                        dueAt = 1_778_321_400_000L,
                        isDone = false,
                        createdAt = appointmentCreatedAt
                    )
                ),
                reminders = listOf(
                    ReminderEntity(
                        id = UUID.randomUUID().toString(),
                        itemId = appointmentItemId,
                        title = "Dentist checkup reminder",
                        remindAt = 1_778_317_800_000L,
                        createdAt = appointmentCreatedAt
                    )
                )
            )
        )
    }

    private data class DemoEntry(
        val item: ItemEntity,
        val result: AiResultEntity,
        val tasks: List<TaskEntity>,
        val reminders: List<ReminderEntity>
    )

    companion object {
        private const val DEMO_SOURCE_APP = "demo"
    }
}
