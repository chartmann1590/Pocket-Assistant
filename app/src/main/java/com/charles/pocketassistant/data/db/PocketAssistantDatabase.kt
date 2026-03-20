package com.charles.pocketassistant.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.charles.pocketassistant.data.db.dao.AiResultDao
import com.charles.pocketassistant.data.db.dao.ChatMessageDao
import com.charles.pocketassistant.data.db.dao.ChatThreadDao
import com.charles.pocketassistant.data.db.dao.ItemDao
import com.charles.pocketassistant.data.db.dao.ReminderDao
import com.charles.pocketassistant.data.db.dao.TaskDao
import com.charles.pocketassistant.data.db.entity.AiResultEntity
import com.charles.pocketassistant.data.db.entity.ChatMessageEntity
import com.charles.pocketassistant.data.db.entity.ChatThreadEntity
import com.charles.pocketassistant.data.db.entity.ItemEntity
import com.charles.pocketassistant.data.db.entity.ReminderEntity
import com.charles.pocketassistant.data.db.entity.TaskEntity

@Database(
    entities = [
        ItemEntity::class,
        AiResultEntity::class,
        TaskEntity::class,
        ReminderEntity::class,
        ChatThreadEntity::class,
        ChatMessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class PocketAssistantDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun aiResultDao(): AiResultDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun chatThreadDao(): ChatThreadDao
    abstract fun chatMessageDao(): ChatMessageDao
}
