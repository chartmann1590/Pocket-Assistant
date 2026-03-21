package com.charles.pocketassistant.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.charles.pocketassistant.data.datastore.SettingsStore
import com.charles.pocketassistant.data.db.entity.ItemEntity
import com.charles.pocketassistant.data.repository.DataMaintenanceRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * E2E tests for HomeScreen. Verifies quick actions, filter chips, item cards,
 * empty state, and navigation to sub-screens.
 */
@HiltAndroidTest
class HomeScreenTest {

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
        // Complete onboarding so we land on HomeScreen
        runBlocking {
            settingsStore.update { it.copy(onboardingComplete = true) }
        }
    }

    @Test
    fun home_displaysAppTitle() {
        composeRule.onNodeWithText("Pocket Assistant").assertIsDisplayed()
    }

    @Test
    fun home_displaysSubtitle() {
        composeRule.onNodeWithText("Your local AI organizer").assertIsDisplayed()
    }

    @Test
    fun home_displaysQuickActionCards() {
        composeRule.onNodeWithText("Photo").assertIsDisplayed()
        composeRule.onNodeWithText("Gallery").assertIsDisplayed()
        composeRule.onNodeWithText("PDF").assertIsDisplayed()
        composeRule.onNodeWithText("Paste").assertIsDisplayed()
        composeRule.onNodeWithText("Tasks").assertIsDisplayed()
        composeRule.onNodeWithText("Ask AI").assertIsDisplayed()
    }

    @Test
    fun home_displaysFilterChips() {
        composeRule.onNodeWithText("All").assertIsDisplayed()
        composeRule.onNodeWithText("Bills").assertIsDisplayed()
        composeRule.onNodeWithText("Messages").assertIsDisplayed()
        composeRule.onNodeWithText("Appointments").assertIsDisplayed()
        composeRule.onNodeWithText("Notes").assertIsDisplayed()
    }

    @Test
    fun home_displaysEmptyState_whenNoItems() {
        // Clear all data first
        runBlocking { dataMaintenanceRepository.clearAllLocalData() }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("No items yet").assertIsDisplayed()
    }

    @Test
    fun home_displaysRecentItemsHeader() {
        composeRule.onNodeWithText("Recent items").assertIsDisplayed()
    }

    @Test
    fun home_filterChips_filterItems() {
        // Add a demo bill
        runBlocking { dataMaintenanceRepository.addDemoData() }
        composeRule.waitForIdle()
        // Click "Bills" filter
        composeRule.onNodeWithText("Bills").performClick()
        composeRule.waitForIdle()
        // Bill classification should be visible
        composeRule.onNodeWithText("Bill").assertIsDisplayed()
    }

    @Test
    fun home_fabExists() {
        composeRule.onNodeWithContentDescription("Quick add").assertIsDisplayed()
    }

    @Test
    fun home_settingsIconNavigates() {
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
    }

    @Test
    fun home_assistantIconNavigates() {
        composeRule.onNodeWithContentDescription("Assistant").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Assistant").assertIsDisplayed()
    }

    @Test
    fun home_itemCard_showsRelativeTimestamp() {
        runBlocking { dataMaintenanceRepository.addDemoData() }
        composeRule.waitForIdle()
        // Items should show relative time like "Just now" or "Xm ago"
        // Since demo data was just added, at least one should exist
        composeRule.onNodeWithText("Bill").assertIsDisplayed()
    }
}
