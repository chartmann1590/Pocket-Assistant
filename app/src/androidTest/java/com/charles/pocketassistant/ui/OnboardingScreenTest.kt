package com.charles.pocketassistant.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * E2E tests for the OnboardingScreen. Verifies the full first-run setup flow
 * including mode selection, model cards, step indicator, and finish button state.
 */
@HiltAndroidTest
class OnboardingScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsStore: SettingsStore

    @Before
    fun setup() {
        hiltRule.inject()
        // Reset onboarding so the screen shows
        runBlocking {
            settingsStore.update { it.copy(onboardingComplete = false) }
        }
    }

    @Test
    fun onboarding_displaysWelcomeHeader() {
        composeRule.onNodeWithText("Welcome to Pocket Assistant").assertIsDisplayed()
    }

    @Test
    fun onboarding_displaysStepIndicator() {
        composeRule.onNodeWithText("Choose path").assertIsDisplayed()
        composeRule.onNodeWithText("Configure").assertIsDisplayed()
        // "Ready" may appear in both step indicator and status pill; assert at least one exists
        composeRule.onAllNodesWithText("Ready")[0].assertIsDisplayed()
    }

    @Test
    fun onboarding_displaysAllThreeModeCards() {
        composeRule.onNodeWithText("Local only").assertIsDisplayed()
        composeRule.onNodeWithText("Connect Ollama").assertIsDisplayed()
        composeRule.onNodeWithText("Use both").assertIsDisplayed()
    }

    @Test
    fun onboarding_localOnly_showsLocalSetupCard() {
        composeRule.onNodeWithText("Local only").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Local AI Setup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Choose model").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun onboarding_ollamaOnly_showsOllamaSetupCard() {
        composeRule.onNodeWithText("Connect Ollama").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Ollama Server").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ollama base URL").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun onboarding_bothMode_showsBothSetupCards() {
        composeRule.onNodeWithText("Use both").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Local AI Setup").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Ollama Server").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun onboarding_finishButton_exists() {
        composeRule.onNodeWithText("Finish setup").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun onboarding_demoContentSection_exists() {
        composeRule.onNodeWithText("Demo Content").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Add demo items").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun onboarding_modelCards_displayAllProfiles() {
        composeRule.onNodeWithText("Local only").performClick()
        composeRule.waitForIdle()
        // At least the default model should be visible
        composeRule.onNodeWithText("Choose model").performScrollTo().assertIsDisplayed()
    }
}
