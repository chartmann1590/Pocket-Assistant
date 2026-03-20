package com.charles.pocketassistant.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.charles.pocketassistant.ai.local.LocalModelManager
import com.charles.pocketassistant.ai.local.LocalModelProfile
import com.charles.pocketassistant.ai.local.ModelConfig
import com.charles.pocketassistant.data.datastore.SettingsStore
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.HttpURLConnection
import java.net.SocketException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Dns

@HiltWorker
class ModelDownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val okHttpClient: OkHttpClient,
    private val localModelManager: LocalModelManager,
    private val settingsStore: SettingsStore
) : CoroutineWorker(appContext, params) {
    private val tag = "ModelDownloadWorker"
    private var lastReportedProgress = -1
    private var lastProgressUpdateAt = 0L

    override suspend fun doWork(): Result {
        val settings = settingsStore.settings.first()
        val profile = ModelConfig.profileFor(settings.selectedLocalModelId)
        if (!localModelManager.hasEnoughStorage(profile)) {
            return failureResult("Not enough free space to download ${profile.displayName}.")
        }
        val output = localModelManager.modelFile()
        val temp = File(output.absolutePath + ".part")
        return try {
            setForeground(createForegroundInfo(0))
            val preparingMessage = "Preparing ${profile.displayName} download..."
            localModelManager.markDownloadState(inProgress = true, message = preparingMessage)
            setProgress(workDataOf(KEY_PROGRESS to 0, KEY_MESSAGE to preparingMessage))
            temp.parentFile?.mkdirs()
            val connectMessage = "Connecting to Hugging Face for ${profile.displayName}..."
            localModelManager.setDownloadMessage(connectMessage)
            setProgress(workDataOf(KEY_PROGRESS to 0, KEY_MESSAGE to connectMessage))
            val token = settings.modelDownloadAuthToken.trim()
            val startTransferMessage = "Starting ${profile.displayName} transfer..."
            localModelManager.setDownloadMessage(startTransferMessage)
            setProgress(workDataOf(KEY_PROGRESS to 0, KEY_MESSAGE to startTransferMessage))
            downloadRemoteModel(temp, profile, token)
            if (!localModelManager.validateModelFile(temp)) {
                temp.delete()
                return failureResult("Installed model file is invalid.")
            }
            localModelManager.setDownloadMessage("Finalizing ${profile.displayName}...")
            setProgress(workDataOf(KEY_PROGRESS to 99, KEY_MESSAGE to "Finalizing ${profile.displayName}..."))
            if (output.exists()) output.delete()
            if (!temp.renameTo(output)) {
                temp.copyTo(output, overwrite = true)
                temp.delete()
            }
            localModelManager.markInstalled(output.absolutePath)
            val message = "${profile.displayName} downloaded successfully."
            setProgress(workDataOf(KEY_PROGRESS to 100, KEY_MESSAGE to message))
            Result.success(workDataOf(KEY_PROGRESS to 100, KEY_MESSAGE to message))
        } catch (e: Exception) {
            val errorMessage = normalizeErrorMessage(e.message ?: "Model download failed.")
            Log.e(tag, "Model download failed on attempt ${runAttemptCount + 1}", e)
            if (shouldRetry(errorMessage)) {
                localModelManager.setDownloadMessage("Retrying download after error: $errorMessage")
                Result.retry()
            } else {
                failureResult(errorMessage)
            }
        } finally {
            localModelManager.markDownloadState(inProgress = false)
        }
    }

    private suspend fun downloadRemoteModel(
        temp: File,
        profile: LocalModelProfile,
        token: String
    ) {
        if (profile.requiresAuthToken && token.isBlank()) {
            throw IOException("Hugging Face token required before downloading ${profile.displayName}.")
        }
        val existingBytes = temp.takeIf { it.exists() }?.length() ?: 0L
        if (existingBytes > 0L) {
            val resumeMessage = "Resuming ${profile.displayName} download... ${existingBytes / (1024L * 1024L)} MB already saved"
            localModelManager.setDownloadMessage(resumeMessage)
            setProgress(workDataOf(KEY_PROGRESS to 0, KEY_MESSAGE to resumeMessage))
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
        val request = requestBuilder.build()
        Log.d(tag, "Starting download request for ${profile.displayName}")
        executeDownloadRequest(request, profile).use { response ->
            if (response.code == 416 && existingBytes > 0L && localModelManager.validateModelFile(temp)) {
                Log.d(tag, "Range already satisfied for ${profile.displayName}; using existing partial as complete file")
                return
            }
            if (!response.isSuccessful) {
                val details = response.peekBody(512 * 1024).string()
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
            val total = when {
                appending && body.contentLength() > 0 -> existingBytes + body.contentLength()
                else -> body.contentLength()
            }
            Log.d(tag, "Download response for ${profile.displayName}: code=${response.code}, bytes=$total, existing=$existingBytes")
            withContext(Dispatchers.IO) {
                body.byteStream().use { input ->
                    FileOutputStream(temp, appending).buffered().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var downloaded = if (appending) existingBytes else 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            downloaded += read
                            updateProgress(downloaded, total)
                        }
                    }
                }
            }
        }
    }

    private suspend fun executeDownloadRequest(
        request: Request,
        profile: LocalModelProfile
    ) = try {
        preferredDownloadClient()?.newCall(request)?.execute() ?: okHttpClient.newCall(request).execute()
    } catch (e: IOException) {
        if (isNetworkBindingPermissionError(e)) {
            Log.w(tag, "Network binding failed for ${profile.displayName}; retrying on default route.", e)
            val message = "VPN-controlled network detected. Retrying ${profile.displayName} on the default route..."
            localModelManager.setDownloadMessage(message)
            setProgress(workDataOf(KEY_PROGRESS to 0, KEY_MESSAGE to message))
            okHttpClient.newCall(request).execute()
        } else {
            throw e
        }
    }

    private fun preferredDownloadClient(): OkHttpClient? {
        val network = selectValidatedDownloadNetwork() ?: return null
        Log.d(tag, "Binding model download to ${describeNetwork(network)}")
        return okHttpClient.newBuilder()
            .socketFactory(network.socketFactory)
            .dns(NetworkBoundDns(network))
            .build()
    }

    private fun selectValidatedDownloadNetwork(): Network? {
        val connectivityManager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return null
        val candidates = connectivityManager.allNetworks.mapNotNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return@mapNotNull null
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return@mapNotNull null
            if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return@mapNotNull null
            if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
            network to capabilities
        }
        val wifi = candidates.firstOrNull { (_, caps) -> caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) }
        if (wifi != null) return wifi.first
        val cellular = candidates.firstOrNull { (_, caps) -> caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) }
        return cellular?.first ?: candidates.firstOrNull()?.first
    }

    private fun describeNetwork(network: Network): String {
        val connectivityManager =
            applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return "selected network"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "selected network"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "validated Wi-Fi network"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "validated cellular network"
            else -> "validated network"
        }
    }

    private suspend fun updateProgress(downloaded: Long, total: Long) {
        val profile = ModelConfig.profileFor(settingsStore.settings.first().selectedLocalModelId)
        val progress = if (total > 0) {
            ((downloaded * 100) / total).toInt().coerceIn(0, 99)
        } else {
            0
        }
        val now = System.currentTimeMillis()
        val downloadedMb = downloaded / (1024L * 1024L)
        val totalMb = if (total > 0) total / (1024L * 1024L) else 0L
        val shouldReport = progress >= 99 ||
            lastReportedProgress < 0 ||
            progress == 0 && downloadedMb >= 1 && now - lastProgressUpdateAt >= PROGRESS_UPDATE_INTERVAL_MS ||
            progress >= lastReportedProgress + 1 && now - lastProgressUpdateAt >= PROGRESS_UPDATE_INTERVAL_MS
        if (!shouldReport) return
        lastReportedProgress = progress
        lastProgressUpdateAt = now
        val message = if (totalMb > 0) {
            "Downloading ${profile.displayName}... ${downloadedMb} MB / ${totalMb} MB"
        } else {
            "Downloading ${profile.displayName}... ${downloadedMb} MB"
        }
        setProgress(workDataOf(KEY_PROGRESS to progress, KEY_MESSAGE to message))
        setForeground(createForegroundInfo(progress))
    }

    private fun failureResult(message: String): Result {
        Log.e(tag, message)
        return Result.failure(workDataOf(KEY_PROGRESS to 0, KEY_MESSAGE to message))
    }

    private fun shouldRetry(message: String): Boolean {
        return false
    }

    private fun isNetworkBindingPermissionError(error: Throwable): Boolean {
        return generateSequence(error) { it.cause }.any { cause ->
            cause is SocketException && cause.message.orEmpty().contains("EPERM", ignoreCase = true)
        }
    }

    private fun normalizeErrorMessage(message: String): String {
        return when {
            message.contains("EPERM", ignoreCase = true) ->
                "An active VPN on this phone is blocking direct model downloads. Pause the VPN and retry the download."
            message.contains("ForegroundServiceStartNotAllowedException", ignoreCase = true) ||
                message.contains("mAllowStartForeground false", ignoreCase = true) ->
                "Android blocked the background download start. Open Pocket Assistant and retry from Settings with the app left on screen."
            message.equals("timeout", ignoreCase = true) ||
                message.contains("timed out", ignoreCase = true) ->
                "The model download timed out. Check the connection and try again, preferably on stable Wi-Fi."
            message.contains("HTTP ${HttpURLConnection.HTTP_UNAUTHORIZED}") ->
                "Hugging Face rejected the token. Use a valid user access token."
            message.contains("public gated repositories", ignoreCase = true) ->
                "Token lacks gated-model access. Use a Hugging Face Read token, or for a fine-grained token enable 'Read access to contents of all public gated repos you can access'."
            message.contains("HTTP ${HttpURLConnection.HTTP_FORBIDDEN}") ->
                "Access to the selected model was denied. Make sure the account has access and the token can read that repository."
            message.contains("probe failed", ignoreCase = true) ->
                message.replace("Download probe failed", "Could not start the model download")
            else -> message
        }
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        val channelId = "model_download"
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Model Download", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setContentTitle("Downloading local AI model")
            .setContentText("$progress%")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .build()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(1001, notification, serviceType)
    }

    companion object {
        const val KEY_PROGRESS = "progress"
        const val KEY_MESSAGE = "message"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 1_000L
    }
}

private class NetworkBoundDns(
    private val network: Network
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> = network.getAllByName(hostname).toList()
}
