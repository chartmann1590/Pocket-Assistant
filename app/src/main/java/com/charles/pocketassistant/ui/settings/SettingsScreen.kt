package com.charles.pocketassistant.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.charles.pocketassistant.ai.local.ModelConfig
import com.charles.pocketassistant.ai.prompt.PromptFactory
import com.charles.pocketassistant.ui.SettingsViewModel
import com.charles.pocketassistant.ui.components.SimpleInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController, vm: SettingsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val testMessage by vm.testMessage.collectAsState()
    val uriHandler = LocalUriHandler.current
    val selectedProfile = state.availableModels.firstOrNull { it.id == state.selectedLocalModelId } ?: ModelConfig.defaultProfile
    val modes = listOf("Local", "Ollama", "Auto")
    val selectedModeIndex = when (state.aiMode) {
        "LOCAL" -> 0
        "OLLAMA" -> 1
        else -> 2
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Settings", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // AI Mode
            SettingsSection(icon = Icons.Outlined.Tune, title = "AI Mode", iconTint = MaterialTheme.colorScheme.primary) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, label ->
                        SegmentedButton(
                            selected = selectedModeIndex == index,
                            onClick = {
                                when (index) {
                                    0 -> vm.setLocal()
                                    1 -> vm.setOllama()
                                    2 -> vm.setAuto()
                                }
                            },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                        ) {
                            Text(label)
                        }
                    }
                }
                Text(
                    when (selectedModeIndex) {
                        0 -> "All processing happens on-device."
                        1 -> "All processing sent to your Ollama server."
                        else -> "Uses local for light tasks, Ollama for complex ones."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Local Model
            SettingsSection(icon = Icons.Outlined.Psychology, title = "Local Model", iconTint = MaterialTheme.colorScheme.tertiary) {
                state.availableModels.forEach { profile ->
                    val isSelected = state.selectedLocalModelId == profile.id
                    Card(
                        onClick = { vm.selectLocalModel(profile.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        ),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(profile.displayName, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "${profile.tierLabel} - ${ModelConfig.formatSize(profile)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Status: ${if (state.localModelInstalled) "Installed" else "Not installed"}", style = MaterialTheme.typography.bodySmall)
                        if (state.localModelVersion.isNotBlank()) {
                            Text("Version: ${state.localModelVersion}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text("${state.localModelStorageMb} MB used", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (state.localModelDownloadInProgress || state.localModelDownloadProgress > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(
                            progress = { (state.localModelDownloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Text("${state.localModelDownloadProgress}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (state.localModelDownloadMessage.isNotBlank()) {
                    Text(state.localModelDownloadMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(ModelConfig.installSummary(selectedProfile), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = vm::redownloadModel,
                        enabled = !state.localModelDownloadInProgress,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.localModelDownloadInProgress) "Downloading..." else "Download")
                    }
                    OutlinedButton(
                        onClick = vm::deleteModel,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Delete")
                    }
                }

                SimpleInput(
                    label = "Hugging Face token",
                    value = state.modelDownloadAuthToken,
                    onValue = vm::updateModelDownloadAuthToken,
                    modifier = Modifier.fillMaxWidth(),
                    isSecret = true,
                    supportingText = if (selectedProfile.requiresAuthToken) "Required for ${selectedProfile.displayName}" else "Optional"
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { uriHandler.openUri(selectedProfile.repoUrl) }, shape = RoundedCornerShape(10.dp)) {
                        Text("Model page", style = MaterialTheme.typography.labelMedium)
                    }
                    OutlinedButton(onClick = { uriHandler.openUri("https://huggingface.co/settings/tokens") }, shape = RoundedCornerShape(10.dp)) {
                        Text("Token settings", style = MaterialTheme.typography.labelMedium)
                    }
                }
                FilledTonalButton(
                    onClick = vm::runLocalModelSelfTest,
                    enabled = state.localModelInstalled && !state.localModelSelfTestRunning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.localModelSelfTestRunning) "Testing..." else "Self-test local model")
                }
                if (state.localModelSelfTestMessage.isNotBlank()) {
                    Text(state.localModelSelfTestMessage, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Ollama
            SettingsSection(icon = Icons.Outlined.Cloud, title = "Ollama Server", iconTint = MaterialTheme.colorScheme.secondary) {
                if (state.ollamaModelName.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Outlined.Cloud, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Active model", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(state.ollamaModelName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                SimpleInput("Base URL", state.ollamaBaseUrl, vm::updateBaseUrl, Modifier.fillMaxWidth())
                SimpleInput(
                    "API token (optional)",
                    state.ollamaApiToken,
                    vm::updateOllamaApiToken,
                    modifier = Modifier.fillMaxWidth(),
                    isSecret = true
                )
                if (state.ollamaModelsLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                }
                OutlinedButton(
                    onClick = vm::refreshOllamaModels,
                    enabled = state.ollamaBaseUrl.isNotBlank() && !state.ollamaModelsLoading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (state.ollamaModelsLoading) "Loading models..." else "Refresh models") }

                if (state.ollamaRemoteModels.isEmpty() && state.ollamaBaseUrl.isNotBlank() && !state.ollamaModelsLoading) {
                    Text("No models found. Ensure the server has pulled models.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.ollamaRemoteModels.forEach { name ->
                    Card(
                        onClick = { vm.updateModelName(name) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (name == state.ollamaModelName) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (name == state.ollamaModelName) {
                                Text("Selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Allow self-signed certs", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.allowSelfSignedCertificates, onCheckedChange = vm::toggleSelfSigned)
                }
                Button(onClick = vm::testOllama, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Text("Test connection")
                }
                if (testMessage.isNotBlank()) {
                    Text(testMessage, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Debug
            SettingsSection(icon = Icons.Outlined.Code, title = "Debug", iconTint = MaterialTheme.colorScheme.onSurfaceVariant) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Show extractor prompt", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = state.showPromptDebug, onCheckedChange = vm::togglePromptDebug)
                }
                if (state.showPromptDebug) {
                    Text(
                        PromptFactory.general("Invoice from City Power for \$82.40 due April 4."),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                            .padding(10.dp)
                    )
                }
            }

            // Data
            SettingsSection(icon = Icons.Outlined.Storage, title = "Local Data", iconTint = MaterialTheme.colorScheme.error) {
                Text("Use demo content for walkthroughs, or clear the database when you want a clean slate.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilledTonalButton(onClick = vm::addDemoData, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                        Text("Add demo items")
                    }
                    OutlinedButton(
                        onClick = vm::clearLocalData,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear database")
                    }
                }
                if (state.dataToolsMessage.isNotBlank()) {
                    Text(state.dataToolsMessage, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Privacy
            SettingsSection(icon = Icons.Outlined.Shield, title = "Privacy", iconTint = MaterialTheme.colorScheme.tertiary) {
                PrivacyItem("OCR and local AI run on-device")
                PrivacyItem("Ollama is optional and user-configured")
                PrivacyItem("No backend hosted by us")
                PrivacyItem("Data stays local unless you choose Ollama")
            }

            Spacer(Modifier.size(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSection(
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
private fun PrivacyItem(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.tertiary)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
