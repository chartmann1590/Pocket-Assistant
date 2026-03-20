package com.charles.pocketassistant.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.charles.pocketassistant.domain.model.AiExtractionResult
import com.charles.pocketassistant.ui.ItemDetailViewModel
import com.charles.pocketassistant.ui.components.SimpleInput
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item detail") },
                navigationIcon = {
                    TextButton(onClick = { nav.popBackStack() }) {
                        Text("Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (item == null) {
                Text("Loading item...")
                return@Column
            }

            val itemValue = item ?: return@Column
            val resultValue = parsedResult

            if (!itemValue.localUri.isNullOrBlank() && itemValue.type != "pdf") {
                Card(modifier = Modifier.fillMaxWidth()) {
                    AsyncImage(
                        model = itemValue.localUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else if (!itemValue.localUri.isNullOrBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Imported file", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(itemValue.localUri.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (editMode) {
                        SimpleInput(
                            label = "Summary",
                            value = editedSummary,
                            onValue = { editedSummary = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = false
                        )
                    } else {
                        Text(resultValue?.summary ?: latestResult?.summary.orEmpty().ifBlank { "No AI result yet." })
                    }
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRawText = !showRawText }
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Raw OCR text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (showRawText) itemValue.rawText else itemValue.rawText.take(240))
                    Text(
                        if (showRawText) "Tap to collapse" else "Tap to expand",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Extracted entities", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("People: ${resultValue?.entities?.people?.joinToString().orEmpty().ifBlank { "None" }}")
                    Text("Organizations: ${resultValue?.entities?.organizations?.joinToString().orEmpty().ifBlank { "None" }}")
                    Text("Amounts: ${resultValue?.entities?.amounts?.joinToString().orEmpty().ifBlank { "None" }}")
                    Text("Dates: ${resultValue?.entities?.dates?.joinToString().orEmpty().ifBlank { "None" }}")
                    Text("Times: ${resultValue?.entities?.times?.joinToString().orEmpty().ifBlank { "None" }}")
                    Text("Locations: ${resultValue?.entities?.locations?.joinToString().orEmpty().ifBlank { "None" }}")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Extracted tasks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (resultValue?.tasks.isNullOrEmpty()) {
                        Text("No task suggestions found.")
                    } else {
                        resultValue?.tasks?.forEach { task ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(task.title.ifBlank { "Untitled task" }, fontWeight = FontWeight.SemiBold)
                                    if (task.details.isNotBlank()) {
                                        Text(task.details, style = MaterialTheme.typography.bodySmall)
                                    }
                                    OutlinedButton(
                                        onClick = { vm.addTaskFromExtraction(itemId, task.title.ifBlank { latestResult?.summary ?: "Follow up" }, task.details) }
                                    ) {
                                        Text("Add task")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Suggested actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SuggestedActionText(result = resultValue)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Actions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val defaultTitle = resultValue?.tasks?.firstOrNull()?.title
                                    ?: resultValue?.appointmentInfo?.title
                                    ?: latestResult?.summary
                                    ?: "Follow up"
                                vm.addTaskFromExtraction(itemId, defaultTitle, latestResult?.summary)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add Task")
                        }
                        Button(
                            onClick = {
                                val reminderTitle = resultValue?.appointmentInfo?.title
                                    ?: resultValue?.billInfo?.vendor
                                    ?: latestResult?.summary
                                    ?: "Pocket Assistant reminder"
                                vm.createReminder(itemId, reminderTitle, System.currentTimeMillis() + 60L * 60L * 1000L)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add Reminder")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.rerunLocal(itemId, itemValue.rawText, itemValue.type) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Re-run Local")
                        }
                        OutlinedButton(
                            onClick = { vm.sendToOllama(itemId, itemValue.rawText, itemValue.type) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Send to Ollama")
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
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    vm.saveEditedResult(itemId, editedSummary, editedJson, latestResult?.modelName ?: "edited")
                                    editMode = false
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Save edits")
                            }
                            OutlinedButton(onClick = { editMode = false }, modifier = Modifier.weight(1f)) {
                                Text("Cancel edit")
                            }
                        }
                    } else {
                        OutlinedButton(onClick = { editMode = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Edit Results")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SuggestedActionText(result: AiExtractionResult?) {
    when (result?.classification) {
        "bill" -> Text("Create a reminder before the due date and add a follow-up task if payment is still pending.")
        "appointment" -> Text("Create a reminder and confirm the location and time.")
        "message" -> Text("Turn the follow-up into a task so it does not get lost.")
        else -> Text("Review the extracted tasks and save any follow-up actions you want to keep.")
    }
}
