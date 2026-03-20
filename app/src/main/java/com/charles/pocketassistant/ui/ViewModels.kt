package com.charles.pocketassistant.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.pocketassistant.ai.local.LocalLlmEngine
import com.charles.pocketassistant.ai.local.LocalModelDownloadManager
import com.charles.pocketassistant.ai.local.LocalModelManager
import com.charles.pocketassistant.ai.local.ModelConfig
import com.charles.pocketassistant.ai.local.LocalModelProfile
import com.charles.pocketassistant.ai.routing.AiRouter
import com.charles.pocketassistant.data.datastore.AiMode
import com.charles.pocketassistant.data.db.entity.ChatMessageEntity
import com.charles.pocketassistant.data.db.entity.ItemEntity
import com.charles.pocketassistant.data.db.entity.ReminderEntity
import com.charles.pocketassistant.data.db.entity.TaskEntity
import com.charles.pocketassistant.data.repository.AiRepository
import com.charles.pocketassistant.data.repository.ChatRepository
import com.charles.pocketassistant.data.repository.DataMaintenanceRepository
import com.charles.pocketassistant.data.repository.ItemRepository
import com.charles.pocketassistant.data.repository.ModelRepository
import com.charles.pocketassistant.data.repository.OllamaRepositoryImpl
import com.charles.pocketassistant.data.repository.SettingsRepository
import com.charles.pocketassistant.data.repository.TaskRepository
import com.charles.pocketassistant.domain.model.AssistantChatResult
import com.charles.pocketassistant.domain.model.AssistantItemReference
import com.charles.pocketassistant.domain.model.StoredAssistantAction
import com.charles.pocketassistant.ocr.OcrEngine
import com.charles.pocketassistant.util.AssistantDateTimeParser
import com.charles.pocketassistant.util.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

enum class OnboardingModeSelection {
    LOCAL,
    OLLAMA,
    BOTH
}

data class OnboardingUiState(
    val selectedMode: OnboardingModeSelection = OnboardingModeSelection.LOCAL,
    val showLocalSetup: Boolean = true,
    val showOllamaSetup: Boolean = false,
    val availableModels: List<LocalModelProfile> = ModelConfig.profiles,
    val selectedLocalModelId: String = ModelConfig.defaultProfile.id,
    val modelName: String = ModelConfig.defaultProfile.displayName,
    val modelSizeMb: Int = ModelConfig.defaultProfile.modelSizeMb,
    val modelDescription: String = ModelConfig.defaultProfile.shortDescription,
    val modelRequiresToken: Boolean = ModelConfig.defaultProfile.requiresAuthToken,
    val modelRepoUrl: String = ModelConfig.defaultProfile.repoUrl,
    val localModelInstalled: Boolean = false,
    val localModelPath: String = "",
    val modelDownloadAuthToken: String = "",
    val downloadInProgress: Boolean = false,
    val downloadProgress: Int = 0,
    val downloadStatusMessage: String = ModelConfig.installSummary(ModelConfig.defaultProfile),
    val ollamaBaseUrl: String = "",
    val ollamaApiToken: String = "",
    val ollamaModelName: String = "",
    val ollamaTestMessage: String = "",
    val dataToolsMessage: String = "",
    val canFinish: Boolean = false
)

@HiltViewModel
class AppStateViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val localModelManager: LocalModelManager
) : ViewModel() {
    val onboardingComplete = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.charles.pocketassistant.data.datastore.UserSettings())

    init {
        viewModelScope.launch {
            localModelManager.reconcileInstallState()
        }
    }
}

