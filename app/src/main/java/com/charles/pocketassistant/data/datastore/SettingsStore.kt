package com.charles.pocketassistant.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class AiMode { LOCAL, OLLAMA, AUTO }

data class UserSettings(
    val onboardingComplete: Boolean = false,
    val aiMode: AiMode = AiMode.LOCAL,
    val selectedLocalModelId: String = com.charles.pocketassistant.ai.local.ModelConfig.defaultProfile.id,
    val localModelInstalled: Boolean = false,
    val localModelVersion: String = "",
    val localModelPath: String = "",
    val localModelDownloadMessage: String = "",
    val modelDownloadAuthToken: String = "",
    val ollamaBaseUrl: String = "",
    val ollamaApiToken: String = "",
    val ollamaModelName: String = "",
    val allowMeteredDownload: Boolean = false,
    val allowSelfSignedCertificates: Boolean = false,
    val showPromptDebug: Boolean = false,
    val localModelDownloadComplete: Boolean = false,
    val localModelDownloadInProgress: Boolean = false,
    val rewardCredits: Int = 0,
    val adFreeUntil: Long = 0L
)

fun UserSettings.isOllamaConfigured(): Boolean =
    ollamaBaseUrl.trim().isNotBlank() && ollamaModelName.trim().isNotBlank()

data class UpdatePrefs(
    val dismissedTag: String = "",
    val lastCheckMillis: Long = 0L
)

class SettingsStore(private val context: Context) {
    private object Keys {
        val dismissedUpdateTag = stringPreferencesKey("dismissedUpdateTag")
        val lastUpdateCheckMillis = longPreferencesKey("lastUpdateCheckMillis")
        val onboardingComplete = booleanPreferencesKey("onboardingComplete")
        val aiMode = stringPreferencesKey("aiMode")
        val selectedLocalModelId = stringPreferencesKey("selectedLocalModelId")
        val localModelInstalled = booleanPreferencesKey("localModelInstalled")
        val localModelVersion = stringPreferencesKey("localModelVersion")
        val localModelPath = stringPreferencesKey("localModelPath")
        val localModelDownloadMessage = stringPreferencesKey("localModelDownloadMessage")
        val modelDownloadAuthToken = stringPreferencesKey("modelDownloadAuthToken")
        val ollamaBaseUrl = stringPreferencesKey("ollamaBaseUrl")
        val ollamaApiToken = stringPreferencesKey("ollamaApiToken")
        val ollamaModelName = stringPreferencesKey("ollamaModelName")
        val allowMeteredDownload = booleanPreferencesKey("allowMeteredDownload")
        val allowSelfSignedCertificates = booleanPreferencesKey("allowSelfSignedCertificates")
        val showPromptDebug = booleanPreferencesKey("showPromptDebug")
        val localModelDownloadComplete = booleanPreferencesKey("localModelDownloadComplete")
        val localModelDownloadInProgress = booleanPreferencesKey("localModelDownloadInProgress")
        val rewardCredits = intPreferencesKey("rewardCredits")
        val adFreeUntil = longPreferencesKey("adFreeUntil")
    }

