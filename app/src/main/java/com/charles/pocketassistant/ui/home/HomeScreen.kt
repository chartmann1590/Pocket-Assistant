package com.charles.pocketassistant.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.charles.pocketassistant.data.db.entity.ItemEntity
import com.charles.pocketassistant.ui.HomeViewModel
import com.charles.pocketassistant.ui.ImportViewModel
import com.charles.pocketassistant.ui.components.ProcessingSheet
import com.charles.pocketassistant.ui.theme.LocalExtendedColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    nav: NavHostController,
    importViewModel: ImportViewModel,
    homeViewModel: HomeViewModel = hiltViewModel()
) {
    val items by homeViewModel.items.collectAsState()
    val summaries by homeViewModel.summaries.collectAsState()
    val priorityScores by homeViewModel.priorityScores.collectAsState()
    val processing by importViewModel.processing.collectAsState()
    val searchQuery by homeViewModel.searchQuery.collectAsState()
    val searchResults by homeViewModel.searchResults.collectAsState()
    var selectedFilter by remember { mutableStateOf("All") }
    val displayItems = searchResults ?: when (selectedFilter) {
        "Bills" -> items.filter { it.classification == "bill" }
        "Messages" -> items.filter { it.classification == "message" }
        "Appointments" -> items.filter { it.classification == "appointment" }
        "Notes" -> items.filter { it.classification == "note" }
        else -> items
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pocket Assistant", fontWeight = FontWeight.Bold)
                        Text(
                            "Your local AI organizer",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (com.charles.pocketassistant.BuildConfig.ADS_ENABLED) {
                        IconButton(onClick = { nav.navigate("rewards") }) {
                            Icon(Icons.Outlined.Star, contentDescription = "Rewards")
                        }
                    }
                    IconButton(onClick = { nav.navigate("tasks") }) {
                        Icon(Icons.Outlined.TaskAlt, contentDescription = "Tasks")
                    }
                    IconButton(onClick = { nav.navigate("assistant") }) {
                        Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "Assistant")
                    }
                    IconButton(onClick = { nav.navigate("settings") }) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { nav.navigate("import") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Quick add", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Search bar
            item {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { homeViewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    placeholder = { Text("Search items...") },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { homeViewModel.clearSearch() }) {
                                Icon(Icons.Outlined.Clear, contentDescription = "Clear search", modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { /* search is live */ }),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (searchQuery.isNotBlank()) "Search results" else "Recent items",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${displayItems.size} items",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val filters = listOf("All", "Bills", "Messages", "Appointments", "Notes")
                    items(filters) { label ->
                        FilterChip(
                            selected = selectedFilter == label,
                            onClick = { selectedFilter = label },
                            label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            if (displayItems.isEmpty()) {
                item {
                    EmptyState(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 32.dp)
                    )
                }
            } else {
                items(displayItems, key = { it.id }) { item ->
                    ItemCard(
                        item = item,
                        summary = summaries[item.id],
                        priorityScore = priorityScores[item.id],
                        onClick = { nav.navigate("detail/${item.id}") },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }

    if (processing.running || processing.message.isNotBlank()) {
        ModalBottomSheet(
            onDismissRequest = {
                if (processing.running) importViewModel.cancelProcessing()
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
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ItemCard(item: ItemEntity, summary: String? = null, priorityScore: Float? = null, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ext = LocalExtendedColors.current
    val classColor = when (item.classification) {
        "bill" -> ext.bill
        "message" -> ext.message
        "appointment" -> ext.appointment
        "note" -> ext.note
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(classColor)
                    )
                    Text(
                        item.classification?.replaceFirstChar { it.uppercase() } ?: "Unknown",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = classColor
                    )
                    if (priorityScore != null && priorityScore > 0.5f) {
                        val urgencyLabel = if (priorityScore > 0.7f) "Urgent" else "High"
                        val urgencyColor = if (priorityScore > 0.7f) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.tertiary
                        Text(
                            urgencyLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = urgencyColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(urgencyColor.copy(alpha = 0.12f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    formatRelativeTime(item.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                (summary?.takeIf { it.isNotBlank() } ?: item.rawText.take(160)).replace('\n', ' '),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!item.sourceApp.isNullOrBlank()) {
                Text(
                    "via ${item.sourceApp}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Inbox,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            "No items yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            "Import a screenshot, bill, PDF, or paste some text to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun formatRelativeTime(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - millis
    return when {
        diff < 60_000 -> "Just now"
        diff < 3_600_000 -> "${diff / 60_000}m ago"
        diff < 86_400_000 -> "${diff / 3_600_000}h ago"
        diff < 172_800_000 -> "Yesterday"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(millis))
    }
}
