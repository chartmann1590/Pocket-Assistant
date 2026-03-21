package com.charles.pocketassistant.ai.local

import android.util.Log
import com.charles.pocketassistant.data.datastore.SettingsStore
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request

data class LocalModelDownloadState(
    val inProgress: Boolean = false,
    val progress: Int = 0,
    val message: String = "",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L
)

@Singleton
class LocalModelDownloadManager @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val localModelManager: LocalModelManager,
    private val settingsStore: SettingsStore
) {
    private val tag = "ModelDownloadManager"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(LocalModelDownloadState())
    val state: StateFlow<LocalModelDownloadState> = _state.asStateFlow()

    private val downloadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .readTimeout(60, TimeUnit.SECONDS)
            .connectTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private var activeJob: Job? = null
    private var lastReportedProgress = -1
    private var lastProgressUpdateAt = 0L

    init {
        scope.launch {
            val current = settingsStore.settings.first()
            if (current.localModelDownloadInProgress) {
                val message = when {
                    current.localModelDownloadMessage.contains("downloaded successfully", ignoreCase = true) ->
                        current.localModelDownloadMessage
                    current.localModelDownloadMessage.isNotBlank() ->
                        "Download paused. Tap Download to resume."
                    else -> ""
                }
                localModelManager.markDownloadState(inProgress = false, message = message)
            }
        }
    }

    fun startOrResume() {
        if (activeJob?.isActive == true) return
        activeJob = scope.launch {
            runDownloadWithRetry()
        }
    }

    suspend fun cancelAndDelete() {
        activeJob?.cancel()
        activeJob = null
        _state.value = LocalModelDownloadState()
        localModelManager.clearInstalled()
    }

    private suspend fun runDownloadWithRetry() {
        var attempt = 0
        while (attempt <= MAX_RETRIES) {
            try {
                runDownload()
                return // success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (e: Exception) {
                val errorMsg = e.message ?: "Model download failed."
                if (attempt < MAX_RETRIES && isRetryable(errorMsg)) {
                    attempt++
                    val delaySec = attempt * 3L
                    Log.w(tag, "Download attempt $attempt failed, retrying in ${delaySec}s: $errorMsg", e)
                    publishState(
                        inProgress = true,
                        progress = _state.value.progress,
                        message = "Connection lost. Retrying in ${delaySec}s... (attempt ${attempt + 1}/${MAX_RETRIES + 1})",
                        downloadedBytes = _state.value.downloadedBytes,
                        totalBytes = _state.value.totalBytes
                    )
                    delay(delaySec * 1000)
                } else {
                    Log.e(tag, "Download failed permanently after ${attempt + 1} attempt(s)", e)
                    publishFailure(normalizeErrorMessage(errorMsg))
                    return
                }
            }
        }
    }

    private suspend fun runDownload() {
        val settings = settingsStore.settings.first()
        val profile = ModelConfig.profileFor(settings.selectedLocalModelId)
        if (!localModelManager.hasEnoughStorage(profile)) {
            throw IOException("Not enough free space to download ${profile.displayName}.")
        }
        val token = settings.modelDownloadAuthToken.trim()
        if (profile.requiresAuthToken && token.isBlank()) {
            throw IOException("Enter a Hugging Face access token that can download ${profile.displayName} before starting.")
        }

        val output = localModelManager.modelFile()
        val temp = File(output.absolutePath + ".part")
        temp.parentFile?.mkdirs()

        lastReportedProgress = -1
        lastProgressUpdateAt = 0L

        val existingBytes = temp.takeIf { it.exists() }?.length() ?: 0L
        if (existingBytes > 0L) {
            publishState(
                inProgress = true,
                progress = if (_state.value.totalBytes > 0) {
                    ((existingBytes * 100) / _state.value.totalBytes).toInt().coerceIn(0, 99)
                } else 0,
                message = "Resuming ${profile.displayName} download... ${existingBytes / MB} MB already saved",
                downloadedBytes = existingBytes
            )
        } else {
            publishState(
                inProgress = true,
                progress = 0,
                message = "Connecting to Hugging Face for ${profile.displayName}..."
            )
        }

        val requestBuilder = Request.Builder()
            .url(profile.remoteUrl)
            .header("User-Agent", "PocketAssistant/Android")
            .header("Accept", "application/octet-stream")
        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }
        if (token.isNotBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        downloadClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 416 && existingBytes > 0L && localModelManager.validateModelFile(temp)) {
                finalizeInstall(temp, output, profile)
                return
            }
            if (!response.isSuccessful) {
                val details = response.peekBody(256 * 1024).string()
                    .replace(Regex("\\s+"), " ")
                    .take(180)
                val message = buildString {
                    append("Download failed with HTTP ${response.code}.")
                    if (details.isNotBlank()) {
                        append(" ")
                        append(details)
                    }
                }
                throw IOException(message)
            }

            val body = response.body ?: throw IOException("Empty model download response.")
            val appending = existingBytes > 0L && response.code == HttpURLConnection.HTTP_PARTIAL
            if (existingBytes > 0L && !appending && response.code == HttpURLConnection.HTTP_OK) {
                temp.delete()
            }
            val totalBytes = when {
                appending && body.contentLength() > 0 -> existingBytes + body.contentLength()
                else -> body.contentLength()
            }

            body.byteStream().use { input ->
                FileOutputStream(temp, appending).buffered().use { outputStream ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = if (appending) existingBytes else 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        outputStream.write(buffer, 0, read)
                        downloaded += read
                        updateProgress(profile, downloaded, totalBytes)
                    }
                }
            }
        }

        if (!localModelManager.validateModelFile(temp)) {
            throw IOException("Installed model file is invalid.")
        }
        finalizeInstall(temp, output, profile)
    }

    private suspend fun finalizeInstall(temp: File, output: File, profile: LocalModelProfile) {
        publishState(
            inProgress = true,
            progress = 99,
            message = "Finalizing ${profile.displayName}..."
        )
        if (output.exists()) output.delete()
        if (!temp.renameTo(output)) {
            temp.copyTo(output, overwrite = true)
            temp.delete()
        }
        localModelManager.markInstalled(output.absolutePath)
        publishState(
            inProgress = false,
            progress = 100,
            message = "${profile.displayName} downloaded successfully.",
            downloadedBytes = output.length(),
            totalBytes = output.length()
        )
    }

    private suspend fun updateProgress(profile: LocalModelProfile, downloaded: Long, total: Long) {
        val progress = if (total > 0) {
            ((downloaded * 100) / total).toInt().coerceIn(0, 99)
        } else {
            0
        }
        val now = System.currentTimeMillis()
        val downloadedMb = downloaded / MB
        val totalMb = if (total > 0) total / MB else 0L
        val shouldReport = progress >= 99 ||
            lastReportedProgress < 0 ||
            (progress == 0 && downloadedMb >= 1 && now - lastProgressUpdateAt >= PROGRESS_UPDATE_INTERVAL_MS) ||
            (progress >= lastReportedProgress + 1 && now - lastProgressUpdateAt >= PROGRESS_UPDATE_INTERVAL_MS)
        if (!shouldReport) return
        lastReportedProgress = progress
        lastProgressUpdateAt = now
        val message = if (totalMb > 0L) {
            "Downloading ${profile.displayName}... ${downloadedMb} MB / ${totalMb} MB"
        } else {
            "Downloading ${profile.displayName}... ${downloadedMb} MB"
        }
        publishState(
            inProgress = true,
            progress = progress,
            message = message,
            downloadedBytes = downloaded,
            totalBytes = total
        )
    }

    private suspend fun publishFailure(message: String) {
        publishState(
            inProgress = false,
            progress = _state.value.progress,
            message = message,
            downloadedBytes = _state.value.downloadedBytes,
            totalBytes = _state.value.totalBytes
        )
    }

    private suspend fun publishState(
        inProgress: Boolean,
        progress: Int,
        message: String,
        downloadedBytes: Long = _state.value.downloadedBytes,
        totalBytes: Long = _state.value.totalBytes
    ) {
        _state.value = LocalModelDownloadState(
            inProgress = inProgress,
            progress = progress,
            message = message,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes
        )
        localModelManager.markDownloadState(inProgress = inProgress, message = message)
    }

    private fun isRetryable(message: String): Boolean {
        return message.contains("timed out", ignoreCase = true) ||
            message.contains("timeout", ignoreCase = true) ||
            message.contains("reset", ignoreCase = true) ||
            message.contains("broken pipe", ignoreCase = true) ||
            message.contains("connection abort", ignoreCase = true) ||
            message.contains("unexpected end of stream", ignoreCase = true) ||
            message.contains("failed to connect", ignoreCase = true) ||
            message.contains("stream was reset", ignoreCase = true)
    }

    private fun normalizeErrorMessage(message: String): String {
        return when {
            message.equals("timeout", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) ->
                "The model download timed out. Check the connection and try again, preferably on stable Wi-Fi."
            message.contains("HTTP ${HttpURLConnection.HTTP_UNAUTHORIZED}") ->
                "Hugging Face rejected the token. Use a valid user access token."
            message.contains("public gated repositories", ignoreCase = true) ->
                "Token lacks gated-model access. Use a Hugging Face Read token, or for a fine-grained token enable access to public gated repos."
            message.contains("HTTP ${HttpURLConnection.HTTP_FORBIDDEN}") ->
                "Access to the selected model was denied. Make sure the account has access and the token can read that repository."
            else -> message
        }
    }

    private companion object {
        private const val PROGRESS_UPDATE_INTERVAL_MS = 1_000L
        private const val MB = 1024L * 1024L
        private const val MAX_RETRIES = 3
    }
}