    val settings: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        UserSettings(
            onboardingComplete = prefs[Keys.onboardingComplete] ?: false,
            aiMode = AiMode.valueOf(prefs[Keys.aiMode] ?: AiMode.LOCAL.name),
            selectedLocalModelId = prefs[Keys.selectedLocalModelId] ?: com.charles.pocketassistant.ai.local.ModelConfig.defaultProfile.id,
            localModelInstalled = prefs[Keys.localModelInstalled] ?: false,
            localModelVersion = prefs[Keys.localModelVersion].orEmpty(),
            localModelPath = prefs[Keys.localModelPath].orEmpty(),
            localModelDownloadMessage = prefs[Keys.localModelDownloadMessage].orEmpty(),
            modelDownloadAuthToken = prefs[Keys.modelDownloadAuthToken].orEmpty(),
            ollamaBaseUrl = prefs[Keys.ollamaBaseUrl].orEmpty(),
            ollamaApiToken = prefs[Keys.ollamaApiToken].orEmpty(),
            ollamaModelName = prefs[Keys.ollamaModelName].orEmpty(),
            allowMeteredDownload = prefs[Keys.allowMeteredDownload] ?: false,
            allowSelfSignedCertificates = prefs[Keys.allowSelfSignedCertificates] ?: false,
            showPromptDebug = prefs[Keys.showPromptDebug] ?: false,
            localModelDownloadComplete = prefs[Keys.localModelDownloadComplete] ?: false,
            localModelDownloadInProgress = prefs[Keys.localModelDownloadInProgress] ?: false,
            rewardCredits = prefs[Keys.rewardCredits] ?: 0,
            adFreeUntil = prefs[Keys.adFreeUntil] ?: 0L
        )
    }

    suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { prefs ->
            val current = UserSettings(
                onboardingComplete = prefs[Keys.onboardingComplete] ?: false,
                aiMode = AiMode.valueOf(prefs[Keys.aiMode] ?: AiMode.LOCAL.name),
                selectedLocalModelId = prefs[Keys.selectedLocalModelId] ?: com.charles.pocketassistant.ai.local.ModelConfig.defaultProfile.id,
                localModelInstalled = prefs[Keys.localModelInstalled] ?: false,
                localModelVersion = prefs[Keys.localModelVersion].orEmpty(),
                localModelPath = prefs[Keys.localModelPath].orEmpty(),
                localModelDownloadMessage = prefs[Keys.localModelDownloadMessage].orEmpty(),
                modelDownloadAuthToken = prefs[Keys.modelDownloadAuthToken].orEmpty(),
                ollamaBaseUrl = prefs[Keys.ollamaBaseUrl].orEmpty(),
                ollamaApiToken = prefs[Keys.ollamaApiToken].orEmpty(),
                ollamaModelName = prefs[Keys.ollamaModelName].orEmpty(),
                allowMeteredDownload = prefs[Keys.allowMeteredDownload] ?: false,
                allowSelfSignedCertificates = prefs[Keys.allowSelfSignedCertificates] ?: false,
                showPromptDebug = prefs[Keys.showPromptDebug] ?: false,
                localModelDownloadComplete = prefs[Keys.localModelDownloadComplete] ?: false,
                localModelDownloadInProgress = prefs[Keys.localModelDownloadInProgress] ?: false,
                rewardCredits = prefs[Keys.rewardCredits] ?: 0,
                adFreeUntil = prefs[Keys.adFreeUntil] ?: 0L
            )
            val next = transform(current)
            prefs[Keys.onboardingComplete] = next.onboardingComplete
            prefs[Keys.aiMode] = next.aiMode.name
            prefs[Keys.selectedLocalModelId] = next.selectedLocalModelId
            prefs[Keys.localModelInstalled] = next.localModelInstalled
            prefs[Keys.localModelVersion] = next.localModelVersion
            prefs[Keys.localModelPath] = next.localModelPath
            prefs[Keys.localModelDownloadMessage] = next.localModelDownloadMessage
            prefs[Keys.modelDownloadAuthToken] = next.modelDownloadAuthToken
            prefs[Keys.ollamaBaseUrl] = next.ollamaBaseUrl
            prefs[Keys.ollamaApiToken] = next.ollamaApiToken
            prefs[Keys.ollamaModelName] = next.ollamaModelName
            prefs[Keys.allowMeteredDownload] = next.allowMeteredDownload
            prefs[Keys.allowSelfSignedCertificates] = next.allowSelfSignedCertificates
            prefs[Keys.showPromptDebug] = next.showPromptDebug
            prefs[Keys.localModelDownloadComplete] = next.localModelDownloadComplete
            prefs[Keys.localModelDownloadInProgress] = next.localModelDownloadInProgress
            prefs[Keys.rewardCredits] = next.rewardCredits
            prefs[Keys.adFreeUntil] = next.adFreeUntil
        }
    }

    suspend fun updatePrefs(): UpdatePrefs {
        val prefs = context.dataStore.data.first()
        return UpdatePrefs(
            dismissedTag = prefs[Keys.dismissedUpdateTag].orEmpty(),
            lastCheckMillis = prefs[Keys.lastUpdateCheckMillis] ?: 0L
        )
    }

    suspend fun setDismissedUpdateTag(tag: String) {
        context.dataStore.edit { it[Keys.dismissedUpdateTag] = tag }
    }

    suspend fun setLastUpdateCheck(millis: Long) {
        context.dataStore.edit { it[Keys.lastUpdateCheckMillis] = millis }
    }
}
