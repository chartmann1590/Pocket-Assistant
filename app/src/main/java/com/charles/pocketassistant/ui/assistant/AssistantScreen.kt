package com.charles.pocketassistant.ui.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.charles.pocketassistant.ui.AssistantActionStatus
import com.charles.pocketassistant.ui.AssistantActionUi
import com.charles.pocketassistant.ui.AssistantMessage
import com.charles.pocketassistant.ui.AssistantReferenceUi
import com.charles.pocketassistant.ui.AssistantThreadUi
import com.charles.pocketassistant.ui.AssistantViewModel
import com.charles.pocketassistant.ui.components.SimpleInput
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(nav: NavHostController, vm: AssistantViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    var showHistory by remember { mutableStateOf(false) }
    val totalRows = state.messages.size + if (state.running) 1 else 0

    LaunchedEffect(totalRows, state.running) {
        if (totalRows > 0) {
            listState.animateScrollToItem(totalRows - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Assistant")
                        Text(
                            state.currentThreadTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = { nav.popBackStack() }) {
                        Text("Back")
                    }
                },
                actions = {
                    TextButton(onClick = { showHistory = true }) {
                        Text("History")
                    }
                    TextButton(onClick = vm::startNewThread) {
                        Text("New")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(top = 6.dp, bottom = 8.dp)
                ) {
                    itemsIndexed(state.messages, key = { _, message -> message.id }) { index, message ->
                        if (shouldShowDateHeader(state.messages, index)) {
                            DateDivider(message.createdAt)
                        }
                        MessageBubble(
                            message = message,
                            onOpenReference = { nav.navigate("detail/${it.itemId}") },
                            onConfirmAction = { vm.confirmAssistantAction(message.id, it.id) },
                            onDismissAction = { vm.dismissAssistantAction(message.id, it.id) }
                        )
                    }
                    if (state.running) {
                        item {
                            PendingBubble(state.runningLabel.ifBlank { "Assistant is thinking..." })
                        }
                    }
                }
                if (state.error.isNotBlank()) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }
                Surface(
                    tonalElevation = 3.dp,
                    shadowElevation = 2.dp,
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SimpleInput(
                            label = "Message",
                            value = state.input,
                            onValue = vm::updateInput,
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false
                        )
                        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = vm::send, enabled = !state.running) {
                                Text(if (state.running) "Thinking..." else "Send")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistory) {
        ModalBottomSheet(onDismissRequest = { showHistory = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Saved chats", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (state.threads.isEmpty()) {
                    Text("No saved chats yet.")
                } else {
                    state.threads.forEach { thread ->
                        ThreadRow(
                            thread = thread,
                            selected = thread.id == state.currentThreadId,
                            onClick = {
                                vm.openThread(thread.id)
                                showHistory = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: AssistantMessage,
    onOpenReference: (AssistantReferenceUi) -> Unit,
    onConfirmAction: (AssistantActionUi) -> Unit,
    onDismissAction: (AssistantActionUi) -> Unit
) {
    val isUser = message.role == "user"
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 22.dp, topEnd = 8.dp, bottomEnd = 22.dp, bottomStart = 22.dp)
    } else {
        RoundedCornerShape(topStart = 8.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.82f),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                tonalElevation = if (isUser) 0.dp else 2.dp,
                shape = bubbleShape
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(message.text, style = MaterialTheme.typography.bodyLarge)
                    if (message.references.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            message.references.forEach { reference ->
                                TextButton(onClick = { onOpenReference(reference) }) {
                                Text(reference.label)
                            }
                        }
                    }
                    }
                    if (message.actions.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            message.actions.forEach { action ->
                                AssistantActionCard(
                                    action = action,
                                    onConfirm = { onConfirmAction(action) },
                                    onDismiss = { onDismissAction(action) }
                                )
                            }
                        }
                    }
                }
            }
            Text(
                formatMessageTime(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun PendingBubble(label: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 2.dp,
            shape = RoundedCornerShape(topStart = 8.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 22.dp),
            modifier = Modifier.fillMaxWidth(0.72f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ThreadRow(thread: AssistantThreadUi, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                    else MaterialTheme.colorScheme.surface
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                thread.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
            Text(
                formatThreadTime(thread.updatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DateDivider(timestamp: Long) {
    if (timestamp <= 0L) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
            shape = RoundedCornerShape(999.dp)
        ) {
            Text(
                text = formatMessageDate(timestamp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun AssistantActionCard(
    action: AssistantActionUi,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (action.type == "create_reminder") "Proposed reminder" else "Proposed task",
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
            Text(action.title)
            if (action.details.isNotBlank()) {
                Text(action.details, style = MaterialTheme.typography.bodySmall)
            }
            if (action.scheduledForLabel.isNotBlank()) {
                Text("When: ${action.scheduledForLabel}", style = MaterialTheme.typography.bodySmall)
            }
            if (action.fallbackNote.isNotBlank()) {
                Text(action.fallbackNote, style = MaterialTheme.typography.bodySmall)
            }
            if (action.feedback.isNotBlank()) {
                Text(
                    action.feedback,
                    color = when (action.status) {
                        AssistantActionStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val canConfirm = when (action.status) {
                    AssistantActionStatus.PROPOSED -> action.type != "create_reminder" || action.scheduledForMillis != null
                    AssistantActionStatus.FAILED -> action.type != "create_reminder" || action.scheduledForMillis != null
                    else -> false
                }
                Button(onClick = onConfirm, enabled = canConfirm && action.status != AssistantActionStatus.APPLYING) {
                    Text(
                        when (action.status) {
                            AssistantActionStatus.APPLYING -> "Adding..."
                            AssistantActionStatus.CONFIRMED -> "Added"
                            else -> action.confirmationLabel
                        }
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = action.status == AssistantActionStatus.PROPOSED || action.status == AssistantActionStatus.FAILED
                ) {
                    Text(if (action.status == AssistantActionStatus.FAILED) "Dismiss" else "Not now")
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    return SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(timestamp))
}

private fun formatMessageDate(timestamp: Long): String =
    SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(timestamp))

private fun formatThreadTime(timestamp: Long): String =
    SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(timestamp))

private fun shouldShowDateHeader(messages: List<AssistantMessage>, index: Int): Boolean {
    val current = messages.getOrNull(index) ?: return false
    if (current.createdAt <= 0L) return false
    val previous = messages.getOrNull(index - 1) ?: return true
    if (previous.createdAt <= 0L) return true
    val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
    return dayFormat.format(Date(current.createdAt)) != dayFormat.format(Date(previous.createdAt))
}
