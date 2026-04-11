package com.charles.pocketassistant.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.charles.pocketassistant.ads.AdManager
import com.charles.pocketassistant.ai.DirectAnswerEngine
import com.charles.pocketassistant.ai.local.LocalLlmEngine
import com.charles.pocketassistant.ai.local.LocalModelDownloadManager
import com.charles.pocketassistant.ai.local.LocalModelManager
import com.charles.pocketassistant.ai.local.ModelConfig
import com.charles.pocketassistant.ai.local.LocalModelProfile
import com.charles.pocketassistant.ai.routing.AiRouter
import com.charles.pocketassistant.data.datastore.AiMode
import com.charles.pocketassistant.data.datastore.isOllamaConfigured
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
import com.charles.pocketassistant.data.repository.UpdateRepository
import com.charles.pocketassistant.domain.UpdateDecision
import com.charles.pocketassistant.domain.model.AssistantChatResult
import com.charles.pocketassistant.domain.model.AssistantItemReference
import com.charles.pocketassistant.domain.model.StoredAssistantAction
import com.charles.pocketassistant.ocr.OcrEngine
import com.charles.pocketassistant.util.AssistantDateTimeParser
import com.charles.pocketassistant.util.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
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

sealed interface UpdateCheckUiState {
    data object Idle : UpdateCheckUiState
    data object Checking : UpdateCheckUiState
    data object UpToDate : UpdateCheckUiState
    data object Error : UpdateCheckUiState
    data class Available(val release: com.charles.pocketassistant.domain.LatestRelease) : UpdateCheckUiState
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
    val ollamaRemoteModels: List<String> = emptyList(),
    val ollamaModelsLoading: Boolean = false,
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

    // Must be declared before init — init calls observeSettings() which combines these flows.
    private val ollamaRemoteModelsState = MutableStateFlow<List<String>>(emptyList())
    private val ollamaModelsLoadingState = MutableStateFlow(false)

