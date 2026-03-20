package com.charles.pocketassistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val type: String,
    val sourceApp: String?,
    val localUri: String?,
    val thumbnailUri: String?,
    val rawText: String,
    val createdAt: Long,
    val classification: String?
)

@Entity(
    tableName = "ai_results",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("itemId")]
)
data class AiResultEntity(
    @PrimaryKey val id: String,
    val itemId: String,
    val modeUsed: String,
    val summary: String,
    val extractedJson: String,
    val modelName: String,
    val createdAt: Long
)

@Entity(
    tableName = "tasks",
    indices = [Index("itemId")]
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val itemId: String?,
    val title: String,
    val details: String?,
    val dueAt: Long?,
    val isDone: Boolean,
    val createdAt: Long
)

@Entity(
    tableName = "reminders",
    indices = [Index("itemId")]
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val itemId: String?,
    val title: String,
    val remindAt: Long,
    val createdAt: Long
)

@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatThreadEntity::class,
            parentColumns = ["id"],
            childColumns = ["threadId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("threadId")]
)
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val role: String,
    val text: String,
    val createdAt: Long,
    val actionsJson: String,
    val referencesJson: String
)
