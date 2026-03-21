package com.charles.pocketassistant.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.charles.pocketassistant.data.datastore.SettingsStore
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * E2E tests for ImportScreen. Verifies the source selection cards,
 * paste text section, and privacy notice.
 */
@HiltAndroidTest
class ImportScreenTest {

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
        // Navigate to Import screen via FAB
        composeRule.onNodeWithContentDescription("Quick add").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun import_displaysTitle() {
        composeRule.onNodeWithText("Import").assertIsDisplayed()
    }

    @Test
    fun import_displaysHeadline() {
        composeRule.onNodeWithText("Choose a source to analyze").assertIsDisplayed()
    }

    @Test
    fun import_displaysSourceCards() {
        composeRule.onNodeWithText("Camera").assertIsDisplayed()
        composeRule.onNodeWithText("Gallery").assertIsDisplayed()
        composeRule.onNodeWithText("File").assertIsDisplayed()
    }

    @Test
    fun import_displaysSourceSubtitles() {
        composeRule.onNodeWithText("Take a photo").assertIsDisplayed()
        composeRule.onNodeWithText("Pick image").assertIsDisplayed()
        composeRule.onNodeWithText("PDF / doc").assertIsDisplayed()
    }

    @Test
    fun import_displaysPasteSection() {
        composeRule.onNodeWithText("Or paste text").assertIsDisplayed()
        composeRule.onNodeWithText("Paste bill text, notes, or messages").assertIsDisplayed()
    }

    @Test
    fun import_processButton_disabledWhenEmpty() {
        composeRule.onNodeWithText("Process text").assertIsNotEnabled()
    }

    @Test
    fun import_privacyNotice_displayed() {
        composeRule.onNodeWithText("Processing happens locally unless your AI mode routes to Ollama.", substring = true).assertIsDisplayed()
    }
}
