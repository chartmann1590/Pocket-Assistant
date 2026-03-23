package com.charles.pocketassistant.data.repository

import com.charles.pocketassistant.ai.local.LocalLlmEngine
import com.charles.pocketassistant.ai.local.LocalModelManager
import com.charles.pocketassistant.ai.parser.AiJsonParser
import com.charles.pocketassistant.ai.routing.AiRouter
import com.charles.pocketassistant.data.datastore.AiMode
import com.charles.pocketassistant.data.datastore.isOllamaConfigured
import com.charles.pocketassistant.data.datastore.SettingsStore
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
import com.charles.pocketassistant.domain.model.AiExtractionResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Singleton
class ItemRepository @Inject constructor(
    private val itemDao: ItemDao,
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao
) {
    fun observeItems(): Flow<List<ItemEntity>> = itemDao.observeAll()
    fun observeById(id: String) = itemDao.observeById(id)
    fun observeByClassification(classification: String) = itemDao.observeByClassification(classification)
    fun search(query: String): Flow<List<ItemEntity>> = itemDao.search(query)
    suspend fun getRecent(limit: Int = 25) = itemDao.getRecent(limit)
    suspend fun insert(item: ItemEntity) = itemDao.upsert(item)
    suspend fun updateClassification(id: String, classification: String) =
        itemDao.updateClassification(id, classification)

    suspend fun delete(id: String) {
        taskDao.deleteByItemId(id)
        reminderDao.deleteByItemId(id)
        itemDao.delete(id) // ai_results cascade-deleted via ForeignKey
    }
}

@Singleton
class TaskRepository @Inject constructor(
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao
) {
    fun observeToday(start: Long, end: Long) = taskDao.observeToday(start, end)
    fun observeUpcoming(from: Long) = taskDao.observeUpcoming(from)
    fun observeOpen() = taskDao.observeOpen()
    fun observeDone() = taskDao.observeDone()
    suspend fun getOpen(limit: Int = 20) = taskDao.getOpen(limit)
    suspend fun getUpcomingReminders(from: Long = System.currentTimeMillis(), limit: Int = 10) =
        reminderDao.getUpcoming(from, limit)
    suspend fun addTask(task: TaskEntity) = taskDao.upsert(task)
    suspend fun setDone(taskId: String, isDone: Boolean) = taskDao.setDone(taskId, isDone)
    suspend fun addReminder(reminder: ReminderEntity) = reminderDao.upsert(reminder)
}

@Singleton
class SettingsRepository @Inject constructor(private val store: SettingsStore) {
    val settings = store.settings
    suspend fun update(block: (com.charles.pocketassistant.data.datastore.UserSettings) -> com.charles.pocketassistant.data.datastore.UserSettings) =
        store.update(block)
}

@Singleton
class ModelRepository @Inject constructor(private val localModelManager: LocalModelManager) {
    fun isInstalled() = localModelManager.isModelInstalled()
    fun installedSizeMb() = localModelManager.installedSizeMb()
    suspend fun deleteModel() = localModelManager.clearInstalled()
}

@Singleton
class ChatRepository @Inject constructor(
    private val chatThreadDao: ChatThreadDao,
    private val chatMessageDao: ChatMessageDao
) {
    fun observeThreads(): Flow<List<ChatThreadEntity>> = chatThreadDao.observeAll()
    fun observeMessages(threadId: String): Flow<List<ChatMessageEntity>> = chatMessageDao.observeForThread(threadId)

    suspend fun pruneEmptyThreads() = chatThreadDao.deleteEmptyThreads()

    suspend fun getLatestOrCreate(): ChatThreadEntity = chatThreadDao.getLatest() ?: createThread()

    suspend fun createThread(title: String = "New chat"): ChatThreadEntity {
        val now = System.currentTimeMillis()
        val thread = ChatThreadEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now
        )
        chatThreadDao.upsert(thread)
        return thread
    }

    suspend fun saveMessage(
        threadId: String,
        role: String,
        text: String,
        createdAt: Long,
        actionsJson: String = "[]",
        referencesJson: String = "[]"
    ) {
        chatMessageDao.upsert(
            ChatMessageEntity(
                id = UUID.randomUUID().toString(),
                threadId = threadId,
                role = role,
                text = text,
                createdAt = createdAt,
                actionsJson = actionsJson,
                referencesJson = referencesJson
            )
        )
        val existingThread = chatThreadDao.getById(threadId) ?: return
        val updatedTitle = if (role == "user" && existingThread.title == "New chat") {
            text.trim().take(40).ifBlank { existingThread.title }
        } else {
            existingThread.title
        }
        chatThreadDao.updateMetadata(
            threadId = threadId,
            title = updatedTitle,
            updatedAt = createdAt
        )
    }

    suspend fun getRecentMessages(threadId: String, limit: Int = 12): List<ChatMessageEntity> =
        chatMessageDao.getRecentForThread(threadId, limit).reversed()

    suspend fun updateMessagePayload(messageId: String, actionsJson: String, referencesJson: String) {
        val existing = chatMessageDao.getById(messageId) ?: return
        chatMessageDao.upsert(
            existing.copy(
                actionsJson = actionsJson,
                referencesJson = referencesJson
            )
        )
    }
}

