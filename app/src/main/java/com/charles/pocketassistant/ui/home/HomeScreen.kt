package com.charles.pocketassistant.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.charles.pocketassistant.data.db.entity.ItemEntity
import com.charles.pocketassistant.ui.HomeViewModel
import com.charles.pocketassistant.ui.ImportViewModel
import com.charles.pocketassistant.ui.components.ProcessingSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    nav: NavHostController,
    pickImage: () -> Unit,
    pickPdf: () -> Unit,
    importViewModel: ImportViewModel,
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val items by homeViewModel.items.collectAsState()
    val processing by importViewModel.processing.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    val filteredItems = when (selectedFilter) {
        "Bills" -> items.filter { it.classification == "bill" }
        "Messages" -> items.filter { it.classification == "message" }
        "Appointments" -> items.filter { it.classification == "appointment" }
        "Tasks" -> items.filter { it.classification == "note" }
        else -> items
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pocket Assistant") },
                actions = {
                    TextButton(onClick = { nav.navigate("assistant") }) { Text("Ask") }
                    TextButton(onClick = { nav.navigate("settings") }) { Text("Settings") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { nav.navigate("import") }) {
                Icon(Icons.Default.Add, contentDescription = "Quick add")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "Turn screenshots, bills, and notes into tasks and reminders.",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard("Add Photo", Icons.Default.Image, Modifier.weight(1f), onClick = pickImage)
                    QuickActionCard("Add Screenshot", Icons.Default.Image, Modifier.weight(1f), onClick = pickImage)
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickActionCard("Add PDF", Icons.Default.Description, Modifier.weight(1f), onClick = pickPdf)
                    QuickActionCard("Paste Text", Icons.AutoMirrored.Filled.NoteAdd, Modifier.weight(1f), onClick = { nav.navigate("import") })
                    QuickActionCard("View Tasks", Icons.Default.TaskAlt, Modifier.weight(1f), onClick = { nav.navigate("tasks") })
                }
            }
            item {
                QuickActionCard("Ask Assistant", Icons.Default.TaskAlt, Modifier.fillMaxWidth(), onClick = { nav.navigate("assistant") })
            }
            item {
                Text("Recent items", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Bills", "Messages", "Appointments", "Tasks").forEach { label ->
                        AssistChip(
                            onClick = { selectedFilter = label },
                            label = { Text(label) }
                        )
                    }
                }
            }
            if (filteredItems.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Text(
                            "No items yet. Import a screenshot, bill, PDF, or pasted note to get started.",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(filteredItems, key = { it.id }) { item ->
                    ItemCard(item = item, onClick = { nav.navigate("detail/${item.id}") })
                }
            }
        }
    }

    if (processing.running || processing.message.isNotBlank()) {
        ModalBottomSheet(
            onDismissRequest = {
                if (processing.running) {
                    importViewModel.cancelProcessing()
                }
            }
        ) {
            ProcessingSheet(
                ocrProgress = processing.ocrProgress,
                aiProgress = processing.aiProgress,
                mode = processing.modeUsed,
                onCancel = importViewModel::cancelProcessing
            )
            if (processing.message.isNotBlank()) {
                Text(
                    processing.message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ItemCard(item: ItemEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                item.classification?.replaceFirstChar { it.uppercase() } ?: "Unknown",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                item.rawText.take(180),
                style = MaterialTheme.typography.bodyMedium
            )
            if (!item.sourceApp.isNullOrBlank()) {
                Text(
                    "Source: ${item.sourceApp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
