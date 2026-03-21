package com.charles.pocketassistant.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
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
 * E2E tests for AssistantScreen. Verifies the chat UI components,
 * intro message, input area, history button, and new chat button.
 */
@HiltAndroidTest
class AssistantScreenTest {

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
        // Navigate to Assistant
        composeRule.onNodeWithContentDescription("Assistant").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun assistant_displaysTitle() {
        composeRule.onNodeWithText("Assistant").assertIsDisplayed()
    }

    @Test
    fun assistant_displaysIntroMessage() {
        // New threads show an intro message bubble
        composeRule.onNodeWithContentDescription("New chat").performClick()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Ask about your recent items", substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ask about your recent items", substring = true).assertIsDisplayed()
    }

    @Test
    fun assistant_displaysMessageInput() {
        composeRule.onNodeWithText("Message").assertIsDisplayed()
    }

    @Test
    fun assistant_displaysSendButton() {
        composeRule.onNodeWithContentDescription("Send").assertIsDisplayed()
    }

    @Test
    fun assistant_displaysHistoryButton() {
        composeRule.onNodeWithContentDescription("History").assertIsDisplayed()
    }

    @Test
    fun assistant_displaysNewChatButton() {
        composeRule.onNodeWithContentDescription("New chat").assertIsDisplayed()
    }

    @Test
    fun assistant_displaysBackButton() {
        composeRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun assistant_historyOpensBottomSheet() {
        composeRule.onNodeWithContentDescription("History").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Chat history").assertIsDisplayed()
    }

    @Test
    fun assistant_backButton_navigatesHome() {
        composeRule.onNodeWithContentDescription("Back").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Pocket Assistant").assertIsDisplayed()
    }
}
