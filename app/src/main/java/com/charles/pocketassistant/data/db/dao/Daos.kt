package com.charles.pocketassistant.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.charles.pocketassistant.data.db.entity.AiResultEntity
import com.charles.pocketassistant.data.db.entity.ChatMessageEntity
import com.charles.pocketassistant.data.db.entity.ChatThreadEntity
import com.charles.pocketassistant.data.db.entity.ItemEntity
import com.charles.pocketassistant.data.db.entity.ReminderEntity
import com.charles.pocketassistant.data.db.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ItemEntity)

    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ItemEntity?>

    @Query("SELECT * FROM items WHERE classification = :classification ORDER BY createdAt DESC")
    fun observeByClassification(classification: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM items WHERE sourceApp = :sourceApp")
    suspend fun countBySourceApp(sourceApp: String): Int

    @Query("UPDATE items SET classification = :classification WHERE id = :id")
    suspend fun updateClassification(id: String, classification: String)

    @Query("SELECT * FROM items WHERE rawText LIKE '%' || :query || '%' OR classification LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun search(query: String): Flow<List<ItemEntity>>
}

@Dao
interface AiResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(result: AiResultEntity)

    @Query("SELECT * FROM ai_results WHERE itemId = :itemId ORDER BY createdAt DESC LIMIT 1")
    fun observeLatestForItem(itemId: String): Flow<AiResultEntity?>

    @Query("SELECT * FROM ai_results ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<AiResultEntity>
}

@Dao
interface TaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND (dueAt IS NULL OR dueAt >= :start AND dueAt < :end) ORDER BY dueAt ASC, createdAt DESC")
    fun observeToday(start: Long, end: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDone = 0 AND dueAt >= :from ORDER BY dueAt ASC, createdAt DESC")
    fun observeUpcoming(from: Long): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY dueAt ASC, createdAt DESC")
    fun observeOpen(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDone = 1 ORDER BY createdAt DESC")
    fun observeDone(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE isDone = 0 ORDER BY COALESCE(dueAt, createdAt) ASC LIMIT :limit")
    suspend fun getOpen(limit: Int): List<TaskEntity>

    @Query("UPDATE tasks SET isDone = :isDone WHERE id = :taskId")
    suspend fun setDone(taskId: String, isDone: Boolean)
}

@Dao
interface ReminderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: ReminderEntity)

    @Query("SELECT * FROM reminders ORDER BY remindAt ASC")
    fun observeAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE remindAt >= :from ORDER BY remindAt ASC LIMIT :limit")
    suspend fun getUpcoming(from: Long, limit: Int): List<ReminderEntity>
}

@Dao
interface ChatThreadDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(thread: ChatThreadEntity)

    @Query("UPDATE chat_threads SET title = :title, updatedAt = :updatedAt WHERE id = :threadId")
    suspend fun updateMetadata(threadId: String, title: String, updatedAt: Long)

    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ChatThreadEntity>>

    @Query("SELECT * FROM chat_threads ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatest(): ChatThreadEntity?

    @Query("SELECT * FROM chat_threads WHERE id = :threadId LIMIT 1")
    suspend fun getById(threadId: String): ChatThreadEntity?

    @Query("DELETE FROM chat_threads WHERE id NOT IN (SELECT DISTINCT threadId FROM chat_messages)")
    suspend fun deleteEmptyThreads()
}

@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(message: ChatMessageEntity)

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeForThread(threadId: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE threadId = :threadId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentForThread(threadId: String, limit: Int): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages WHERE id = :messageId LIMIT 1")
    suspend fun getById(messageId: String): ChatMessageEntity?
}
