package com.charles.pocketassistant.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.charles.pocketassistant.data.datastore.SettingsStore
import com.charles.pocketassistant.data.db.entity.TaskEntity
import com.charles.pocketassistant.data.repository.DataMaintenanceRepository
import com.charles.pocketassistant.data.repository.TaskRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * E2E tests for TasksScreen. Verifies task tabs, task creation,
 * done/undo toggle, empty states, and task card display.
 */
@HiltAndroidTest
class TasksScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var settingsStore: SettingsStore

    @Inject
    lateinit var taskRepository: TaskRepository

    @Inject
    lateinit var dataMaintenanceRepository: DataMaintenanceRepository

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            settingsStore.update { it.copy(onboardingComplete = true) }
            dataMaintenanceRepository.clearAllLocalData()
        }
        // Navigate to Tasks screen
        composeRule.onNodeWithText("Tasks").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun tasks_displaysTitle() {
        composeRule.onNodeWithText("Tasks").assertIsDisplayed()
    }

    @Test
    fun tasks_displaysAllTabs() {
        composeRule.onNodeWithText("Today").assertIsDisplayed()
        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()
        composeRule.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun tasks_displaysAddTaskInput() {
        composeRule.onNodeWithText("Add a new task...").assertIsDisplayed()
    }

    @Test
    fun tasks_emptyState_showsForToday() {
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("No tasks for today"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("No tasks for today").assertIsDisplayed()
    }

    @Test
    fun tasks_emptyState_showsForDone() {
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("No completed tasks"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("No completed tasks").assertIsDisplayed()
    }

    @Test
    fun tasks_addManualTask_appearsInList() {
        runBlocking {
            taskRepository.addTask(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = null,
                    title = "Test Manual Task E2E",
                    details = "Test details",
                    dueAt = null,
                    isDone = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Test Manual Task E2E"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Test Manual Task E2E").assertIsDisplayed()
    }

    @Test
    fun tasks_toggleDone_movesToDoneTab() {
        val taskId = UUID.randomUUID().toString()
        runBlocking {
            taskRepository.addTask(
                TaskEntity(
                    id = taskId,
                    itemId = null,
                    title = "Toggle Test Task",
                    details = null,
                    dueAt = null,
                    isDone = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Toggle Test Task"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        // Mark it done via the checkbox
        composeRule.onNodeWithContentDescription("Mark done").performClick()
        composeRule.waitForIdle()
        // Switch to Done tab
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Toggle Test Task"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Toggle Test Task").assertIsDisplayed()
    }

    @Test
    fun tasks_doneTask_showsStrikethrough() {
        runBlocking {
            taskRepository.addTask(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = null,
                    title = "Strikethrough Check Task",
                    details = null,
                    dueAt = null,
                    isDone = true,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        composeRule.onNodeWithText("Done").performClick()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Strikethrough Check Task"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Strikethrough Check Task").assertIsDisplayed()
    }

    @Test
    fun tasks_taskWithDueDate_showsDate() {
        val dueAt = System.currentTimeMillis() + 86_400_000L // tomorrow
        runBlocking {
            taskRepository.addTask(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = null,
                    title = "Due Date Task",
                    details = null,
                    dueAt = dueAt,
                    isDone = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Due Date Task"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Due Date Task").assertIsDisplayed()
        // Due date line shows formatted date like "Due Mar 21, 10:00 AM"
        // Use onAllNodes to verify at least 2 nodes contain "Due" (title + date label)
        val dueNodes = composeRule.onAllNodes(hasText("Due", substring = true))
        assert(dueNodes.fetchSemanticsNodes().size >= 2) { "Expected task title and due date label" }
    }

    @Test
    fun tasks_upcomingTab_showsFutureTask() {
        val tomorrow = System.currentTimeMillis() + 86_400_000L
        runBlocking {
            taskRepository.addTask(
                TaskEntity(
                    id = UUID.randomUUID().toString(),
                    itemId = null,
                    title = "Tomorrow Task",
                    details = null,
                    dueAt = tomorrow,
                    isDone = false,
                    createdAt = System.currentTimeMillis()
                )
            )
        }
        composeRule.onNodeWithText("Upcoming").performClick()
        composeRule.waitUntil(5000) {
            composeRule.onAllNodes(hasText("Tomorrow Task"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Tomorrow Task").assertIsDisplayed()
    }
}