@Singleton
class AiRepository @Inject constructor(
    private val settingsStore: SettingsStore,
    private val localLlmEngine: LocalLlmEngine,
    private val ollamaRepositoryImpl: OllamaRepositoryImpl,
    private val aiResultDao: AiResultDao,
    private val parser: AiJsonParser,
    private val entityExtractionEngine: com.charles.pocketassistant.ml.EntityExtractionEngine,
    private val languageDetector: com.charles.pocketassistant.ml.LanguageDetector,
    private val textClassifier: com.charles.pocketassistant.ml.TextClassifier,
    private val translationEngine: com.charles.pocketassistant.ml.TranslationEngine,
    private val geminiNanoEngine: com.charles.pocketassistant.ai.nano.GeminiNanoEngine
) {
    private val router = AiRouter()

    suspend fun run(itemId: String, text: String, sourceType: String = "text"): AiExtractionResult {
        val settings = settingsStore.settings.first()
        val localAvailable = localLlmEngine.isAvailable()
        val ollamaConfigured = settings.isOllamaConfigured()
        val decision = router.decide(
            selectedMode = settings.aiMode,
            localAvailable = localAvailable,
            ollamaConfigured = ollamaConfigured
        )
        if (settings.aiMode == AiMode.AUTO && decision.mode == AiMode.OLLAMA && localAvailable) {
            val ollamaResult = ollamaRepositoryImpl.summarizeAndExtract(text)
            return ollamaResult.fold(
                onSuccess = { result ->
                    persistResult(
                        itemId = itemId,
                        modeUsed = "ollama",
                        modelName = settings.ollamaModelName,
                        result = result
                    )
                    result
                },
                onFailure = {
                    val localResult = localLlmEngine.summarizeAndExtract(text)
                    persistResult(
                        itemId = itemId,
                        modeUsed = "local",
                        modelName = settings.localModelVersion,
                        result = localResult
                    )
                    localResult
                }
            )
        }
        return runWithMode(
            itemId = itemId,
            text = text,
            mode = decision.mode,
            sourceType = sourceType
        )
    }

    suspend fun runWithMode(
        itemId: String,
        text: String,
        mode: AiMode,
        sourceType: String = "text"
    ): AiExtractionResult {
        val settings = settingsStore.settings.first()

        // Step 1: Detect language (~10ms) and translate if non-English
        val detectedLang = languageDetector.identifyLanguage(text)
        val processText = if (!languageDetector.isEnglish(detectedLang) && detectedLang != "und") {
            translationEngine.translate(text, detectedLang, "en")
        } else {
            text
        }

        // Step 2: Fast classification (~1ms) — instant result before LLM
        val fastClass = textClassifier.classify(processText)

        // Step 3: ML Kit entity extraction in parallel (instant, ~50ms)
        val mlEntities = entityExtractionEngine.extract(processText)

        // Step 4: LLM inference — try Gemini Nano first for LOCAL mode (highest quality on-device)
        val llmResult = when (mode) {
            AiMode.LOCAL -> {
                val nanoResult = tryGeminiNano(processText)
                nanoResult ?: localLlmEngine.summarizeAndExtract(processText)
            }
            AiMode.OLLAMA -> ollamaRepositoryImpl.summarizeAndExtract(processText)
                .getOrElse { parser.parseOrFallback(it.message ?: "Ollama request failed.") }
            AiMode.AUTO -> run(itemId, text, sourceType)
        }

        // Step 5: Use fast classification as fallback if LLM returned "unknown"
        val usedNano = mode == AiMode.LOCAL && geminiNanoEngine.available.value == true
        var merged = llmResult
        if (merged.classification == "unknown" && fastClass.confidence > 0.3f) {
            merged = merged.copy(classification = fastClass.classification)
        }

        // Step 6: Backfill entities the LLM missed with ML Kit's reliable extraction
        val result = entityExtractionEngine.backfillEntities(merged, mlEntities)
        persistResult(
            itemId = itemId,
            modeUsed = if (usedNano) "gemini_nano" else mode.name.lowercase(),
            modelName = if (usedNano) "gemini-nano" else if (mode == AiMode.OLLAMA) settings.ollamaModelName else settings.localModelVersion,
            result = result
        )
        return result
    }

    suspend fun saveEditedResult(
        itemId: String,
        rawOutput: String,
        fallbackSummary: String,
        modelName: String
    ): AiExtractionResult {
        val parsed = parser.parseOrFallback(rawOutput).let {
            if (fallbackSummary.isBlank()) it else it.copy(summary = fallbackSummary)
        }
        persistResult(
            itemId = itemId,
            modeUsed = "edited",
            modelName = modelName,
            result = parsed
        )
        return parsed
    }

    fun observeLatest(itemId: String) = aiResultDao.observeLatestForItem(itemId)
    suspend fun getRecentResults(limit: Int = 10) = aiResultDao.getRecent(limit)

    /**
     * Try Gemini Nano for summarization. Returns null if unavailable or failed.
     * Gemini Nano (Pixel 8+) is higher quality than the bundled LiteRT models.
     */
    private suspend fun tryGeminiNano(text: String): AiExtractionResult? {
        if (!geminiNanoEngine.isAvailable()) return null
        return try {
            val nanoSummary = geminiNanoEngine.summarize(text) ?: return null
            parser.parseOrFallback(nanoSummary)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun persistResult(
        itemId: String,
        modeUsed: String,
        modelName: String,
        result: AiExtractionResult
    ) {
        aiResultDao.upsert(
            AiResultEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                modeUsed = modeUsed,
                summary = result.summary,
                extractedJson = kotlinx.serialization.json.Json.encodeToString(AiExtractionResult.serializer(), result),
                modelName = modelName,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}