    init {
        observeSettings()
        observeDownloadState()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                settingsRepository.settings,
                ollamaRemoteModelsState,
                ollamaModelsLoadingState
            ) { s, remoteModels, modelsLoading ->
                Triple(s, remoteModels, modelsLoading)
            }.collect { (s, remoteModels, modelsLoading) ->
                val localInstalled = s.localModelInstalled && localModelManager.isModelInstalled()
                val ollamaConfigured = s.isOllamaConfigured()
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
                    ollamaRemoteModels = remoteModels,
                    ollamaModelsLoading = modelsLoading,
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

    fun refreshOllamaModels() = viewModelScope.launch {
        val s = settingsRepository.settings.first()
        if (s.ollamaBaseUrl.isBlank()) {
            ollamaRemoteModelsState.value = emptyList()
            return@launch
        }
        ollamaModelsLoadingState.value = true
        val result = ollamaRepositoryImpl.testConnection()
        ollamaModelsLoadingState.value = false
        if (result.isSuccess) {
            val models = result.getOrNull().orEmpty()
            ollamaRemoteModelsState.value = models
            ollamaRepositoryImpl.applyDefaultOllamaModelIfNeeded(models)
        } else {
            ollamaRemoteModelsState.value = emptyList()
        }
    }

    fun testOllama() = viewModelScope.launch {
        val s = settingsRepository.settings.first()
        if (s.ollamaBaseUrl.isBlank()) {
            _state.value = _state.value.copy(ollamaTestMessage = "Enter a base URL first.")
            return@launch
        }
        ollamaModelsLoadingState.value = true
        val result = ollamaRepositoryImpl.testConnection()
        ollamaModelsLoadingState.value = false
        if (result.isSuccess) {
            val models = result.getOrNull().orEmpty()
            ollamaRemoteModelsState.value = models
            ollamaRepositoryImpl.applyDefaultOllamaModelIfNeeded(models)
            _state.value = _state.value.copy(
                ollamaTestMessage = "Connected. Found ${models.size} model(s)."
            )
        } else {
            ollamaRemoteModelsState.value = emptyList()
            _state.value = _state.value.copy(
                ollamaTestMessage = "Connection failed: ${result.exceptionOrNull()?.message}"
            )
        }
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
            if (it.isOllamaConfigured()) {
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
    private val ocrEngine: OcrEngine,
    private val notificationHelper: com.charles.pocketassistant.util.NotificationHelper,
    private val barcodeScannerEngine: com.charles.pocketassistant.ml.BarcodeScannerEngine
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
        notificationHelper.showAiComplete(id, result.summary.ifBlank { "Analysis complete" })
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
        // Scan for barcodes on image imports (fast, ~50ms)
        val barcodes = if (type == "image" || type == "screenshot") {
            barcodeScannerEngine.scanFromUri(uri)
        } else emptyList()
        if (raw.isBlank() && barcodes.isEmpty()) error("No text or barcodes found.")
        // Prepend barcode data to the text so AI can process it
        val barcodePrefix = if (barcodes.isNotEmpty()) {
            barcodes.joinToString("\n") { b ->
                buildString {
                    append("[Barcode/${b.type.name}: ${b.displayValue}]")
                    b.url?.let { append(" URL: $it") }
                    b.email?.let { append(" Email: $it") }
                    b.phone?.let { append(" Phone: $it") }
                }
            } + "\n\n"
        } else ""
        val combinedText = barcodePrefix + raw
        _processing.value = _processing.value.copy(ocrProgress = 100, aiProgress = 10)
        processTextImport(
            text = combinedText,
            sourceApp = sourceApp,
            type = type,
            localUri = uri.toString(),
            thumbnailUri = uri.toString(),
            navigateOnComplete = navigateOnComplete
        )
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val aiRepository: AiRepository,
    private val semanticSearchEngine: com.charles.pocketassistant.ml.SemanticSearchEngine,
    private val priorityScorer: com.charles.pocketassistant.ml.PriorityScorer,
    private val updateRepository: UpdateRepository
) : ViewModel() {
    val updateAvailable: StateFlow<UpdateDecision.Available?> = updateRepository.available
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), updateRepository.available.value)

    fun checkForUpdate(force: Boolean = false) {
        viewModelScope.launch {
            runCatching { updateRepository.checkForUpdate(force) }
        }
    }

    fun dismissUpdate() {
        val current = updateRepository.available.value ?: return
        viewModelScope.launch {
            updateRepository.dismiss(current.release.tag)
        }
    }

    val items = itemRepository.observeItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _summaries = MutableStateFlow<Map<String, String>>(emptyMap())
    val summaries: StateFlow<Map<String, String>> = _summaries

    private val _priorityScores = MutableStateFlow<Map<String, Float>>(emptyMap())
    val priorityScores: StateFlow<Map<String, Float>> = _priorityScores

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _searchResults = MutableStateFlow<List<ItemEntity>?>(null)
    val searchResults: StateFlow<List<ItemEntity>?> = _searchResults

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            _searchResults.value = null
        } else {
            viewModelScope.launch {
                // First try semantic search for better relevance
                val allItems = items.value
                if (allItems.isNotEmpty()) {
                    val texts = allItems.map { item ->
                        val summary = _summaries.value[item.id] ?: ""
                        "$summary ${item.rawText.take(200)} ${item.classification ?: ""}"
                    }
                    val ranked = semanticSearchEngine.rankBySimilarity(query, texts, topK = 20)
                    val semanticResults = ranked
                        .filter { it.similarity > 0.05f }
                        .map { allItems[it.index] }
                    if (semanticResults.isNotEmpty()) {
                        _searchResults.value = semanticResults
                        return@launch
                    }
                }
                // Fallback to SQL LIKE search
                itemRepository.search(query).collect { results ->
                    _searchResults.value = results
                }
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = null
    }

    init {
        checkForUpdate(force = false)
        viewModelScope.launch {
            items.collect { itemList ->
                val results = aiRepository.getRecentResults(200)
                val map = mutableMapOf<String, String>()
                for (r in results) {
                    if (r.itemId !in map && r.summary.isNotBlank() && !r.summary.trimStart().startsWith("{")) {
                        map[r.itemId] = r.summary
                    }
                }
                _summaries.value = map

                // Update semantic search IDF index
                val corpus = itemList.map { item ->
                    val summary = map[item.id] ?: ""
                    "$summary ${item.rawText.take(300)}"
                }
                if (corpus.isNotEmpty()) {
                    semanticSearchEngine.updateIdf(corpus)
                }

                // Score priorities for all items
                val scores = mutableMapOf<String, Float>()
                for (item in itemList) {
                    val summary = map[item.id] ?: item.rawText.take(200)
                    val extractedAmount = Regex("""\$[\d,]+\.?\d*""").find(summary)
                        ?.value?.replace("$", "")?.replace(",", "")?.toDoubleOrNull()
                    val features = com.charles.pocketassistant.ml.ItemFeatures(
                        classification = item.classification ?: "unknown",
                        text = summary,
                        dollarAmount = extractedAmount,
                        createdAtMillis = item.createdAt
                    )
                    scores[item.id] = priorityScorer.score(features).score
                }
                _priorityScores.value = scores
            }
        }
    }
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
    val error: String = "",
    val smartReplies: List<String> = emptyList()
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
    private val reminderScheduler: ReminderScheduler,
    private val smartReplyEngine: com.charles.pocketassistant.ml.SmartReplyEngine,
    private val directAnswerEngine: DirectAnswerEngine
) : ViewModel() {
    private companion object {
        // ~6000 chars ≈ ~1500 tokens, leaving room for prompt template + question + response
        const val LOCAL_CONTEXT_CHAR_LIMIT = 6000
    }

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
            currentThreadTitle = "New chat",
            messages = listOf(introMessage()),
            input = "",
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
                input = ""
            )
            chatRepository.saveMessage(
                threadId = threadId,
                role = "user",
                text = question,
                createdAt = userCreatedAt
            )
            val recentItems = itemRepository.getRecent(50)
            val threadHistory = chatRepository.getRecentMessages(threadId, 8)
            val tasks = taskRepository.getOpen(50)
            val reminders = taskRepository.getUpcomingReminders()
            val aiResults = aiRepository.getRecentResults(200)
            val aiResultMap = mutableMapOf<String, com.charles.pocketassistant.data.db.entity.AiResultEntity>()
            for (r in aiResults) {
                if (r.itemId !in aiResultMap) aiResultMap[r.itemId] = r
            }

            // Update IDF for semantic search with current item corpus
            directAnswerEngine.updateSearchIndex(recentItems, aiResultMap)

            // RAG-style retrieval: semantic + keyword search finds the RIGHT items
            val relevantItems = directAnswerEngine.findRelevantItems(question, recentItems, aiResultMap)
            Log.d("AssistantVM", "RAG: found ${relevantItems.size} relevant items" +
                (relevantItems.firstOrNull()?.let { " (top: ${it.item.classification} score=${it.relevanceScore})" } ?: ""))

