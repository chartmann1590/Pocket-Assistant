package com.charles.pocketassistant.ai.local

import android.content.Context
import com.charles.pocketassistant.data.datastore.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class LocalModelStatus {
    NOT_INSTALLED,
    DOWNLOADING,
    INSTALLED,
    INVALID
}

@Singleton
class LocalModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore
) {
    val settings: Flow<com.charles.pocketassistant.data.datastore.UserSettings> = settingsStore.settings
    val status: Flow<LocalModelStatus> = settings.map { s ->
        when {
            s.localModelDownloadInProgress -> LocalModelStatus.DOWNLOADING
            !s.localModelInstalled -> LocalModelStatus.NOT_INSTALLED
            isModelInstalled() -> LocalModelStatus.INSTALLED
            else -> LocalModelStatus.INVALID
        }
    }

    // App-specific external storage survives app updates while remaining private to this app.
    private fun storageDir(): File = context.getExternalFilesDir(null) ?: context.filesDir

    fun modelFile(): File = File(storageDir(), ModelConfig.localFilename)

    fun modelPathOrEmpty(): String = modelFile().takeIf { validateModelFile(it) }?.absolutePath.orEmpty()

    fun installedSizeBytes(): Long = modelFile().takeIf { it.exists() }?.length() ?: 0L

    fun installedSizeMb(): Long = installedSizeBytes() / (1024L * 1024L)

    fun isModelInstalled(): Boolean {
        val f = modelFile()
        return validateModelFile(f)
    }

    fun validateModelFile(file: File = modelFile()): Boolean = file.exists() && file.length() > (5L * 1024L * 1024L)

    fun hasEnoughStorage(profile: LocalModelProfile? = null): Boolean {
        val targetProfile = profile ?: ModelConfig.defaultProfile
        return storageDir().usableSpace >= ModelConfig.requiredFreeSpaceBytes(targetProfile)
    }

    suspend fun reconcileInstallState() {
        val canonical = modelFile()
        val validInstalled = when {
            validateModelFile(canonical) -> canonical
            else -> migrateLegacyModelFile(canonical)
        }
        settingsStore.update { current ->
            if (validInstalled != null) {
                val profile = ModelConfig.profileFor(current.selectedLocalModelId)
                current.copy(
                    localModelInstalled = true,
                    localModelVersion = if (current.localModelVersion.isNotBlank()) current.localModelVersion else "${profile.displayName} (${ModelConfig.version})",
                    localModelPath = validInstalled.absolutePath,
                    localModelDownloadComplete = true,
                    localModelDownloadInProgress = false
                )
            } else {
                current.copy(
                    localModelInstalled = false,
                    localModelVersion = "",
                    localModelPath = "",
                    localModelDownloadComplete = false,
                    localModelDownloadInProgress = false,
                    localModelDownloadMessage = if (current.localModelDownloadMessage.contains("downloaded successfully", ignoreCase = true)) {
                        "Local model file is missing. Download the selected model again."
                    } else {
                        current.localModelDownloadMessage
                    }
                )
            }
        }
    }

    suspend fun markInstalled(path: String) {
        val profile = ModelConfig.profileFor(settingsStore.settings.first().selectedLocalModelId)
        settingsStore.update {
            it.copy(
                localModelInstalled = true,
                localModelVersion = "${profile.displayName} (${ModelConfig.version})",
                localModelPath = path,
                localModelDownloadMessage = "${profile.displayName} downloaded successfully.",
                localModelDownloadComplete = true,
                localModelDownloadInProgress = false
            )
        }
    }

    suspend fun markDownloadState(inProgress: Boolean, message: String? = null) {
        settingsStore.update {
            it.copy(
                localModelDownloadInProgress = inProgress,
                localModelDownloadMessage = message ?: it.localModelDownloadMessage
            )
        }
    }

    suspend fun setDownloadMessage(message: String) {
        settingsStore.update {
            it.copy(localModelDownloadMessage = message)
        }
    }

    suspend fun clearInstalled() {
        storageDir().listFiles()?.forEach { file ->
            if (
                file.name.endsWith(".litertlm") ||
                file.name.endsWith(".litertlm.part")
            ) {
                file.delete()
            }
        }
        settingsStore.update {
            it.copy(
                localModelInstalled = false,
                localModelVersion = "",
                localModelPath = "",
                localModelDownloadMessage = "",
                localModelDownloadComplete = false,
                localModelDownloadInProgress = false
            )
        }
    }

    private fun migrateLegacyModelFile(canonical: File): File? {
        val legacy = storageDir().listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.name.endsWith(".litertlm") &&
                    validateModelFile(file)
            }
            ?.maxByOrNull { it.length() }
            ?: return null
        if (legacy.absolutePath == canonical.absolutePath) return legacy
        return runCatching {
            if (canonical.exists()) {
                canonical.delete()
            }
            if (!legacy.renameTo(canonical)) {
                legacy.copyTo(canonical, overwrite = true)
                legacy.delete()
            }
            canonical
        }.getOrNull()
    }
}
