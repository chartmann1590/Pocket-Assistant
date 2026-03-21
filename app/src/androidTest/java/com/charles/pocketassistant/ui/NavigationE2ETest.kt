package com.charles.pocketassistant.ui

import androidx.compose.ui.test.assertIsDisplayed
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
 * Full navigation E2E test. Walks through the entire app flow:
 * Home -> Import -> Back -> Tasks -> Back -> Assistant -> Back -> Settings -> Back
 * Also tests Home -> Item Detail -> Back round-trip with demo data.
 */
@HiltAndroidTest
class NavigationE2ETest {

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
    }

    @Test
    fun fullNavigation_homeToImportAndBack() {
        // Start on Home
        composeRule.onNodeWithText("Pocket Assistant").assertIsDisplayed()

        // Navigate to Import via FAB
        composeRule.onNodeWithContentDescription("Quick add").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Choose a source to analyze").assertIsDisplayed()

        // Back to Home (press system back)
        composeRule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pocket Assistant").assertIsDisplayed()
    }

    @Test
    fun fullNavigation_homeToTasksViaQuickAction() {
        composeRule.onNodeWithText("Tasks").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun fullNavigation_homeToAssistantAndBack() {
        composeRule.onNodeWithContentDescription("Assistant").performClick()
        composeRule.waitForIdle()
        // Verify we're on the Assistant screen (title is always visible)
        composeRule.onNodeWithText("Assistant").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pocket Assistant").assertIsDisplayed()
    }

    @Test
    fun fullNavigation_homeToSettingsViaIcon() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("AI Mode").assertIsDisplayed()
        composeRule.onNodeWithText("Local Model").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun fullNavigation_homeToDetailAndBack() {
        // Click on a demo item (Bill)
        composeRule.onNodeWithText("Bill").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Item Detail").assertIsDisplayed()
        composeRule.onNodeWithText("AI Summary").assertIsDisplayed()

        // Navigate back
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pocket Assistant").assertIsDisplayed()
    }

    @Test
    fun fullNavigation_homeToAskAI() {
        composeRule.onNodeWithText("Ask AI").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Assistant").assertIsDisplayed()
    }

    @Test
    fun fullNavigation_filterChipsWorkCorrectly() {
        // All filter should show all items
        composeRule.onNodeWithText("All").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bill").assertIsDisplayed()

        // Bills filter
        composeRule.onNodeWithText("Bills").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Bill").assertIsDisplayed()

        // Back to All
        composeRule.onNodeWithText("All").performClick()
        composeRule.waitForIdle()
    }
}