            // Build structured context the LLM can actually use (even a 0.6B model)
            val historyPairs = threadHistory.map { it.role to it.text }
            val fullContext = directAnswerEngine.buildStructuredContext(
                question = question,
                relevantItems = relevantItems,
                allItems = recentItems,
                aiResults = aiResultMap,
                tasks = tasks,
                reminders = reminders,
                threadHistory = historyPairs
            )

            val settings = settingsRepository.settings.first()
            val localAvailable = localLlmEngine.isAvailable()
            val ollamaConfigured = settings.isOllamaConfigured()
            val decision = router.decide(
                selectedMode = settings.aiMode,
                localAvailable = localAvailable,
                ollamaConfigured = ollamaConfigured
            )
            Log.d("AssistantVM", "Routing: mode=${settings.aiMode} local=$localAvailable ollamaConfigured=$ollamaConfigured → ${decision.mode} (${decision.reason})")
            // Local models have small context windows (~4K tokens); cap context to fit.
            val context = if (decision.mode == AiMode.LOCAL) fullContext.take(LOCAL_CONTEXT_CHAR_LIMIT) else fullContext
            val localContext = fullContext.take(LOCAL_CONTEXT_CHAR_LIMIT)
            _state.value = _state.value.copy(
                runningLabel = when {
                    decision.mode == AiMode.LOCAL -> "Assistant is thinking with local AI..."
                    decision.mode == AiMode.OLLAMA -> "Assistant is thinking with Ollama..."
                    else -> "Assistant is thinking..."
                }
            )
            val shouldAllowActions = shouldAllowActionSuggestions(question)
            // Only allow Ollama fallback when the user has explicitly selected AUTO mode
            val allowOllamaFallback = settings.aiMode == AiMode.AUTO && ollamaConfigured
            var response = when (decision.mode) {
                AiMode.LOCAL -> tryLocalWithOllamaFallback(question, context, fullContext, shouldAllowActions, allowOllamaFallback)
                AiMode.OLLAMA -> when {
                    settings.aiMode == AiMode.AUTO && localAvailable ->
                        tryOllamaWithLocalFallback(question, context, localContext, shouldAllowActions)
                    else -> ollamaRepositoryImpl.answerQuestionStructured(question, context, shouldAllowActions)
                        .getOrElse { AssistantChatResult(reply = "Ollama request failed: ${it.message.orEmpty()}") }
                }
                else -> tryLocalWithOllamaFallback(question, context, fullContext, shouldAllowActions, allowOllamaFallback)
            }

            // The LLM ALWAYS runs first — it gets structured context from RAG retrieval
            // so even the 0.6B local model has the right data to work with.
            // The direct answer fallback ONLY fires when the LLM genuinely fails.
            Log.d("AssistantVM", "LLM responded (${response.reply.length} chars): ${response.reply.take(100)}")
            val llmProducedAnswer = !isLowQualityResponse(response) && !isGenericApology(response.reply)
            if (!llmProducedAnswer) {
                Log.d("AssistantVM", "LLM response insufficient, augmenting with structured data fallback")
                val directAnswer = directAnswerEngine.tryDirectAnswer(question, relevantItems, tasks, reminders)
                if (directAnswer != null) {
                    Log.d("AssistantVM", "Direct answer used as safety net: ${directAnswer.reply.take(80)}")
                    response = response.copy(reply = directAnswer.reply)
                }
            }
            var assistantActions = response.actions.mapNotNull { suggestion ->
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
            // If the user explicitly asked to create something but the model didn't
            // return actions (common with small local models), extract an action from
            // the question and the model's reply.
            if (shouldAllowActions && assistantActions.isEmpty()) {
                val fallbackAction = extractFallbackAction(question, response.reply)
                if (fallbackAction != null) {
                    assistantActions = listOf(fallbackAction)
                }
            }
            // Auto-execute actions when the user explicitly asked to create/add something.
            var replyText = response.reply.ifBlank { "I did not have a usable answer." }
            if (shouldAllowActions && assistantActions.isNotEmpty()) {
                val executed = autoExecuteActions(assistantActions)
                assistantActions = executed
                replyText = buildAutoExecuteReply(executed)
            }
            val assistantReferences = resolveAssistantReferences(
                responseReferences = response.references,
                question = question,
                reply = response.reply,
                recentItems = recentItems,
                relevantItems = relevantItems
            )
            val optimisticAssistantMessage = AssistantMessage(
                id = "local-assistant-${System.currentTimeMillis()}",
                role = "assistant",
                text = replyText,
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
            generateSmartReplies()
        }
    }

    private fun generateSmartReplies() {
        viewModelScope.launch {
            val messages = _state.value.messages.takeLast(6)
            if (messages.isEmpty()) return@launch
            val chatMessages = messages.map { msg ->
                com.charles.pocketassistant.ml.SmartReplyEngine.ChatMessage(
                    text = msg.text,
                    timestampMillis = msg.createdAt,
                    isLocalUser = msg.role == "user"
                )
            }
            val suggestions = smartReplyEngine.suggestReplies(chatMessages)
            _state.value = _state.value.copy(smartReplies = suggestions)
        }
    }

    fun useSmartReply(reply: String) {
        _state.value = _state.value.copy(input = reply, smartReplies = emptyList())
    }

    private fun isLocalModelFailure(reply: String): Boolean =
        reply.startsWith("Local model failed") ||
            reply.startsWith("Local model is not installed") ||
            reply == "The local model did not return a response."

