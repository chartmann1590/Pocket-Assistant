package com.charles.pocketassistant.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.charles.pocketassistant.data.datastore.SettingsStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * E2E tests for SettingsScreen. Verifies section headers, AI mode selector,
 * local model section, Ollama section, debug section, and privacy section.
 */
@HiltAndroidTest
class SettingsScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsStore: SettingsStore

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            settingsStore.update { it.copy(onboardingComplete = true) }
        }
        // Navigate to Settings
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun settings_displaysTitle() {
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun settings_displaysAiModeSection() {
        composeRule.onNodeWithText("AI Mode").assertIsDisplayed()
    }

    @Test
    fun settings_displaysAiModeSegmentedButtons() {
        composeRule.onNodeWithText("Local").assertIsDisplayed()
        composeRule.onNodeWithText("Ollama").assertIsDisplayed()
        composeRule.onNodeWithText("Auto").assertIsDisplayed()
    }

    @Test
    fun settings_displaysLocalModelSection() {
        composeRule.onNodeWithText("Local Model").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_displaysOllamaSection() {
        composeRule.onNodeWithText("Ollama Server").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_displaysDebugSection() {
        composeRule.onNodeWithText("Debug").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_displaysLocalDataSection() {
        composeRule.onNodeWithText("Local Data").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_displaysPrivacySection() {
        composeRule.onNodeWithText("Privacy").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("OCR and local AI run on-device").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_displaysAboutAndSupportSection() {
        // Scroll toward the bottom (Privacy sits just above About & Support).
        composeRule.onNodeWithText("Privacy").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("About & Support").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("About & Support").assertIsDisplayed()
        composeRule.onNodeWithText("GitHub Repository").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("GitHub Repository").assertIsDisplayed()
        composeRule.onNodeWithText("Sponsor / Buy Me a Coffee").performScrollTo()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Sponsor / Buy Me a Coffee").assertIsDisplayed()
    }

    @Test
    fun settings_aiModeSelector_switchesToOllama() {
        composeRule.onNodeWithText("Ollama").performClick()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("All processing sent to your Ollama server."))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("All processing sent to your Ollama server.").assertIsDisplayed()
    }

    @Test
    fun settings_aiModeSelector_switchesToAuto() {
        composeRule.onNodeWithText("Auto").performClick()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Uses local for light tasks, Ollama for complex ones."))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Uses local for light tasks, Ollama for complex ones.").assertIsDisplayed()
    }

    @Test
    fun settings_aiModeSelector_switchesToLocal() {
        // First switch away from Local, then back, to ensure state change
        composeRule.onNodeWithText("Ollama").performClick()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("All processing sent to your Ollama server."))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Local").performClick()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("All processing happens on-device."))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("All processing happens on-device.").assertIsDisplayed()
    }

    @Test
    fun settings_downloadButton_exists() {
        composeRule.onNodeWithText("Download").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_deleteButton_exists() {
        composeRule.onNodeWithText("Delete").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_selfTestButton_exists() {
        composeRule.onNodeWithText("Self-test local model").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_ollamaBaseUrlInput_exists() {
        composeRule.onNodeWithText("Base URL").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_testConnectionButton_exists() {
        composeRule.onNodeWithText("Test connection").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun settings_demoDataButtons_exist() {
        composeRule.onNodeWithText("Add demo items").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Clear database").performScrollTo().assertIsDisplayed()
    }
}
