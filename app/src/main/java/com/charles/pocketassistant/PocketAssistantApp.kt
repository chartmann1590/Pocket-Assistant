package com.charles.pocketassistant

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.charles.pocketassistant.ads.AdManager
import com.charles.pocketassistant.ml.EntityExtractionEngine
import com.charles.pocketassistant.ml.NeuralEmbeddingEngine
import com.charles.pocketassistant.util.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class PocketAssistantApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var entityExtractionEngine: EntityExtractionEngine
    @Inject lateinit var neuralEmbeddingEngine: NeuralEmbeddingEngine
    @Inject lateinit var adManager: AdManager

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Create notification channels on startup
        notificationHelper.createChannels()
        // Initialize ads SDK (no-op if ADS_ENABLED=false)
        adManager.initialize()
        appScope.launch {
            // Pre-download ML Kit entity extraction model (~2MB, fast)
            entityExtractionEngine.ensureModelReady()
            // Initialize neural embeddings (downloads ~28MB USE model on first run)
            neuralEmbeddingEngine.tryInit()
        }
    }
}