    private fun isLowQualityResponse(result: AssistantChatResult): Boolean {
        val reply = result.reply.trim()
        if (isLocalModelFailure(reply)) return true
        // Too short to be useful (likely garbled)
        if (reply.length < 15) return true
        // Looks like raw JSON leaked through instead of a real reply
        if (reply.startsWith("{") || reply.startsWith("```")) return true
        // Generic fallback text from parser
        if (reply.contains("broken format") || reply.contains("could not parse")) return true
        return false
    }

    private fun isGenericApology(reply: String): Boolean {
        val lower = reply.lowercase().trim()
        return lower.contains("i cannot answer") ||
            lower.contains("i can't answer") ||
            lower.contains("i don't have enough") ||
            lower.contains("i do not have enough") ||
            lower.contains("sorry, i cannot") ||
            lower.contains("sorry, i can't") ||
            lower.contains("i'm not able to") ||
            lower.contains("i am not able to") ||
            (lower.contains("sorry") && lower.length < 80)
    }

    private suspend fun tryLocalWithOllamaFallback(
        question: String,
        localContext: String,
        fullContext: String,
        allowActions: Boolean,
        ollamaAvailable: Boolean
    ): AssistantChatResult {
        // LocalLlmEngine now retries internally with a simpler prompt variant
        val localResult = localLlmEngine.answerQuestionStructured(question, localContext, allowActions)
        if (isLocalModelFailure(localResult.reply) && ollamaAvailable) {
            _state.value = _state.value.copy(runningLabel = "Local model unavailable, falling back to Ollama...")
            return ollamaRepositoryImpl.answerQuestionStructured(question, fullContext, allowActions)
                .getOrElse { AssistantChatResult(reply = "Local model unavailable and Ollama fallback also failed: ${it.message.orEmpty()}") }
        }
        // If still low quality after engine's internal retry, try Ollama as last resort
        if (isLowQualityResponse(localResult) && ollamaAvailable) {
            Log.d("AssistantVM", "Low quality after local retries, trying Ollama...")
            _state.value = _state.value.copy(runningLabel = "Trying Ollama for a better answer...")
            return ollamaRepositoryImpl.answerQuestionStructured(question, fullContext, allowActions)
                .getOrElse { localResult }
        }
        return localResult
    }

