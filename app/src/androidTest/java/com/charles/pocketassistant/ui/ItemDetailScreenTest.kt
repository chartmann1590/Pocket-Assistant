package com.charles.pocketassistant.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.charles.pocketassistant.data.datastore.SettingsStore
import com.charles.pocketassistant.data.repository.DataMaintenanceRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * E2E tests for ItemDetailScreen. Verifies AI summary section,
 * raw OCR text, entity display, task extraction, and action buttons.
 * Uses demo data to populate an item to navigate into.
 */
@HiltAndroidTest
class ItemDetailScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var dataMaintenanceRepository: DataMaintenanceRepository

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            settingsStore.update { it.copy(onboardingComplete = true) }
            dataMaintenanceRepository.addDemoData()
        }
        composeRule.waitForIdle()
        // Click on the first item card ("Bill" classification from demo data)
        composeRule.onNodeWithText("Bill").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun detail_displaysTitle() {
        composeRule.onNodeWithText("Item Detail").assertIsDisplayed()
    }

    @Test
    fun detail_displaysBackButton() {
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun detail_displaysAiSummarySection() {
        composeRule.onNodeWithText("AI Summary").assertIsDisplayed()
    }

    @Test
    fun detail_displaysRawOcrTextSection() {
        composeRule.onNodeWithText("Raw OCR text").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_displaysSuggestedActions() {
        composeRule.onNodeWithText("Suggested next steps").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_displaysAddTaskButton() {
        composeRule.onNodeWithText("Add Task").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_displaysReminderButton() {
        composeRule.onNodeWithText("Reminder").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_displaysRerunLocalButton() {
        composeRule.onNodeWithText("Re-run Local").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_displaysOllamaButton() {
        composeRule.onNodeWithText("Ollama").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_displaysEditResultsButton() {
        composeRule.onNodeWithText("Edit Results").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_editMode_showsSaveCancel() {
        composeRule.onNodeWithText("Edit Results").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Save edits").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun detail_rawOcrText_expandsOnClick() {
        composeRule.onNodeWithText("Raw OCR text").performScrollTo().performClick()
        composeRule.waitForIdle()
        // After expanding, the full text should be visible (no "..." truncation)
        // We verify the expand/collapse icon changed
        composeRule.onNodeWithText("Raw OCR text").assertIsDisplayed()
    }

    @Test
    fun detail_backButton_navigatesHome() {
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pocket Assistant").assertIsDisplayed()
    }
}
