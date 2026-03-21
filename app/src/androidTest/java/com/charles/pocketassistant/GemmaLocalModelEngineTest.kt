package com.charles.pocketassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.charles.pocketassistant.ai.local.LocalLlmEngine
import com.charles.pocketassistant.ai.local.LocalModelManager
import com.charles.pocketassistant.data.datastore.AiMode
import com.charles.pocketassistant.data.datastore.SettingsStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates the on-device LiteRT path with Gemma selected — no Compose/Espresso (avoids Android 15+
 * InputManager/Espresso idle issues on some devices).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@LargeTest
class GemmaLocalModelEngineTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var localModelManager: LocalModelManager

    @Inject
    lateinit var localLlmEngine: LocalLlmEngine

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun gemma_engine_selfTest_reportsOk_whenModelInstalled() = runBlocking {
        assumeTrue(
            "Install the local Gemma 3n E2B model on device first.",
            localModelManager.isModelInstalled()
        )
        settingsStore.update {
            it.copy(
                selectedLocalModelId = GEMMA_PROFILE_ID,
                aiMode = AiMode.LOCAL
            )
        }
        val result = localLlmEngine.selfTest()
        assertTrue("Expected success line, got: $result", result.contains("Local model OK", ignoreCase = true))
    }

    private companion object {
        const val GEMMA_PROFILE_ID = "gemma3n_e2b"
    }
}
