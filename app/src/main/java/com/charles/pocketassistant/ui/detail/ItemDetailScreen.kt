package com.charles.pocketassistant.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.charles.pocketassistant.domain.model.AiExtractionResult
import com.charles.pocketassistant.ui.ItemDetailViewModel
import com.charles.pocketassistant.ui.components.SimpleInput
import com.charles.pocketassistant.ui.theme.LocalExtendedColors
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    nav: NavHostController,
    itemId: String,
    vm: ItemDetailViewModel = hiltViewModel()
) {
    val item by vm.observeItem(itemId).collectAsState(initial = null)
    val latestResult by vm.observeLatestResult(itemId).collectAsState(initial = null)
    val rerunning by vm.rerunning.collectAsState()
    val calendarStatus by vm.calendarStatus.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var pendingCalendarAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (granted) {
            pendingCalendarAction?.invoke()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Calendar permission denied") }
        }
        pendingCalendarAction = null
    }

    fun requestCalendarThen(action: () -> Unit) {
        if (vm.hasCalendarPermission()) {
            action()
        } else {
            pendingCalendarAction = action
            calendarPermissionLauncher.launch(
                arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
            )
        }
    }

    LaunchedEffect(calendarStatus) {
        if (calendarStatus.isNotBlank()) {
            snackbarHostState.showSnackbar(calendarStatus)
            vm.clearCalendarStatus()
        }
    }

    val parsedResult = remember(latestResult?.extractedJson) {
        latestResult?.extractedJson?.let {
            runCatching {
                Json { ignoreUnknownKeys = true }.decodeFromString(AiExtractionResult.serializer(), it)
            }.getOrNull()
        }
    }
    var showRawText by rememberSaveable { mutableStateOf(false) }
    var editMode by rememberSaveable { mutableStateOf(false) }
    var editedSummary by rememberSaveable(latestResult?.summary) { mutableStateOf(latestResult?.summary.orEmpty()) }
    var editedJson by rememberSaveable(latestResult?.extractedJson) { mutableStateOf(latestResult?.extractedJson.orEmpty()) }
    val ext = LocalExtendedColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Item Detail", fontWeight = FontWeight.Bold)
                        item?.classification?.let { cls ->
                            Text(
                                cls.replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.bodySmall,
                                color = when (cls) {
                                    "bill" -> ext.bill
                                    "message" -> ext.message
                                    "appointment" -> ext.appointment
                                    "note" -> ext.note
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (item == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Loading...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            val itemValue = item ?: return@Column
            val resultValue = parsedResult

            if (!itemValue.localUri.isNullOrBlank() && itemValue.type != "pdf") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    AsyncImage(
                        model = itemValue.localUri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
            } else if (!itemValue.localUri.isNullOrBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Imported file", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                itemValue.localUri.orEmpty().substringAfterLast('/'),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // AI Summary
            SectionCard(
                icon = Icons.Outlined.Psychology,
                title = "AI Summary",
                iconTint = MaterialTheme.colorScheme.primary
            ) {
                if (editMode) {
                    SimpleInput(
                        label = "Summary",
                        value = editedSummary,
                        onValue = { editedSummary = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )
                } else {
                    Text(
                        resultValue?.summary ?: latestResult?.summary.orEmpty().ifBlank { "No AI result yet. Run analysis to generate a summary." },
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            // Raw OCR text
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRawText = !showRawText },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.DocumentScanner, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Text("Raw OCR text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(
                            if (showRawText) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        if (showRawText) itemValue.rawText else itemValue.rawText.take(200).replace('\n', ' ') + if (itemValue.rawText.length > 200) "..." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Extracted entities
            if (resultValue?.entities != null) {
                SectionCard(
                    icon = Icons.Outlined.People,
                    title = "Extracted Entities",
                    iconTint = MaterialTheme.colorScheme.secondary
                ) {
                    EntityRow(Icons.Outlined.People, "People", resultValue.entities.people)
                    EntityRow(Icons.Outlined.Business, "Organizations", resultValue.entities.organizations)
                    EntityRow(Icons.Outlined.Payments, "Amounts", resultValue.entities.amounts)
                    EntityRow(Icons.Outlined.CalendarMonth, "Dates", resultValue.entities.dates)
                    EntityRow(Icons.Outlined.AccessTime, "Times", resultValue.entities.times)
                    EntityRow(Icons.Outlined.LocationOn, "Locations", resultValue.entities.locations)
                }
            }

            // Extracted tasks
            SectionCard(
                icon = Icons.Outlined.Add,
                title = "Extracted Tasks",
                iconTint = ext.success
            ) {
                if (resultValue?.tasks.isNullOrEmpty()) {
                    Text(
                        "No task suggestions found.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    resultValue?.tasks?.forEach { task ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        task.title.ifBlank { "Untitled task" },
                                        fontWeight = FontWeight.Medium,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (task.details.isNotBlank()) {
                                        Text(task.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (task.dueDate.isNotBlank()) {
                                        Text("Due: ${task.dueDate}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                FilledTonalButton(
                                    onClick = { vm.addTaskFromExtraction(itemId, task.title.ifBlank { latestResult?.summary ?: "Follow up" }, task.details) },
                                    contentPadding = ButtonDefaults.ContentPadding
                                ) {
                                    Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Add", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // Suggested actions
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Suggested next steps", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    SuggestedActionText(result = resultValue)
                }
            }

            // Action buttons
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            val defaultTitle = resultValue?.tasks?.firstOrNull()?.title
                                ?: resultValue?.appointmentInfo?.title
                                ?: latestResult?.summary
                                ?: "Follow up"
                            vm.addTaskFromExtraction(itemId, defaultTitle, latestResult?.summary)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Task")
                    }
                    Button(
                        onClick = {
                            val title = resultValue?.appointmentInfo?.title
                                ?: resultValue?.billInfo?.vendor
                                ?: latestResult?.summary
                                ?: "Pocket Assistant reminder"
                            vm.createReminder(itemId, title, System.currentTimeMillis() + 60L * 60L * 1000L)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Reminder")
                    }
                }
                // Add to Calendar — shows for bills and appointments
                if (resultValue?.classification == "bill" || resultValue?.classification == "appointment") {
                    Button(
                        onClick = {
                            requestCalendarThen {
                                if (resultValue.classification == "bill") {
                                    vm.addBillToCalendar(resultValue)
                                } else {
                                    vm.addAppointmentToCalendar(resultValue)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.tertiary
                        )
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (resultValue.classification == "bill") "Add Due Date to Calendar"
                            else "Add to Calendar"
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { vm.rerunLocal(itemId, itemValue.rawText, itemValue.type) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !rerunning
                    ) {
                        if (rerunning) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(if (rerunning) "Running..." else "Re-run Local")
                    }
                    OutlinedButton(
                        onClick = { vm.sendToOllama(itemId, itemValue.rawText, itemValue.type) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !rerunning
                    ) {
                        if (rerunning) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Outlined.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(6.dp))
                        Text("Ollama")
                    }
                }
                if (editMode) {
                    SimpleInput(
                        label = "Edited extraction JSON",
                        value = editedJson,
                        onValue = { editedJson = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                vm.saveEditedResult(itemId, editedSummary, editedJson, latestResult?.modelName ?: "edited")
                                editMode = false
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Save edits") }
                        OutlinedButton(
                            onClick = { editMode = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) { Text("Cancel") }
                    }
                } else {
                    OutlinedButton(
                        onClick = { editMode = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Results")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionCard(
    icon: ImageVector,
    title: String,
    iconTint: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun EntityRow(icon: ImageVector, label: String, values: List<String>) {
    if (values.isEmpty()) return
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(values.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SuggestedActionText(result: AiExtractionResult?) {
    Text(
        when (result?.classification) {
            "bill" -> "Create a reminder before the due date and add a follow-up task if payment is still pending."
            "appointment" -> "Create a reminder and confirm the location and time."
            "message" -> "Turn the follow-up into a task so it does not get lost."
            else -> "Review the extracted tasks and save any follow-up actions you want to keep."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
}
