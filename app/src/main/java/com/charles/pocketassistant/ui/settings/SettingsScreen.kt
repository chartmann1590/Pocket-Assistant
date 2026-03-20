package com.charles.pocketassistant.ui.settings

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
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

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("AI mode")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::setLocal) { Text("Local") }
                        Button(onClick = vm::setOllama) { Text("Ollama") }
                        Button(onClick = vm::setAuto) { Text("Auto") }
                    }
                    Text("Current mode: ${state.aiMode}")
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Local model")
                    state.availableModels.forEach { profile ->
                        OutlinedButton(
                            onClick = { vm.selectLocalModel(profile.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (state.selectedLocalModelId == profile.id) {
                                    "${profile.tierLabel}: ${profile.displayName} (${profile.modelSizeMb} MB) selected"
                                } else {
                                    "${profile.tierLabel}: ${profile.displayName} (${profile.modelSizeMb} MB)"
                                }
                            )
                        }
                    }
                    Text("Installed: ${if (state.localModelInstalled) "Yes" else "No"}")
                    Text("Version: ${state.localModelVersion.ifBlank { "n/a" }}")
                    Text("Path: ${state.localModelPath.ifBlank { "n/a" }}")
                    Text("Storage used: ${state.localModelStorageMb} MB")
                    Text(
                        "Local model files stay in app-specific storage and normally survive app updates.",
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.localModelDownloadInProgress || state.localModelDownloadProgress > 0) {
                        LinearProgressIndicator(
                            progress = { (state.localModelDownloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("${state.localModelDownloadProgress}% complete")
                    }
                    Text(
                        state.localModelDownloadMessage.ifBlank { ModelConfig.installSummary(selectedProfile) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = vm::redownloadModel,
                            enabled = !state.localModelDownloadInProgress,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (state.localModelDownloadInProgress) "Downloading..." else "Download / redownload")
                        }
                        OutlinedButton(onClick = vm::deleteModel, modifier = Modifier.weight(1f)) {
                            Text("Delete local model")
                        }
                    }
                    SimpleInput(
                        label = "Hugging Face token for local model download",
                        value = state.modelDownloadAuthToken,
                        onValue = vm::updateModelDownloadAuthToken,
                        modifier = Modifier.fillMaxWidth(),
                        isSecret = true,
                        supportingText = if (selectedProfile.requiresAuthToken) {
                            "Required for ${selectedProfile.displayName} because this repository is gated on Hugging Face."
                        } else {
                            "Optional for the current model. The selected Qwen model can be downloaded without a token."
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { uriHandler.openUri(selectedProfile.repoUrl) }) {
                            Text("Open model page")
                        }
                        OutlinedButton(onClick = { uriHandler.openUri("https://huggingface.co/settings/tokens") }) {
                            Text("Open token settings")
                        }
                    }
                    OutlinedButton(
                        onClick = vm::runLocalModelSelfTest,
                        enabled = state.localModelInstalled && !state.localModelSelfTestRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.localModelSelfTestRunning) "Testing local model..." else "Run local model self-test")
                    }
                    if (state.localModelSelfTestMessage.isNotBlank()) {
                        Text(state.localModelSelfTestMessage, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ollama")
                    SimpleInput("Ollama base URL", state.ollamaBaseUrl, vm::updateBaseUrl, Modifier.fillMaxWidth())
                    SimpleInput(
                        "Ollama API token (optional)",
                        state.ollamaApiToken,
                        vm::updateOllamaApiToken,
                        modifier = Modifier.fillMaxWidth(),
                        isSecret = true
                    )
                    SimpleInput("Ollama model", state.ollamaModelName, vm::updateModelName, Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Allow self-signed certs")
                        Switch(checked = state.allowSelfSignedCertificates, onCheckedChange = vm::toggleSelfSigned)
                    }
                    Button(onClick = vm::testOllama, modifier = Modifier.fillMaxWidth()) { Text("Test Ollama connection") }
                    if (testMessage.isNotBlank()) Text(testMessage)
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Prompt / debug")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Show extractor prompt")
                        Switch(checked = state.showPromptDebug, onCheckedChange = vm::togglePromptDebug)
                    }
                    if (state.showPromptDebug) {
                        Text(
                            PromptFactory.general("Invoice from City Power for $82.40 due April 4."),
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Local data")
                    Text("Use demo content for walkthroughs, or clear the database later when you want a clean slate.")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::addDemoData, modifier = Modifier.weight(1f)) {
                            Text("Add demo items")
                        }
                        OutlinedButton(onClick = vm::clearLocalData, modifier = Modifier.weight(1f)) {
                            Text("Clear local database")
                        }
                    }
                    if (state.dataToolsMessage.isNotBlank()) {
                        Text(state.dataToolsMessage)
                    }
                }
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Privacy")
                    Text("OCR and local AI run on-device.")
                    Text("Ollama is optional and only used when the user configures it.")
                    Text("Pocket Assistant does not require any backend hosted by us.")
                    Text("Data stays local unless the user chooses remote Ollama processing.")
                }
            }
        }
    }
}
