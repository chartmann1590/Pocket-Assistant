package com.charles.pocketassistant.data.repository

import android.content.Context
import android.content.pm.PackageManager
import com.charles.pocketassistant.data.datastore.SettingsStore
import com.charles.pocketassistant.data.remote.github.GitHubRelease
import com.charles.pocketassistant.data.remote.github.GitHubReleasesApi
import com.charles.pocketassistant.domain.LatestRelease
import com.charles.pocketassistant.domain.UpdateDecision
import com.charles.pocketassistant.util.UpdateDecider
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val okHttpClient: OkHttpClient
) {
    private val _available = MutableStateFlow<UpdateDecision.Available?>(null)
    val available: StateFlow<UpdateDecision.Available?> = _available.asStateFlow()

    private val api: GitHubReleasesApi = run {
        val json = Json { ignoreUnknownKeys = true }
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubReleasesApi::class.java)
    }

    suspend fun checkForUpdate(force: Boolean = false): UpdateDecision = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val prefs = settingsStore.updatePrefs()
        if (!force && !UpdateDecider.shouldCheck(now, prefs.lastCheckMillis)) {
            return@withContext UpdateDecision.CheckSkipped
        }
        val release = try {
            api.latest(OWNER, REPO).toDomain()
        } catch (t: Throwable) {
            return@withContext UpdateDecision.CheckSkipped
        }
        settingsStore.setLastUpdateCheck(now)
        val installMillis = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
        }.getOrElse { 0L }
        val decision = UpdateDecider.decide(
            release = release,
            installLastUpdateMillis = installMillis,
            dismissedTag = prefs.dismissedTag
        )
        _available.value = decision as? UpdateDecision.Available
        decision
    }

    suspend fun dismiss(tag: String) {
        settingsStore.setDismissedUpdateTag(tag)
        _available.value = null
    }

    companion object {
        private const val OWNER = "chartmann1590"
        private const val REPO = "Pocket-Assistant"
        private const val APK_ASSET_NAME = "app-debug.apk"

        internal fun GitHubRelease.toDomain(): LatestRelease {
            val asset = assets.firstOrNull { it.name == APK_ASSET_NAME }
                ?: assets.firstOrNull { it.name.endsWith(".apk") }
            val publishedMillis = parseIsoMillis(publishedAt)
            return LatestRelease(
                tag = tagName,
                name = name.ifBlank { tagName },
                htmlUrl = htmlUrl,
                publishedAtMillis = publishedMillis,
                apkAssetUrl = asset?.browserDownloadUrl.orEmpty(),
                apkSizeBytes = asset?.size ?: 0L,
                notes = body
            )
        }

        private fun parseIsoMillis(iso: String): Long = runCatching {
            if (iso.isBlank()) 0L
            else OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .toInstant()
                .toEpochMilli()
        }.getOrElse { 0L }
    }
}