    private suspend fun tryOllamaWithLocalFallback(
        question: String,
        ollamaContext: String,
        localContext: String,
        allowActions: Boolean
    ): AssistantChatResult {
        val ollamaResult = ollamaRepositoryImpl.answerQuestionStructured(question, ollamaContext, allowActions)
        return ollamaResult.getOrElse {
            _state.value = _state.value.copy(runningLabel = "Using local model (Ollama didn’t respond)…")
            localLlmEngine.answerQuestionStructured(question, localContext, allowActions)
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
                val itemId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                // Create a visible item on the home screen
                itemRepository.insert(
                    ItemEntity(
                        id = itemId,
                        type = "assistant",
                        sourceApp = null,
                        localUri = null,
                        thumbnailUri = null,
                        rawText = buildItemRawText(action),
                        createdAt = now,
                        classification = classificationForAction(action)
                    )
                )
                aiRepository.saveEditedResult(
                    itemId = itemId,
                    rawOutput = "{}",
                    fallbackSummary = buildItemSummary(action),
                    modelName = "assistant"
                )
                when (action.type) {
                    "create_task" -> {
                        taskRepository.addTask(
                            TaskEntity(
                                id = UUID.randomUUID().toString(),
                                itemId = itemId,
                                title = action.title,
                                details = action.details.ifBlank { null },
                                dueAt = action.scheduledForMillis,
                                isDone = false,
                                createdAt = now
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
                                itemId = itemId,
                                title = action.title,
                                remindAt = remindAt,
                                createdAt = now
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

    // Context building is now handled by DirectAnswerEngine.buildStructuredContext()

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
        // Questions asking FOR information should never auto-create things
        val infoPatterns = listOf(
            "^what'?s?\\b", "^when'?s?\\b", "^where'?s?\\b", "^who'?s?\\b", "^how\\b",
            "^whens\\b", "^wheres\\b", "^whos\\b", "^whats\\b",
            "^tell me\\b", "^show me\\b", "^list\\b", "^do i\\b",
            "^is (?:there|my|the)\\b", "^are (?:there|my|the)\\b",
            "^any\\b", "^which\\b", "^can you (?:tell|show|check|look)\\b",
            "\\bwhen is\\b", "\\bwhen'?s\\b", "\\bhow much\\b"
        )
        if (infoPatterns.any { Regex(it).containsMatchIn(normalized) }) return false

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
            "\\bset an appointment\\b",
            // Informational statements that imply tracking
            "\\b(?:i have|i got|i've got|there'?s) (?:a |an )?(?:dentist|doctor|appointment|meeting|bill|payment|deadline)\\b",
            "\\b(?:bill|payment|rent|invoice) (?:is )?due\\b",
            "\\bdue (?:on|by|next|this|tomorrow)\\b",
            "\\bneed to (?:pay|remember|do|finish|complete|submit)\\b",
            "\\bdon'?t forget\\b",
            "\\btrack (?:this|my|a)\\b"
        )
        if (directActionPatterns.any { Regex(it).containsMatchIn(normalized) }) return true
        val imperativeStart = listOf("remind", "schedule", "add", "create", "set", "put", "track")
        return imperativeStart.any { normalized.startsWith("$it ") }
    }

    /**
     * When the local model says "I've added X" in the reply but returns no actions,
     * extract the action from the user's question so we can auto-execute it.
     */
    private fun extractFallbackAction(question: String, reply: String): AssistantActionUi? {
        val lower = question.lowercase()
        // Determine type from question intent
        val isReminder = listOf("remind", "appointment", "schedule", "calendar", "bill", "due", "payment")
            .any { lower.contains(it) }
        val type = if (isReminder) "create_reminder" else "create_task"

        // Extract a title: use the model's reply to find keywords, or derive from question
        val title = extractTitleFromQuestion(question)
        if (title.isBlank()) return null

        // Extract amount if mentioned
        val amountMatch = Regex("""\$?\d+(?:\.\d{2})?""").find(question)
        val amount = amountMatch?.value?.let { if (!it.startsWith("$")) "$$it" else it }

        // Extract date from question using relative day names
        val scheduledFor = extractDateFromQuestion(question)

        val details = buildString {
            if (amount != null) append(amount)
            if (scheduledFor.isBlank() && amount == null) {
                // No details to add
            }
        }.trim()

        val scheduledMillis = if (scheduledFor.isNotBlank()) {
            AssistantDateTimeParser.parseToEpochMillis(scheduledFor)
        } else null

        return AssistantActionUi(
            type = type,
            title = title,
            details = details,
            scheduledForIso = scheduledFor,
            scheduledForMillis = scheduledMillis,
            scheduledForLabel = AssistantDateTimeParser.formatForDisplay(scheduledMillis),
            confirmationLabel = if (type == "create_reminder") "Add reminder" else "Add task",
            fallbackNote = ""
        )
    }

    private fun extractTitleFromQuestion(question: String): String {
        val patterns = listOf(
            // "add a bill for electric" / "create a new task for groceries"
            Regex("""(?:add|create|make|set up)\s+(?:a\s+)?(?:new\s+)?(.+?)(?:\s+for\s+(?:me|us))?(?:\.\s*its|\.\s*it's|,\s*its|,\s*it's|\.\s*due|,\s*due|$)""", RegexOption.IGNORE_CASE),
            Regex("""(?:add|create|make)\s+(?:a\s+)?(?:new\s+)?(.+?)(?:\s*\.\s*|\s*,\s*|$)""", RegexOption.IGNORE_CASE),
            // "remind me to call dentist"
            Regex("""remind me (?:to |about )?(.+?)(?:\s+(?:on|at|by|tomorrow|next|this)\b|$)""", RegexOption.IGNORE_CASE),
            // "I have a dentist appointment in two weeks" / "I got a meeting tomorrow"
            Regex("""(?:i have|i got|i've got|there'?s)\s+(?:a\s+|an\s+)?(.+?)(?:\s+(?:in|on|at|next|this|tomorrow|due)\b|$)""", RegexOption.IGNORE_CASE),
            // "need to pay rent by Friday"
            Regex("""need to\s+(.+?)(?:\s+(?:by|before|on|at)\b|$)""", RegexOption.IGNORE_CASE),
            // "bill is due next Tuesday"
            Regex("""(.+?)\s+(?:is\s+)?due\s""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(question)?.groupValues?.getOrNull(1)?.trim()
            if (!match.isNullOrBlank() && match.length >= 3) {
                return match.replaceFirstChar { it.uppercase() }
            }
        }
        // Last resort: strip common verbs and use as title
        return question.replace(Regex("""^(add|create|make|set|remind me to|remind me about|i have |i got |i've got )\s*""", RegexOption.IGNORE_CASE), "")
            .trim()
            .take(60)
            .replaceFirstChar { it.uppercase() }
    }

    private fun extractDateFromQuestion(question: String): String {
        val lower = question.lowercase()
        val today = java.time.LocalDate.now()
        val dayNames = mapOf(
            "monday" to java.time.DayOfWeek.MONDAY,
            "tuesday" to java.time.DayOfWeek.TUESDAY,
            "wednesday" to java.time.DayOfWeek.WEDNESDAY,
            "thursday" to java.time.DayOfWeek.THURSDAY,
            "friday" to java.time.DayOfWeek.FRIDAY,
            "saturday" to java.time.DayOfWeek.SATURDAY,
            "sunday" to java.time.DayOfWeek.SUNDAY
        )
        // "in X days/weeks/months"
        val relativeMatch = Regex("""in (\d+|a|an|two|three|four|five|six) (day|week|month)s?""").find(lower)
        if (relativeMatch != null) {
            val numStr = relativeMatch.groupValues[1]
            val num = when (numStr) {
                "a", "an" -> 1L
                "two" -> 2L
                "three" -> 3L
                "four" -> 4L
                "five" -> 5L
                "six" -> 6L
                else -> numStr.toLongOrNull() ?: 1L
            }
            val target = when (relativeMatch.groupValues[2]) {
                "day" -> today.plusDays(num)
                "week" -> today.plusWeeks(num)
                "month" -> today.plusMonths(num)
                else -> today.plusDays(num)
            }
            return "${target}T09:00"
        }
        // "next week" (no specific day)
        if (lower.contains("next week")) {
            return "${today.plusWeeks(1)}T09:00"
        }
        // Check for "next <day>" or just "<day>"
        for ((name, dow) in dayNames) {
            if (lower.contains(name)) {
                val target = today.with(java.time.temporal.TemporalAdjusters.next(dow))
                return "${target}T09:00"
            }
        }
        if (lower.contains("tomorrow")) {
            return "${today.plusDays(1)}T09:00"
        }
        if (lower.contains("today")) {
            return "${today}T18:00"
        }
        // Try ISO date pattern in the question
        val isoMatch = Regex("""\d{4}-\d{2}-\d{2}""").find(question)
        if (isoMatch != null) {
            return "${isoMatch.value}T09:00"
        }
        return ""
    }

    /**
     * Auto-execute proposed actions: create tasks/reminders immediately
     * and also create a visible Item on the home screen.
     */
    private suspend fun autoExecuteActions(actions: List<AssistantActionUi>): List<AssistantActionUi> {
        return actions.map { action ->
            runCatching {
                val itemId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                val classification = classificationForAction(action)
                // Create a visible item on the home screen
                itemRepository.insert(
                    ItemEntity(
                        id = itemId,
                        type = "assistant",
                        sourceApp = null,
                        localUri = null,
                        thumbnailUri = null,
                        rawText = buildItemRawText(action),
                        createdAt = now,
                        classification = classification
                    )
                )
                // Create an AI result so the detail screen shows a summary
                aiRepository.saveEditedResult(
                    itemId = itemId,
                    rawOutput = "{}",
                    fallbackSummary = buildItemSummary(action),
                    modelName = "assistant"
                )
                when (action.type) {
                    "create_task" -> {
                        taskRepository.addTask(
                            TaskEntity(
                                id = UUID.randomUUID().toString(),
                                itemId = itemId,
                                title = action.title,
                                details = action.details.ifBlank { null },
                                dueAt = action.scheduledForMillis,
                                isDone = false,
                                createdAt = now
                            )
                        )
                        action.copy(status = AssistantActionStatus.CONFIRMED, feedback = "Task added.")
                    }
                    "create_reminder" -> {
                        val remindAt = action.scheduledForMillis
                        if (remindAt != null) {
                            val reminderId = UUID.randomUUID().toString()
                            taskRepository.addReminder(
                                ReminderEntity(
                                    id = reminderId,
                                    itemId = itemId,
                                    title = action.title,
                                    remindAt = remindAt,
                                    createdAt = now
                                )
                            )
                            reminderScheduler.schedule(reminderId, action.title, remindAt)
                            action.copy(status = AssistantActionStatus.CONFIRMED, feedback = "Reminder set.")
                        } else {
                            // No date — create as task instead
                            taskRepository.addTask(
                                TaskEntity(
                                    id = UUID.randomUUID().toString(),
                                    itemId = itemId,
                                    title = action.title,
                                    details = action.details.ifBlank { null },
                                    dueAt = null,
                                    isDone = false,
                                    createdAt = now
                                )
                            )
                            action.copy(status = AssistantActionStatus.CONFIRMED, feedback = "Added as task (no date given).")
                        }
                    }
                    else -> action.copy(status = AssistantActionStatus.FAILED, feedback = "Unknown action type.")
                }
            }.getOrElse { e ->
                action.copy(status = AssistantActionStatus.FAILED, feedback = e.message ?: "Failed.")
            }
        }
    }

    private fun classificationForAction(action: AssistantActionUi): String {
        val titleLower = action.title.lowercase()
        return when {
            listOf("bill", "payment", "invoice", "rent", "electric", "water", "gas", "internet", "phone")
                .any { titleLower.contains(it) } -> "bill"
            listOf("appointment", "dentist", "doctor", "meeting", "interview")
                .any { titleLower.contains(it) } -> "appointment"
            else -> "note"
        }
    }

    private fun buildItemRawText(action: AssistantActionUi): String = buildString {
        append(action.title)
        if (action.details.isNotBlank()) {
            append("\n")
            append(action.details)
        }
        if (action.scheduledForLabel.isNotBlank()) {
            append("\nDue: ")
            append(action.scheduledForLabel)
        }
    }

    private fun buildItemSummary(action: AssistantActionUi): String = buildString {
        append(action.title)
        if (action.scheduledForLabel.isNotBlank()) {
            append(" — ")
            append(action.scheduledForLabel)
        }
    }

    private fun buildAutoExecuteReply(actions: List<AssistantActionUi>): String {
        val confirmed = actions.filter { it.status == AssistantActionStatus.CONFIRMED }
        val failed = actions.filter { it.status == AssistantActionStatus.FAILED }
        return buildString {
            for (action in confirmed) {
                val label = if (action.type == "create_reminder") "Reminder" else "Task"
                append("Done! $label \"${action.title}\" added")
                if (action.scheduledForLabel.isNotBlank()) {
                    append(" for ${action.scheduledForLabel}")
                }
                appendLine(".")
            }
            for (action in failed) {
                appendLine("Could not add \"${action.title}\": ${action.feedback}")
            }
        }.trim()
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

    @Suppress("UNUSED_PARAMETER")
    private fun resolveAssistantReferences(
        responseReferences: List<AssistantItemReference>,
        question: String,
        reply: String,
        recentItems: List<ItemEntity>,
        relevantItems: List<com.charles.pocketassistant.ai.RelevantItem> = emptyList()
    ): List<AssistantReferenceUi> {
        // 1. Explicit references from the LLM response (by item ID)
        val explicit = responseReferences.mapNotNull { reference ->
            val matched = recentItems.firstOrNull { it.id == reference.itemId } ?: return@mapNotNull null
            AssistantReferenceUi(
                itemId = matched.id,
                label = reference.label.ifBlank { defaultReferenceLabel(matched) }
            )
        }
        if (explicit.isNotEmpty()) return explicit

        // 2. Use semantically matched items from RAG retrieval (much more accurate)
        val topRelevant = relevantItems.firstOrNull { it.relevanceScore > 0.15f }
        if (topRelevant != null) {
            return listOf(AssistantReferenceUi(topRelevant.item.id, defaultReferenceLabel(topRelevant.item)))
        }

        return emptyList()
    }


}

@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val aiRepository: AiRepository,
    private val taskRepository: TaskRepository,
    private val reminderScheduler: ReminderScheduler,
    private val calendarSyncHelper: com.charles.pocketassistant.util.CalendarSyncHelper
) : ViewModel() {
    fun observeItem(itemId: String) = itemRepository.observeById(itemId)
    fun observeLatestResult(itemId: String) = aiRepository.observeLatest(itemId)

    private val _rerunning = MutableStateFlow(false)
    val rerunning: StateFlow<Boolean> = _rerunning

    private val _calendarStatus = MutableStateFlow("")
    val calendarStatus: StateFlow<String> = _calendarStatus

    fun hasCalendarPermission(): Boolean = calendarSyncHelper.hasCalendarPermission()

    fun addAppointmentToCalendar(result: com.charles.pocketassistant.domain.model.AiExtractionResult) {
        val info = result.appointmentInfo
        val dateStr = info.date.ifBlank { result.entities.dates.firstOrNull() ?: "" }
        val startMillis = AssistantDateTimeParser.parseToEpochMillis(dateStr)
        if (startMillis == null) {
            _calendarStatus.value = "Could not parse date: $dateStr"
            return
        }
        val eventId = calendarSyncHelper.insertEvent(
            title = info.title.ifBlank { result.summary },
            description = result.summary,
            startMillis = startMillis,
            location = info.location
        )
        _calendarStatus.value = if (eventId != null) "Added to calendar" else "Failed — check calendar permissions"
    }

    fun addBillToCalendar(result: com.charles.pocketassistant.domain.model.AiExtractionResult) {
        val info = result.billInfo
        val dateStr = info.dueDate.ifBlank { result.entities.dates.firstOrNull() ?: "" }
        val millis = AssistantDateTimeParser.parseToEpochMillis(dateStr)
        if (millis == null) {
            _calendarStatus.value = "Could not parse due date: $dateStr"
            return
        }
        val eventId = calendarSyncHelper.insertBillReminder(
            vendor = info.vendor.ifBlank { "Bill" },
            amount = info.amount,
            dueDateMillis = millis
        )
        _calendarStatus.value = if (eventId != null) "Bill reminder added to calendar" else "Failed — check calendar permissions"
    }

    fun clearCalendarStatus() { _calendarStatus.value = "" }

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted

    fun deleteItem(itemId: String) = viewModelScope.launch {
        itemRepository.delete(itemId)
        _deleted.value = true
    }

    fun rerunLocal(itemId: String, rawText: String, sourceType: String) = viewModelScope.launch {
        Log.d("ItemDetailVM", "rerunLocal: itemId=$itemId textLen=${rawText.length} type=$sourceType")
        if (rawText.isBlank()) {
            Log.w("ItemDetailVM", "rerunLocal: rawText is blank, skipping")
            return@launch
        }
        _rerunning.value = true
        try {
            val result = aiRepository.runWithMode(itemId, rawText, AiMode.LOCAL, sourceType)
            Log.d("ItemDetailVM", "rerunLocal done: summary=${result.summary.take(80)} class=${result.classification}")
            itemRepository.updateClassification(itemId, result.classification)
        } catch (e: Exception) {
            Log.e("ItemDetailVM", "rerunLocal failed", e)
        } finally {
            _rerunning.value = false
        }
    }

    fun sendToOllama(itemId: String, rawText: String, sourceType: String) = viewModelScope.launch {
        Log.d("ItemDetailVM", "sendToOllama: itemId=$itemId textLen=${rawText.length} type=$sourceType")
        _rerunning.value = true
        try {
            val result = aiRepository.runWithMode(itemId, rawText, AiMode.OLLAMA, sourceType)
            Log.d("ItemDetailVM", "sendToOllama done: summary=${result.summary.take(80)} class=${result.classification}")
            itemRepository.updateClassification(itemId, result.classification)
        } catch (e: Exception) {
            Log.e("ItemDetailVM", "sendToOllama failed", e)
        } finally {
            _rerunning.value = false
        }
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
    val ollamaRemoteModels: List<String> = emptyList(),
    val ollamaModelsLoading: Boolean = false,
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
    private val localLlmEngine: LocalLlmEngine,
    private val updateRepository: UpdateRepository
) : ViewModel() {
    private val _updateCheckState = MutableStateFlow<UpdateCheckUiState>(UpdateCheckUiState.Idle)
    val updateCheckState: StateFlow<UpdateCheckUiState> = _updateCheckState

    fun checkForUpdateNow() {
        _updateCheckState.value = UpdateCheckUiState.Checking
        viewModelScope.launch {
            val decision = updateRepository.checkForUpdate(force = true)
            _updateCheckState.value = when (decision) {
                is UpdateDecision.Available -> UpdateCheckUiState.Available(decision.release)
                UpdateDecision.Dismissed -> UpdateCheckUiState.UpToDate
                UpdateDecision.UpToDate -> UpdateCheckUiState.UpToDate
                UpdateDecision.CheckSkipped -> UpdateCheckUiState.Error
            }
        }
    }

    fun clearUpdateCheck() {
        _updateCheckState.value = UpdateCheckUiState.Idle
    }

    private val dataToolsState = MutableStateFlow("")
    private val downloadState = MutableStateFlow(Pair(false, 0))
    private val downloadMessageState = MutableStateFlow("")
    private val localSelfTestMessageState = MutableStateFlow("")
    private val localSelfTestRunningState = MutableStateFlow(false)
    private val ollamaRemoteModelsState = MutableStateFlow<List<String>>(emptyList())
    private val ollamaModelsLoadingState = MutableStateFlow(false)
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
        .combine(ollamaRemoteModelsState) { ui, models -> ui.copy(ollamaRemoteModels = models) }
        .combine(ollamaModelsLoadingState) { ui, loading -> ui.copy(ollamaModelsLoading = loading) }
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
    fun refreshOllamaModels() = viewModelScope.launch {
        val s = settingsRepository.settings.first()
        if (s.ollamaBaseUrl.isBlank()) {
            ollamaRemoteModelsState.value = emptyList()
            return@launch
        }
        ollamaModelsLoadingState.value = true
        val result = ollamaRepositoryImpl.testConnection()
        ollamaModelsLoadingState.value = false
        if (result.isSuccess) {
            val models = result.getOrNull().orEmpty()
            ollamaRemoteModelsState.value = models
            ollamaRepositoryImpl.applyDefaultOllamaModelIfNeeded(models)
        } else {
            ollamaRemoteModelsState.value = emptyList()
        }
    }

    fun testOllama() = viewModelScope.launch {
        val s = settingsRepository.settings.first()
        if (s.ollamaBaseUrl.isBlank()) {
            _testMessage.value = "Enter a base URL first."
            return@launch
        }
        ollamaModelsLoadingState.value = true
        val result = ollamaRepositoryImpl.testConnection()
        ollamaModelsLoadingState.value = false
        if (result.isSuccess) {
            val models = result.getOrNull().orEmpty()
            ollamaRemoteModelsState.value = models
            ollamaRepositoryImpl.applyDefaultOllamaModelIfNeeded(models)
            _testMessage.value = "Connected. Found ${models.size} model(s)."
        } else {
            ollamaRemoteModelsState.value = emptyList()
            _testMessage.value = "Connection failed: ${result.exceptionOrNull()?.message}"
        }
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

data class HandwritingUiState(
    val recognizedText: String = "",
    val recognizing: Boolean = false,
    val error: String = ""
)

@HiltViewModel
class HandwritingViewModel @Inject constructor(
    private val digitalInkEngine: com.charles.pocketassistant.ml.DigitalInkEngine
) : ViewModel() {
    private val _state = MutableStateFlow(HandwritingUiState())
    val state: StateFlow<HandwritingUiState> = _state

    private val collectedStrokes = mutableListOf<com.google.mlkit.vision.digitalink.Ink.Stroke>()

    fun addStroke(points: List<androidx.compose.ui.geometry.Offset>) {
        val strokeBuilder = com.google.mlkit.vision.digitalink.Ink.Stroke.builder()
        val baseTime = System.currentTimeMillis()
        for ((i, point) in points.withIndex()) {
            strokeBuilder.addPoint(
                com.google.mlkit.vision.digitalink.Ink.Point.create(
                    point.x, point.y, baseTime + i
                )
            )
        }
        collectedStrokes.add(strokeBuilder.build())
    }

    fun clearStrokes() {
        collectedStrokes.clear()
        _state.value = HandwritingUiState()
    }

    fun recognize() {
        if (collectedStrokes.isEmpty()) return
        _state.value = _state.value.copy(recognizing = true, error = "")
        viewModelScope.launch {
            runCatching {
                val inkBuilder = com.google.mlkit.vision.digitalink.Ink.builder()
                for (stroke in collectedStrokes) inkBuilder.addStroke(stroke)
                digitalInkEngine.recognize(inkBuilder.build())
            }.onSuccess { text ->
                _state.value = _state.value.copy(
                    recognizedText = text,
                    recognizing = false,
                    error = if (text.isBlank()) "Could not recognize handwriting." else ""
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    recognizing = false,
                    error = e.message ?: "Recognition failed."
                )
            }
        }
    }
}

// ── Rewards ─────────────────────────────────────────────────────────

data class RewardTier(
    val credits: Int,
    val hours: Int,
    val label: String
)

@HiltViewModel
class RewardsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    val adManager: AdManager
) : ViewModel() {

    companion object {
        val TIERS = listOf(
            RewardTier(1, 1, "1 hour"),
            RewardTier(3, 3, "3 hours"),
            RewardTier(6, 6, "6 hours")
        )
    }

    val settings = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.charles.pocketassistant.data.datastore.UserSettings())

    val rewardedReady = adManager.rewardedReady

    private val _snackbar = MutableStateFlow("")
    val snackbar: StateFlow<String> = _snackbar

    fun clearSnackbar() { _snackbar.value = "" }

    fun onRewardEarned() {
        viewModelScope.launch {
            settingsRepository.update { it.copy(rewardCredits = it.rewardCredits + 1) }
            _snackbar.value = "You earned 1 credit!"
        }
    }

    fun redeemTier(tier: RewardTier) {
        viewModelScope.launch {
            val current = settings.value
            if (current.rewardCredits < tier.credits) {
                _snackbar.value = "Not enough credits"
                return@launch
            }
            val now = System.currentTimeMillis()
            val currentEnd = if (current.adFreeUntil > now) current.adFreeUntil else now
            val newEnd = currentEnd + tier.hours * 3_600_000L
            settingsRepository.update {
                it.copy(
                    rewardCredits = it.rewardCredits - tier.credits,
                    adFreeUntil = newEnd
                )
            }
            _snackbar.value = "Ads disabled for ${tier.label}!"
        }
    }

    fun retryLoadRewarded() {
        adManager.loadRewarded()
    }
}