data class ProcessingUiState(
    val running: Boolean = false,
    val ocrProgress: Int = 0,
    val aiProgress: Int = 0,
    val modeUsed: String = "local",
    val message: String = "",
    val currentItemId: String? = null
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    app: Application,
    private val settingsRepository: SettingsRepository,
    private val localModelManager: LocalModelManager,
    private val localModelDownloadManager: LocalModelDownloadManager,
    private val ollamaRepositoryImpl: OllamaRepositoryImpl,
    private val dataMaintenanceRepository: DataMaintenanceRepository
) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    init {
        observeSettings()
        observeDownloadState()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            settingsRepository.settings.collect { s ->
                val localInstalled = s.localModelInstalled && localModelManager.isModelInstalled()
                val ollamaConfigured = s.ollamaBaseUrl.isNotBlank() && s.ollamaModelName.isNotBlank()
                val selectedProfile = ModelConfig.profileFor(s.selectedLocalModelId)
                val selectedMode = when (s.aiMode) {
                    AiMode.LOCAL -> OnboardingModeSelection.LOCAL
                    AiMode.OLLAMA -> OnboardingModeSelection.OLLAMA
                    AiMode.AUTO -> OnboardingModeSelection.BOTH
                }
                _state.value = _state.value.copy(
                    selectedMode = selectedMode,
                    showLocalSetup = selectedMode != OnboardingModeSelection.OLLAMA,
                    showOllamaSetup = selectedMode != OnboardingModeSelection.LOCAL,
                    selectedLocalModelId = selectedProfile.id,
                    modelName = selectedProfile.displayName,
                    modelSizeMb = selectedProfile.modelSizeMb,
                    modelDescription = selectedProfile.shortDescription,
                    modelRequiresToken = selectedProfile.requiresAuthToken,
                    modelRepoUrl = selectedProfile.repoUrl,
                    localModelInstalled = localInstalled,
                    localModelPath = if (localInstalled) s.localModelPath else "",
                    modelDownloadAuthToken = s.modelDownloadAuthToken,
                    downloadInProgress = s.localModelDownloadInProgress,
                    downloadStatusMessage = if (s.localModelDownloadMessage.isNotBlank()) {
                        s.localModelDownloadMessage
                    } else {
                        _state.value.downloadStatusMessage
                    },
                    ollamaBaseUrl = s.ollamaBaseUrl,
                    ollamaApiToken = s.ollamaApiToken,
                    ollamaModelName = s.ollamaModelName,
                    dataToolsMessage = _state.value.dataToolsMessage,
                    canFinish = when (selectedMode) {
                        OnboardingModeSelection.LOCAL -> localInstalled
                        OnboardingModeSelection.OLLAMA -> ollamaConfigured
                        OnboardingModeSelection.BOTH -> localInstalled || ollamaConfigured
                    }
                )
            }
        }
    }

    fun selectLocalModel(modelId: String) = viewModelScope.launch {
        val current = state.value.selectedLocalModelId
        if (current == modelId) return@launch
        localModelDownloadManager.cancelAndDelete()
        settingsRepository.update { it.copy(selectedLocalModelId = modelId) }
        val selectedProfile = ModelConfig.profileFor(modelId)
        _state.value = _state.value.copy(
            selectedLocalModelId = modelId,
            modelName = selectedProfile.displayName,
            modelSizeMb = selectedProfile.modelSizeMb,
            modelDescription = selectedProfile.shortDescription,
            modelRequiresToken = selectedProfile.requiresAuthToken,
            modelRepoUrl = selectedProfile.repoUrl,
            downloadProgress = 0,
            downloadStatusMessage = ModelConfig.installSummary(selectedProfile)
        )
    }

    private fun observeDownloadState() {
        viewModelScope.launch {
            localModelDownloadManager.state.collect { download ->
                _state.value = _state.value.copy(
                    downloadInProgress = download.inProgress,
                    downloadProgress = download.progress,
                    downloadStatusMessage = if (download.message.isNotBlank()) download.message else _state.value.downloadStatusMessage
                )
            }
        }
    }

    fun selectLocalOnly() { _state.value = _state.value.copy(showLocalSetup = true, showOllamaSetup = false) }
    fun selectOllamaOnly() {
        _state.value = _state.value.copy(
            selectedMode = OnboardingModeSelection.OLLAMA,
            showLocalSetup = false,
            showOllamaSetup = true
        )
        viewModelScope.launch { settingsRepository.update { it.copy(aiMode = AiMode.OLLAMA) } }
    }
    fun selectBoth() {
        _state.value = _state.value.copy(
            selectedMode = OnboardingModeSelection.BOTH,
            showLocalSetup = true,
            showOllamaSetup = true
        )
        viewModelScope.launch { settingsRepository.update { it.copy(aiMode = AiMode.AUTO) } }
    }
    fun selectLocalOnlyPersist() = viewModelScope.launch {
        _state.value = _state.value.copy(
            selectedMode = OnboardingModeSelection.LOCAL,
            showLocalSetup = true,
            showOllamaSetup = false
        )
        settingsRepository.update { it.copy(aiMode = AiMode.LOCAL) }
    }

    fun updateBaseUrl(v: String) = viewModelScope.launch { settingsRepository.update { it.copy(ollamaBaseUrl = v) } }
    fun updateOllamaApiToken(v: String) = viewModelScope.launch { settingsRepository.update { it.copy(ollamaApiToken = v.trim()) } }
    fun updateModelName(v: String) = viewModelScope.launch { settingsRepository.update { it.copy(ollamaModelName = v) } }
    fun updateModelDownloadAuthToken(v: String) = viewModelScope.launch {
        settingsRepository.update { it.copy(modelDownloadAuthToken = v.trim()) }
    }

    fun testOllama() = viewModelScope.launch {
        val result = ollamaRepositoryImpl.testConnection()
        _state.value = _state.value.copy(
            ollamaTestMessage = result.fold(
                onSuccess = { "Connected. Models: ${it.take(5).joinToString()}" },
                onFailure = { "Connection failed: ${it.message}" }
            )
        )
    }

    fun downloadModel() {
        if (_state.value.downloadInProgress) return
        if (!localModelManager.hasEnoughStorage()) {
            val selectedProfile = ModelConfig.profileFor(_state.value.selectedLocalModelId)
            _state.value = _state.value.copy(
                downloadStatusMessage = "Not enough free space. Keep at least ${ModelConfig.requiredFreeSpaceBytes(selectedProfile) / (1024L * 1024L)} MB free."
            )
            return
        }
        val selectedProfile = ModelConfig.profileFor(_state.value.selectedLocalModelId)
        if (selectedProfile.requiresAuthToken && _state.value.modelDownloadAuthToken.isBlank()) {
            _state.value = _state.value.copy(
                downloadStatusMessage = "Enter a Hugging Face access token that can download ${selectedProfile.displayName} before starting."
            )
            return
        }
        _state.value = _state.value.copy(
            downloadProgress = 0,
            downloadStatusMessage = "Starting ${selectedProfile.displayName} download..."
        )
        localModelDownloadManager.startOrResume()
    }

    fun finish() = viewModelScope.launch { settingsRepository.update { it.copy(onboardingComplete = true) } }

    fun addDemoData() = viewModelScope.launch {
        _state.value = _state.value.copy(dataToolsMessage = dataMaintenanceRepository.addDemoData())
    }

    fun setupLocalLater() = viewModelScope.launch {
        settingsRepository.update {
            if (it.ollamaBaseUrl.isNotBlank() && it.ollamaModelName.isNotBlank()) {
                it.copy(onboardingComplete = true, aiMode = AiMode.OLLAMA)
            } else {
                it
            }
        }
    }
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val aiRepository: AiRepository,
    private val ocrEngine: OcrEngine
) : ViewModel() {
    val items = itemRepository.observeItems().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _processing = MutableStateFlow(ProcessingUiState())
    val processing: StateFlow<ProcessingUiState> = _processing
    private val _completedItemId = MutableStateFlow<String?>(null)
    val completedItemId: StateFlow<String?> = _completedItemId
    private var currentImportJob: Job? = null

    fun importText(text: String, sourceApp: String? = null, type: String = "text") {
        startImport {
            processTextImport(text = text, sourceApp = sourceApp, type = type, navigateOnComplete = true)
        }
    }

    fun importUri(uri: Uri, type: String, sourceApp: String? = null) {
        startImport {
            processUriImport(uri = uri, type = type, sourceApp = sourceApp, navigateOnComplete = true)
        }
    }

    fun cancelProcessing() {
        currentImportJob?.cancel()
        currentImportJob = null
        _processing.value = _processing.value.copy(
            running = false,
            message = "Processing cancelled."
        )
    }

    fun consumeCompletedItemNavigation() {
        _completedItemId.value = null
    }

    private fun startImport(block: suspend () -> Unit) {
        currentImportJob?.cancel()
        currentImportJob = viewModelScope.launch {
            runCatching { block() }
                .onFailure {
                    _processing.value = _processing.value.copy(
                        running = false,
                        message = if (it is java.util.concurrent.CancellationException) {
                            "Processing cancelled."
                        } else {
                            "Processing failed: ${it.message.orEmpty()}"
                        }
                    )
                }
        }
    }

    private suspend fun processTextImport(
        text: String,
        sourceApp: String? = null,
        type: String = "text",
        localUri: String? = null,
        thumbnailUri: String? = null,
        navigateOnComplete: Boolean
    ) {
        _processing.value = ProcessingUiState(running = true, ocrProgress = 100, aiProgress = 10, modeUsed = "pending")
        val id = UUID.randomUUID().toString()
        itemRepository.insert(
            ItemEntity(
                id = id,
                type = type,
                sourceApp = sourceApp,
                localUri = localUri,
                thumbnailUri = thumbnailUri,
                rawText = text,
                createdAt = System.currentTimeMillis(),
                classification = null
            )
        )
        _processing.value = _processing.value.copy(aiProgress = 65, currentItemId = id)
        val result = aiRepository.run(id, text, sourceType = type)
        itemRepository.updateClassification(id, result.classification)
        _processing.value = _processing.value.copy(
            running = false,
            aiProgress = 100,
            modeUsed = result.classification,
            message = "",
            currentItemId = id
        )
        if (navigateOnComplete) {
            _completedItemId.value = id
        }
    }

    private suspend fun processUriImport(
        uri: Uri,
        type: String,
        sourceApp: String? = null,
        navigateOnComplete: Boolean
    ) {
        _processing.value = ProcessingUiState(running = true, ocrProgress = 15, aiProgress = 0, modeUsed = "pending")
        val raw = ocrEngine.fromUri(uri)
        if (raw.isBlank()) error("No OCR text found.")
        _processing.value = _processing.value.copy(ocrProgress = 100, aiProgress = 10)
        processTextImport(
            text = raw,
            sourceApp = sourceApp,
            type = type,
            localUri = uri.toString(),
            thumbnailUri = uri.toString(),
            navigateOnComplete = navigateOnComplete
        )
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(itemRepository: ItemRepository) : ViewModel() {
    val items = itemRepository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
}

data class AssistantMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis(),
    val actions: List<AssistantActionUi> = emptyList(),
    val references: List<AssistantReferenceUi> = emptyList()
)

enum class AssistantActionStatus {
    PROPOSED,
    APPLYING,
    CONFIRMED,
    DISMISSED,
    FAILED
}

data class AssistantActionUi(
    val id: String = UUID.randomUUID().toString(),
    val type: String,
    val title: String,
    val details: String,
    val scheduledForIso: String,
    val scheduledForMillis: Long?,
    val scheduledForLabel: String,
    val confirmationLabel: String,
    val fallbackNote: String,
    val status: AssistantActionStatus = AssistantActionStatus.PROPOSED,
    val feedback: String = ""
)

data class AssistantReferenceUi(
    val itemId: String,
    val label: String
)

data class AssistantThreadUi(
    val id: String,
    val title: String,
    val updatedAt: Long
)

data class AssistantUiState(
    val currentThreadId: String = "",
    val currentThreadTitle: String = "New chat",
    val threads: List<AssistantThreadUi> = emptyList(),
    val messages: List<AssistantMessage> = emptyList(),
    val input: String = "",
    val running: Boolean = false,
    val runningLabel: String = "",
    val error: String = ""
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val taskRepository: TaskRepository,
    private val aiRepository: AiRepository,
    private val chatRepository: ChatRepository,
    private val settingsRepository: SettingsRepository,
    private val localLlmEngine: LocalLlmEngine,
    private val ollamaRepositoryImpl: OllamaRepositoryImpl,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {
    private val _state = MutableStateFlow(AssistantUiState())
    val state: StateFlow<AssistantUiState> = _state
    private val router = AiRouter()
    private val assistantJson = Json { ignoreUnknownKeys = true }
    private var activeThreadMessagesJob: Job? = null

    init {
        viewModelScope.launch {
            chatRepository.pruneEmptyThreads()
            observeThreads()
            val thread = chatRepository.getLatestOrCreate()
            openThread(thread.id)
        }
    }

    fun updateInput(value: String) {
        _state.value = _state.value.copy(input = value)
    }

    fun startNewThread() = viewModelScope.launch {
        val thread = chatRepository.createThread()
        openThread(thread.id)
    }

    fun openThread(threadId: String) {
        if (threadId.isBlank()) return
        if (_state.value.currentThreadId == threadId && activeThreadMessagesJob != null) return
        activeThreadMessagesJob?.cancel()
        _state.value = _state.value.copy(
            currentThreadId = threadId,
            running = false,
            runningLabel = "",
            error = ""
        )
        activeThreadMessagesJob = viewModelScope.launch {
            chatRepository.observeMessages(threadId).collect { messages ->
                val persisted = messages.map(::mapPersistedMessage)
                val fallbackMessages = stripIntroMessages(_state.value.messages)
                _state.value = _state.value.copy(
                    currentThreadId = threadId,
                    currentThreadTitle = _state.value.threads.firstOrNull { it.id == threadId }?.title ?: "New chat",
                    messages = when {
                        persisted.isNotEmpty() -> persisted
                        fallbackMessages.isNotEmpty() && _state.value.currentThreadId == threadId -> fallbackMessages
                        else -> listOf(introMessage())
                    }
                )
            }
        }
    }

    fun send() {
        val question = _state.value.input.trim()
        if (question.isBlank() || _state.value.running) return
        viewModelScope.launch {
            val threadId = currentThreadId()
            val userCreatedAt = System.currentTimeMillis()
            val optimisticUserMessage = AssistantMessage(
                id = "local-user-$userCreatedAt",
                role = "user",
                text = question,
                createdAt = userCreatedAt
            )
            _state.value = _state.value.copy(
                messages = stripIntroMessages(_state.value.messages) + optimisticUserMessage,
                running = true,
                error = "",
                input = "",
                runningLabel = "Checking your saved data..."
            )
            chatRepository.saveMessage(
                threadId = threadId,
                role = "user",
                text = question,
                createdAt = userCreatedAt
            )
            val recentItems = itemRepository.getRecent(20)
            val threadHistory = chatRepository.getRecentMessages(threadId, 8)
            val directResponse = answerFromStoredData(question, recentItems)
            if (directResponse != null) {
                val directReferences = resolveAssistantReferences(
                    responseReferences = directResponse.references,
                    question = question,
                    reply = directResponse.reply,
                    recentItems = recentItems
                )
                val optimisticAssistantMessage = AssistantMessage(
                    id = "local-assistant-${System.currentTimeMillis()}",
                    role = "assistant",
                    text = directResponse.reply.ifBlank { "I did not find a direct answer in your saved data." },
                    createdAt = System.currentTimeMillis(),
                    references = directReferences
                )
                _state.value = _state.value.copy(
                    messages = stripIntroMessages(_state.value.messages) + optimisticAssistantMessage,
                    running = false,
                    runningLabel = ""
                )
                chatRepository.saveMessage(
                    threadId = threadId,
                    role = "assistant",
                    text = optimisticAssistantMessage.text,
                    createdAt = optimisticAssistantMessage.createdAt,
                    referencesJson = serializeReferences(directReferences)
                )
                return@launch
            }
            val context = buildContext(recentItems, threadHistory)
            val settings = settingsRepository.settings.first()
            val decision = router.decide(
                selectedMode = settings.aiMode,
                textLength = question.length + context.length,
                localAvailable = localLlmEngine.isAvailable(),
                ollamaConfigured = settings.ollamaBaseUrl.isNotBlank() && settings.ollamaModelName.isNotBlank(),
                sourceType = "assistant"
            )
            _state.value = _state.value.copy(
                runningLabel = when (decision.mode) {
                    AiMode.LOCAL -> "Assistant is thinking with local AI..."
                    AiMode.OLLAMA -> "Assistant is thinking with Ollama..."
                    AiMode.AUTO -> "Assistant is deciding how to answer..."
                }
            )
            val shouldAllowActions = shouldAllowActionSuggestions(question)
            val response = when (decision.mode) {
                AiMode.LOCAL -> localLlmEngine.answerQuestionStructured(question, context, shouldAllowActions)
                AiMode.OLLAMA -> ollamaRepositoryImpl.answerQuestionStructured(question, context, shouldAllowActions)
                    .getOrElse { AssistantChatResult(reply = "Ollama request failed: ${it.message.orEmpty()}") }
                AiMode.AUTO -> AssistantChatResult(reply = "Assistant routing did not resolve a concrete mode.")
            }
            val assistantActions = response.actions.mapNotNull { suggestion ->
                if (!shouldAllowActions) {
                    null
                } else {
                    val normalizedType = normalizeAssistantActionType(question, suggestion.type, suggestion.scheduledFor)
                    val scheduledMillis = AssistantDateTimeParser.parseToEpochMillis(suggestion.scheduledFor)
                    AssistantActionUi(
                        type = normalizedType,
                        title = suggestion.title,
                        details = suggestion.details,
                        scheduledForIso = suggestion.scheduledFor,
                        scheduledForMillis = scheduledMillis,
                        scheduledForLabel = AssistantDateTimeParser.formatForDisplay(scheduledMillis),
                        confirmationLabel = suggestion.confirmationLabel.ifBlank {
                            if (normalizedType == "create_reminder") "Add reminder" else "Add task"
                        },
                        fallbackNote = suggestion.fallbackNote
                    )
                }
            }
            val assistantReferences = resolveAssistantReferences(
                responseReferences = response.references,
                question = question,
                reply = response.reply,
                recentItems = recentItems
            )
            val optimisticAssistantMessage = AssistantMessage(
                id = "local-assistant-${System.currentTimeMillis()}",
                role = "assistant",
                text = response.reply.ifBlank { "I did not have a usable answer." },
                createdAt = System.currentTimeMillis(),
                actions = assistantActions,
                references = assistantReferences
            )
            _state.value = _state.value.copy(
                messages = stripIntroMessages(_state.value.messages) + optimisticAssistantMessage,
                running = false,
                runningLabel = ""
            )
            chatRepository.saveMessage(
                threadId = threadId,
                role = "assistant",
                text = optimisticAssistantMessage.text,
                createdAt = optimisticAssistantMessage.createdAt,
                actionsJson = serializeActions(assistantActions),
                referencesJson = serializeReferences(assistantReferences)
            )
        }
    }

    fun confirmAssistantAction(messageId: String, actionId: String) {
        val action = _state.value.messages
            .firstOrNull { it.id == messageId }
            ?.actions
            ?.firstOrNull { it.id == actionId }
            ?: return
        if (action.status != AssistantActionStatus.PROPOSED) return
        updateAction(messageId, actionId) { it.copy(status = AssistantActionStatus.APPLYING, feedback = "") }
        viewModelScope.launch {
            val updated = runCatching {
                when (action.type) {
                    "create_task" -> {
                        taskRepository.addTask(
                            TaskEntity(
                                id = UUID.randomUUID().toString(),
                                itemId = null,
                                title = action.title,
                                details = action.details.ifBlank { null },
                                dueAt = action.scheduledForMillis,
                                isDone = false,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        action.copy(
                            status = AssistantActionStatus.CONFIRMED,
                            feedback = "Task added."
                        )
                    }
                    "create_reminder" -> {
                        val remindAt = action.scheduledForMillis
                            ?: error("I still need a full date and time before I can add that reminder.")
                        val reminderId = UUID.randomUUID().toString()
                        taskRepository.addReminder(
                            ReminderEntity(
                                id = reminderId,
                                itemId = null,
                                title = action.title,
                                remindAt = remindAt,
                                createdAt = System.currentTimeMillis()
                            )
                        )
                        reminderScheduler.schedule(reminderId, action.title, remindAt)
                        action.copy(
                            status = AssistantActionStatus.CONFIRMED,
                            feedback = "Reminder added for ${AssistantDateTimeParser.formatForDisplay(remindAt)}."
                        )
                    }
                    else -> error("Unsupported assistant action.")
                }
            }.getOrElse { error ->
                action.copy(
                    status = AssistantActionStatus.FAILED,
                    feedback = error.message ?: "Could not apply this action."
                )
            }
            updateAction(messageId, actionId) { updated }
            persistMessageState(messageId)
        }
    }

    fun dismissAssistantAction(messageId: String, actionId: String) {
        updateAction(messageId, actionId) {
            if (it.status == AssistantActionStatus.PROPOSED || it.status == AssistantActionStatus.FAILED) {
                it.copy(status = AssistantActionStatus.DISMISSED, feedback = "Dismissed.")
            } else {
                it
            }
        }
        viewModelScope.launch { persistMessageState(messageId) }
    }

    private suspend fun buildContext(items: List<ItemEntity>, threadMessages: List<ChatMessageEntity>): String {
        val tasks = taskRepository.getOpen(20)
        val reminders = taskRepository.getUpcomingReminders()
        val aiResults = aiRepository.getRecentResults(10)
        return buildString {
            if (threadMessages.isNotEmpty()) {
                appendLine("Recent conversation in this thread:")
                threadMessages.takeLast(8).forEachIndexed { index, message ->
                    append(index + 1)
                    append(". ")
                    append(message.role)
                    append(": ")
                    appendLine(message.text.take(180))
                }
                appendLine()
            }
            appendLine("Recent items:")
            items.forEachIndexed { index, item ->
                append(index + 1)
                append(". itemId=")
                append(item.id)
                append(" classification=")
                append(item.classification ?: item.type)
                append(" summary=\"")
                append(item.rawText.take(220).replace("\"", "'"))
                appendLine("\"")
            }
            appendLine()
            appendLine("Open tasks:")
            tasks.forEachIndexed { index, task ->
                append(index + 1)
                append(". ")
                append(task.title)
                if (!task.details.isNullOrBlank()) {
                    append(" - ")
                    append(task.details)
                }
                appendLine()
            }
            appendLine()
            appendLine("Upcoming reminders:")
            reminders.forEachIndexed { index, reminder ->
                append(index + 1)
                append(". ")
                append(reminder.title)
                append(" at ")
                append(AssistantDateTimeParser.formatForDisplay(reminder.remindAt))
                appendLine()
            }
            appendLine()
            appendLine("Recent AI summaries:")
            aiResults.forEachIndexed { index, result ->
                append(index + 1)
                append(". ")
                append(result.summary.take(180))
                appendLine()
            }
        }.trim()
    }

    private fun updateAction(
        messageId: String,
        actionId: String,
        transform: (AssistantActionUi) -> AssistantActionUi
    ) {
        _state.value = _state.value.copy(
            messages = _state.value.messages.map { message ->
                if (message.id != messageId) {
                    message
                } else {
                    message.copy(
                        actions = message.actions.map { action ->
                            if (action.id == actionId) transform(action) else action
                        }
                    )
                }
            }
        )
    }

    private fun observeThreads() {
        viewModelScope.launch {
            chatRepository.observeThreads().collect { threads ->
                val threadUi = threads.map {
                    AssistantThreadUi(
                        id = it.id,
                        title = it.title,
                        updatedAt = it.updatedAt
                    )
                }
                val currentTitle = threadUi.firstOrNull { it.id == _state.value.currentThreadId }?.title
                    ?: _state.value.currentThreadTitle
                _state.value = _state.value.copy(
                    threads = threadUi,
                    currentThreadTitle = currentTitle
                )
                if (_state.value.currentThreadId.isBlank() && threadUi.isNotEmpty()) {
                    openThread(threadUi.first().id)
                }
            }
        }
    }

    private fun normalizeAssistantActionType(question: String, suggestedType: String, scheduledFor: String): String {
        val lowerQuestion = question.lowercase()
        val looksLikeReminderIntent = listOf("remind", "reminder", "appointment", "schedule", "calendar")
            .any(lowerQuestion::contains)
        return if (suggestedType == "create_task" && scheduledFor.isNotBlank() && looksLikeReminderIntent) {
            "create_reminder"
        } else {
            suggestedType
        }
    }

    private fun shouldAllowActionSuggestions(question: String): Boolean {
        val normalized = question.lowercase().trim()
        val directActionPatterns = listOf(
            "\\bremind me\\b",
            "\\bset a reminder\\b",
            "\\bcreate a reminder\\b",
            "\\bschedule\\b",
            "\\badd (a )?task\\b",
            "\\bcreate (a )?task\\b",
            "\\bmake (a )?task\\b",
            "\\badd this to my (tasks|todo|to-do|checklist|calendar)\\b",
            "\\bput this on my calendar\\b",
            "\\bcreate an appointment\\b",
            "\\bset an appointment\\b"
        )
        if (directActionPatterns.any { Regex(it).containsMatchIn(normalized) }) return true
        val imperativeStart = listOf("remind", "schedule", "add", "create", "set", "put")
        return imperativeStart.any { normalized.startsWith("$it ") }
    }

    private fun defaultReferenceLabel(item: ItemEntity): String {
        val kind = item.classification?.replaceFirstChar { it.uppercase() } ?: "Item"
        return "Open $kind"
    }

    private suspend fun currentThreadId(): String {
        val existing = _state.value.currentThreadId
        if (existing.isNotBlank()) return existing
        val thread = chatRepository.getLatestOrCreate()
        openThread(thread.id)
        return thread.id
    }

    private fun introMessage() = AssistantMessage(
        id = "assistant-intro",
        role = "assistant",
        text = "Ask about your recent items, extracted reminders, bills, or tasks.",
        createdAt = 0L
    )

    private fun stripIntroMessages(messages: List<AssistantMessage>): List<AssistantMessage> =
        messages.filterNot { it.id == "assistant-intro" }

    private fun mapPersistedMessage(message: ChatMessageEntity): AssistantMessage {
        val actions = runCatching {
            assistantJson.decodeFromString(
                ListSerializer(StoredAssistantAction.serializer()),
                message.actionsJson.ifBlank { "[]" }
            )
        }.getOrDefault(emptyList()).map { stored ->
            val status = runCatching { AssistantActionStatus.valueOf(stored.status.ifBlank { "PROPOSED" }) }
                .getOrDefault(AssistantActionStatus.PROPOSED)
            val scheduledMillis = AssistantDateTimeParser.parseToEpochMillis(stored.scheduledForIso)
            AssistantActionUi(
                type = stored.type,
                title = stored.title,
                details = stored.details,
                scheduledForIso = stored.scheduledForIso,
                scheduledForMillis = scheduledMillis,
                scheduledForLabel = AssistantDateTimeParser.formatForDisplay(scheduledMillis),
                confirmationLabel = stored.confirmationLabel.ifBlank {
                    if (stored.type == "create_reminder") "Add reminder" else "Add task"
                },
                fallbackNote = stored.fallbackNote,
                status = status,
                feedback = stored.feedback
            )
        }
        val references = runCatching {
            assistantJson.decodeFromString(
                ListSerializer(AssistantItemReference.serializer()),
                message.referencesJson.ifBlank { "[]" }
            )
        }.getOrDefault(emptyList()).map {
            AssistantReferenceUi(itemId = it.itemId, label = it.label)
        }
        return AssistantMessage(
            id = message.id,
            role = message.role,
            text = message.text,
            createdAt = message.createdAt,
            actions = actions,
            references = references
        )
    }

    private fun serializeActions(actions: List<AssistantActionUi>): String =
        assistantJson.encodeToString(
            ListSerializer(StoredAssistantAction.serializer()),
            actions.map {
                StoredAssistantAction(
                    type = it.type,
                    title = it.title,
                    details = it.details,
                    scheduledForIso = it.scheduledForIso,
                    confirmationLabel = it.confirmationLabel,
                    fallbackNote = it.fallbackNote,
                    status = it.status.name,
                    feedback = it.feedback
                )
            }
        )

    private fun serializeReferences(references: List<AssistantReferenceUi>): String =
        assistantJson.encodeToString(
            ListSerializer(AssistantItemReference.serializer()),
            references.map { AssistantItemReference(itemId = it.itemId, label = it.label) }
        )

    private suspend fun persistMessageState(messageId: String) {
        val message = _state.value.messages.firstOrNull { it.id == messageId } ?: return
        chatRepository.updateMessagePayload(
            messageId = messageId,
            actionsJson = serializeActions(message.actions),
            referencesJson = serializeReferences(message.references)
        )
    }

    private fun resolveAssistantReferences(
        responseReferences: List<AssistantItemReference>,
        question: String,
        reply: String,
        recentItems: List<ItemEntity>
    ): List<AssistantReferenceUi> {
        val explicit = responseReferences.mapNotNull { reference ->
            val matched = recentItems.firstOrNull { it.id == reference.itemId } ?: return@mapNotNull null
            AssistantReferenceUi(
                itemId = matched.id,
                label = reference.label.ifBlank { defaultReferenceLabel(matched) }
            )
        }
        if (explicit.isNotEmpty()) return explicit

        val combined = "$question $reply".lowercase()
        val preferredClassification = when {
            combined.contains("bill") || combined.contains("payment") || combined.contains("due") -> "bill"
            combined.contains("appointment") || combined.contains("meeting") || combined.contains("calendar") || combined.contains("visit") -> "appointment"
            combined.contains("message") || combined.contains("email") || combined.contains("text") -> "message"
            combined.contains("note") -> "note"
            else -> null
        }
        val inferredItem = preferredClassification?.let { classification ->
            recentItems.firstOrNull { it.classification == classification }
        }
        return inferredItem?.let { listOf(AssistantReferenceUi(it.id, defaultReferenceLabel(it))) } ?: emptyList()
    }

    private suspend fun answerFromStoredData(question: String, recentItems: List<ItemEntity>): AssistantChatResult? {
        val normalized = question.lowercase(Locale.getDefault())
        return when {
            looksLikeBillLookup(normalized) -> buildBillLookupAnswer(recentItems)
            looksLikeAppointmentLookup(normalized) -> buildAppointmentLookupAnswer(recentItems)
            looksLikeReminderLookup(normalized) -> buildReminderLookupAnswer()
            looksLikeTaskLookup(normalized) -> buildTaskLookupAnswer()
            else -> null
        }
    }

    private fun looksLikeBillLookup(question: String): Boolean =
        question.contains("bill") && listOf("next", "upcoming", "due", "when", "what").any(question::contains)

    private fun looksLikeAppointmentLookup(question: String): Boolean =
        listOf("appointment", "meeting", "visit").any(question::contains) &&
            listOf("next", "upcoming", "when", "what").any(question::contains)

    private fun looksLikeReminderLookup(question: String): Boolean =
        question.contains("reminder") && listOf("next", "upcoming", "when", "what").any(question::contains)

    private fun looksLikeTaskLookup(question: String): Boolean =
        listOf("task", "todo", "to-do", "checklist").any(question::contains) &&
            listOf("next", "upcoming", "open", "what").any(question::contains)

    private fun buildBillLookupAnswer(items: List<ItemEntity>): AssistantChatResult? {
        val candidate = items
            .mapNotNull { item ->
                if (inferItemKind(item) != "bill") return@mapNotNull null
                val dueText = extractNaturalDateText(item.rawText)
                BillLookupCandidate(
                    item = item,
                    dueText = dueText,
                    dueAt = parseNaturalDateTime(dueText),
                    amount = extractAmount(item.rawText),
                    vendor = extractBillVendor(item.rawText)
                )
            }
            .sortedWith(compareBy<BillLookupCandidate> { it.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.item.createdAt })
            .firstOrNull()
            ?: return null
        val reply = buildString {
            append("Your next bill")
            if (!candidate.vendor.isNullOrBlank()) {
                append(" is ")
                append(candidate.vendor)
            }
            if (!candidate.amount.isNullOrBlank()) {
                append(" for ")
                append(candidate.amount)
            }
            candidate.dueText?.let {
                append(" due ")
                append(it)
            }
            append(".")
        }
        return AssistantChatResult(
            reply = reply,
            references = listOf(AssistantItemReference(candidate.item.id, "View bill"))
        )
    }

    private fun buildAppointmentLookupAnswer(items: List<ItemEntity>): AssistantChatResult? {
        val candidate = items
            .mapNotNull { item ->
                if (inferItemKind(item) != "appointment") return@mapNotNull null
                val whenText = extractNaturalDateText(item.rawText)
                AppointmentLookupCandidate(
                    item = item,
                    title = extractAppointmentTitle(item.rawText),
                    whenText = whenText,
                    whenAt = parseNaturalDateTime(whenText),
                    location = extractAppointmentLocation(item.rawText)
                )
            }
            .sortedWith(compareBy<AppointmentLookupCandidate> { it.whenAt ?: Long.MAX_VALUE }.thenByDescending { it.item.createdAt })
            .firstOrNull()
            ?: return null
        val reply = buildString {
            append("Your next appointment")
            if (!candidate.title.isNullOrBlank()) {
                append(" is ")
                append(candidate.title)
            }
            candidate.whenText?.let {
                append(" on ")
                append(it)
            }
            if (!candidate.location.isNullOrBlank()) {
                append(" at ")
                append(candidate.location)
            }
            append(".")
        }
        return AssistantChatResult(
            reply = reply,
            references = listOf(AssistantItemReference(candidate.item.id, "View appointment"))
        )
    }

    private suspend fun buildReminderLookupAnswer(): AssistantChatResult? {
        val reminder = taskRepository.getUpcomingReminders(limit = 1).firstOrNull() ?: return null
        return AssistantChatResult(
            reply = "Your next reminder is ${reminder.title} at ${AssistantDateTimeParser.formatForDisplay(reminder.remindAt)}."
        )
    }

    private suspend fun buildTaskLookupAnswer(): AssistantChatResult? {
        val task = taskRepository.getOpen(limit = 1).firstOrNull() ?: return null
        val dueLabel = AssistantDateTimeParser.formatForDisplay(task.dueAt)
        val reply = if (dueLabel.isNotBlank()) {
            "Your next open task is ${task.title}, due $dueLabel."
        } else {
            "Your next open task is ${task.title}."
        }
        return AssistantChatResult(reply = reply)
    }

    private fun inferItemKind(item: ItemEntity): String {
        val stored = item.classification?.lowercase(Locale.getDefault()).orEmpty()
        if (stored.isNotBlank() && stored != "unknown") return stored
        val raw = item.rawText.lowercase(Locale.getDefault())
        return when {
            raw.contains("bill") || raw.contains("amount due") -> "bill"
            raw.contains("appointment") || raw.contains("meeting") || raw.contains("checkup") || raw.contains("dentist") -> "appointment"
            raw.contains("message") || raw.contains("email") || raw.contains("text") -> "message"
            raw.contains("note") -> "note"
            else -> "unknown"
        }
    }

    private fun extractAmount(raw: String): String? =
        Regex("""\$ ?\d[\d,]*(?:\.\d{2})?""")
            .find(raw)
            ?.value
            ?.replace(" ", "")

    private fun extractBillVendor(raw: String): String? =
        Regex("""([A-Z][A-Za-z0-9&.' -]{2,40})\s+bill""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()

    private fun extractAppointmentTitle(raw: String): String? {
        val cleaned = raw
            .removePrefix("Appointment:")
            .removePrefix("appointment:")
            .trim()
        return cleaned.substringBefore(" on ").substringBefore(",").trim().takeIf { it.isNotBlank() }
    }

    private fun extractAppointmentLocation(raw: String): String? {
        val commaLocation = raw.substringAfter(", ", "").trim()
        if (commaLocation.isNotBlank() && commaLocation != raw.trim()) {
            return commaLocation.substringBefore(".").trim()
        }
        return Regex("""\b(?:at|location)\s+([^.,]+(?:\s[^.,]+){0,5})""", RegexOption.IGNORE_CASE)
            .find(raw)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractNaturalDateText(raw: String): String? =
        Regex(
            """\b(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\s+\d{1,2}(?:,\s*\d{4})?(?:\s+at\s+\d{1,2}(?::\d{2})?\s*(?:AM|PM))?""",
            RegexOption.IGNORE_CASE
        ).find(raw)?.value?.trim()

    private fun parseNaturalDateTime(value: String?): Long? {
        val text = value?.trim().orEmpty()
        if (text.isBlank()) return null
        val normalized = text.replace(Regex("\\s+"), " ")
        val now = LocalDate.now()
        val patterns = listOf(
            "MMM d, yyyy 'at' h:mm a",
            "MMMM d, yyyy 'at' h:mm a",
            "MMM d 'at' h:mm a",
            "MMMM d 'at' h:mm a",
            "MMM d, yyyy",
            "MMMM d, yyyy",
            "MMM d",
            "MMMM d"
        )
        patterns.forEach { pattern ->
            val hadExplicitYear = normalized.contains(Regex(""",\s*\d{4}\b"""))
            runCatching {
                val parseInput = if (pattern.contains("yyyy")) normalized else "$normalized, ${now.year}"
                val formatter = if (pattern.contains("yyyy")) {
                    DateTimeFormatter.ofPattern(pattern, Locale.US)
                } else {
                    DateTimeFormatterBuilder()
                        .appendPattern("$pattern, yyyy")
                        .parseDefaulting(ChronoField.YEAR, now.year.toLong())
                        .toFormatter(Locale.US)
                }
                if (pattern.contains("h:mm")) {
                    val parsed = LocalDateTime.parse(parseInput, formatter)
                    adjustYearIfPast(parsed, hadExplicitYear).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                } else {
                    val parsed = LocalDate.parse(parseInput, formatter)
                    adjustYearIfPast(parsed.atTime(LocalTime.of(9, 0)), hadExplicitYear)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                }
            }.getOrNull()?.let { return it }
        }
        return null
    }

    private fun adjustYearIfPast(value: LocalDateTime, hadExplicitYear: Boolean): LocalDateTime {
        if (hadExplicitYear) return value
        val now = LocalDateTime.now()
        return if (value.isBefore(now.minusDays(1))) value.plusYears(1) else value
    }

    private data class BillLookupCandidate(
        val item: ItemEntity,
        val dueText: String?,
        val dueAt: Long?,
        val amount: String?,
        val vendor: String?
    )

    private data class AppointmentLookupCandidate(
        val item: ItemEntity,
        val title: String?,
        val whenText: String?,
        val whenAt: Long?,
        val location: String?
    )
}

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val aiRepository: AiRepository,
    private val taskRepository: TaskRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {
    fun observeItem(itemId: String) = itemRepository.observeById(itemId)
    fun observeLatestResult(itemId: String) = aiRepository.observeLatest(itemId)

    fun rerunLocal(itemId: String, rawText: String, sourceType: String) = viewModelScope.launch {
        val result = aiRepository.runWithMode(itemId, rawText, AiMode.LOCAL, sourceType)
        itemRepository.updateClassification(itemId, result.classification)
    }

    fun sendToOllama(itemId: String, rawText: String, sourceType: String) = viewModelScope.launch {
        val result = aiRepository.runWithMode(itemId, rawText, AiMode.OLLAMA, sourceType)
        itemRepository.updateClassification(itemId, result.classification)
    }

    fun saveEditedResult(itemId: String, summary: String, extractedJson: String, modelName: String) = viewModelScope.launch {
        val result = aiRepository.saveEditedResult(itemId, extractedJson, summary, modelName)
        itemRepository.updateClassification(itemId, result.classification)
    }

    fun addTaskFromExtraction(itemId: String, title: String, details: String?) = viewModelScope.launch {
        taskRepository.addTask(
            TaskEntity(
                id = UUID.randomUUID().toString(),
                itemId = itemId,
                title = title,
                details = details,
                dueAt = null,
                isDone = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    fun createReminder(itemId: String, title: String, remindAt: Long) = viewModelScope.launch {
        val id = UUID.randomUUID().toString()
        taskRepository.addReminder(
            ReminderEntity(
                id = id,
                itemId = itemId,
                title = title,
                remindAt = remindAt,
                createdAt = System.currentTimeMillis()
            )
        )
        reminderScheduler.schedule(id, title, remindAt)
    }
}

@HiltViewModel
class TasksViewModel @Inject constructor(private val taskRepository: TaskRepository) : ViewModel() {
    private fun startOfDay(now: Long): Long {
        val zone = java.util.TimeZone.getDefault()
        val cal = java.util.Calendar.getInstance(zone).apply { timeInMillis = now }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private val dayStart = startOfDay(System.currentTimeMillis())
    private val nextDay = dayStart + 24L * 60L * 60L * 1000L
    val today = taskRepository.observeToday(dayStart, nextDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val upcoming = taskRepository.observeUpcoming(nextDay)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val open = taskRepository.observeOpen().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val done = taskRepository.observeDone().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggle(task: TaskEntity) = viewModelScope.launch {
        taskRepository.setDone(task.id, !task.isDone)
    }

    fun addManualTask(title: String) = viewModelScope.launch {
        if (title.isBlank()) return@launch
        taskRepository.addTask(
            TaskEntity(
                id = UUID.randomUUID().toString(),
                itemId = null,
                title = title.trim(),
                details = null,
                dueAt = null,
                isDone = false,
                createdAt = System.currentTimeMillis()
            )
        )
    }
}

data class SettingsUiState(
    val aiMode: String = AiMode.LOCAL.name,
    val availableModels: List<LocalModelProfile> = ModelConfig.profiles,
    val selectedLocalModelId: String = ModelConfig.defaultProfile.id,
    val localModelInstalled: Boolean = false,
    val localModelVersion: String = "",
    val localModelPath: String = "",
    val localModelStorageMb: Long = 0,
    val localModelDownloadMessage: String = "",
    val localModelDownloadInProgress: Boolean = false,
    val localModelDownloadProgress: Int = 0,
    val modelDownloadAuthToken: String = "",
    val ollamaBaseUrl: String = "",
    val ollamaApiToken: String = "",
    val ollamaModelName: String = "",
    val allowSelfSignedCertificates: Boolean = false,
    val showPromptDebug: Boolean = false,
    val dataToolsMessage: String = "",
    val localModelSelfTestMessage: String = "",
    val localModelSelfTestRunning: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val modelRepository: ModelRepository,
    private val ollamaRepositoryImpl: OllamaRepositoryImpl,
    private val localModelDownloadManager: LocalModelDownloadManager,
    private val localModelManager: LocalModelManager,
    private val dataMaintenanceRepository: DataMaintenanceRepository,
    private val localLlmEngine: LocalLlmEngine
) : ViewModel() {
    private val dataToolsState = MutableStateFlow("")
    private val downloadState = MutableStateFlow(Pair(false, 0))
    private val downloadMessageState = MutableStateFlow("")
    private val localSelfTestMessageState = MutableStateFlow("")
    private val localSelfTestRunningState = MutableStateFlow(false)
    val state = settingsRepository.settings
        .combine(dataToolsState) { s, dataToolsMessage -> s to dataToolsMessage }
        .combine(downloadState) { (s, dataToolsMessage), downloadState ->
            Triple(s, dataToolsMessage, downloadState)
        }
        .combine(downloadMessageState) { (s, dataToolsMessage, downloadState), downloadMessage ->
            (Triple(s, dataToolsMessage, downloadState)) to downloadMessage
        }
        .combine(localSelfTestMessageState) { (values, downloadMessage), selfTestMessage ->
            Triple(values, downloadMessage, selfTestMessage)
        }
        .combine(localSelfTestRunningState) { (values, downloadMessage, selfTestMessage), selfTestRunning ->
            val actualInstalled = modelRepository.isInstalled()
            val s = values.first
            val dataToolsMessage = values.second
            val downloadState = values.third
            SettingsUiState(
                aiMode = s.aiMode.name,
                selectedLocalModelId = s.selectedLocalModelId,
                localModelInstalled = actualInstalled,
                localModelVersion = s.localModelVersion,
                localModelPath = if (actualInstalled) localModelManager.modelPathOrEmpty() else "",
                localModelStorageMb = modelRepository.installedSizeMb(),
                localModelDownloadMessage = downloadMessage.ifBlank { s.localModelDownloadMessage },
                localModelDownloadInProgress = downloadState.first || s.localModelDownloadInProgress,
                localModelDownloadProgress = downloadState.second,
                modelDownloadAuthToken = s.modelDownloadAuthToken,
                ollamaBaseUrl = s.ollamaBaseUrl,
                ollamaApiToken = s.ollamaApiToken,
                ollamaModelName = s.ollamaModelName,
                allowSelfSignedCertificates = s.allowSelfSignedCertificates,
                showPromptDebug = s.showPromptDebug,
                dataToolsMessage = dataToolsMessage,
                localModelSelfTestMessage = selfTestMessage,
                localModelSelfTestRunning = selfTestRunning
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())
    private val _testMessage = MutableStateFlow("")
    val testMessage: StateFlow<String> = _testMessage

    init {
        viewModelScope.launch {
            localModelManager.reconcileInstallState()
        }
        observeDownloadState()
    }

    private fun observeDownloadState() {
        viewModelScope.launch {
            localModelDownloadManager.state.collect { download ->
                downloadState.value = download.inProgress to download.progress
                downloadMessageState.value = download.message
            }
        }
    }

    fun setLocal() = viewModelScope.launch { settingsRepository.update { it.copy(aiMode = AiMode.LOCAL) } }
    fun setOllama() = viewModelScope.launch { settingsRepository.update { it.copy(aiMode = AiMode.OLLAMA) } }
    fun setAuto() = viewModelScope.launch { settingsRepository.update { it.copy(aiMode = AiMode.AUTO) } }
    fun deleteModel() = viewModelScope.launch {
        localSelfTestMessageState.value = ""
        localModelDownloadManager.cancelAndDelete()
    }
    fun redownloadModel() = viewModelScope.launch {
        localSelfTestMessageState.value = ""
        settingsRepository.update {
            it.copy(
                localModelDownloadComplete = false,
                localModelDownloadInProgress = true,
                localModelDownloadMessage = "Starting ${ModelConfig.profileFor(it.selectedLocalModelId).displayName} download..."
            )
        }
        localModelDownloadManager.startOrResume()
    }
    fun selectLocalModel(modelId: String) = viewModelScope.launch {
        if (state.value.selectedLocalModelId == modelId) return@launch
        localSelfTestMessageState.value = ""
        localModelDownloadManager.cancelAndDelete()
        settingsRepository.update {
            it.copy(
                selectedLocalModelId = modelId,
                localModelDownloadComplete = false
            )
        }
    }
    fun updateModelDownloadAuthToken(v: String) = viewModelScope.launch {
        settingsRepository.update { it.copy(modelDownloadAuthToken = v.trim()) }
    }
    fun updateBaseUrl(v: String) = viewModelScope.launch { settingsRepository.update { it.copy(ollamaBaseUrl = v) } }
    fun updateOllamaApiToken(v: String) = viewModelScope.launch { settingsRepository.update { it.copy(ollamaApiToken = v.trim()) } }
    fun updateModelName(v: String) = viewModelScope.launch { settingsRepository.update { it.copy(ollamaModelName = v) } }
    fun toggleSelfSigned(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { it.copy(allowSelfSignedCertificates = enabled) }
    }
    fun togglePromptDebug(enabled: Boolean) = viewModelScope.launch {
        settingsRepository.update { it.copy(showPromptDebug = enabled) }
    }
    fun testOllama() = viewModelScope.launch {
        _testMessage.value = ollamaRepositoryImpl.testConnection().fold(
            onSuccess = { "Connected. Found ${it.size} model(s)." },
            onFailure = { "Connection failed: ${it.message}" }
        )
    }
    fun runLocalModelSelfTest() = viewModelScope.launch {
        localSelfTestRunningState.value = true
        localSelfTestMessageState.value = localLlmEngine.selfTest()
        localSelfTestRunningState.value = false
    }
    fun addDemoData() = viewModelScope.launch {
        dataToolsState.value = dataMaintenanceRepository.addDemoData()
    }
    fun clearLocalData() = viewModelScope.launch {
        dataToolsState.value = dataMaintenanceRepository.clearAllLocalData()
    }
}
