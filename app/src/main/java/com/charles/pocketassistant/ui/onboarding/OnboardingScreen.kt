package com.charles.pocketassistant.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.pocketassistant.ai.local.LocalModelProfile
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.charles.pocketassistant.ai.local.ModelConfig
import com.charles.pocketassistant.ui.OnboardingModeSelection
import com.charles.pocketassistant.ui.OnboardingViewModel
import com.charles.pocketassistant.ui.components.SimpleInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(nav: NavHostController, vm: OnboardingViewModel = hiltViewModel()) {
    val state by vm.state.collectAsState()
    val uriHandler = LocalUriHandler.current
    val selectedProfile = state.availableModels.firstOrNull { it.id == state.selectedLocalModelId } ?: ModelConfig.defaultProfile
    Scaffold(topBar = { TopAppBar(title = { Text("Pocket Assistant Setup") }) }) { p ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(p)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Set up your offline assistant", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Pocket Assistant keeps OCR and task extraction on-device by default. Choose how you want AI to run before entering the app.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            Text("Choose your AI path", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ModeCard(
                title = "Local only",
                description = "Best privacy. Installs the on-device model package and keeps extraction offline.",
                selected = state.selectedMode == OnboardingModeSelection.LOCAL,
                onClick = vm::selectLocalOnlyPersist
            )
            ModeCard(
                title = "Connect Ollama",
                description = "Use your own Ollama server and skip local model setup on this device.",
                selected = state.selectedMode == OnboardingModeSelection.OLLAMA,
                onClick = vm::selectOllamaOnly
            )
            ModeCard(
                title = "Use both",
                description = "Keep a local path for lighter work and use Ollama when you want the server option available.",
                selected = state.selectedMode == OnboardingModeSelection.BOTH,
                onClick = vm::selectBoth
            )

            if (state.showLocalSetup) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Local AI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(ModelConfig.installSummary(selectedProfile), style = MaterialTheme.typography.bodyMedium)
                            }
                            StatusPill(
                                text = when {
                                    state.localModelInstalled -> "Installed"
                                    state.downloadInProgress -> "Downloading"
                                    else -> "Not ready"
                                },
                                containerColor = when {
                                    state.localModelInstalled -> MaterialTheme.colorScheme.secondaryContainer
                                    state.downloadInProgress -> MaterialTheme.colorScheme.tertiaryContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                }
                            )
                        }
                        Text("Choose local model", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        state.availableModels.forEach { profile ->
                            LocalModelOptionCard(
                                profile = profile,
                                selected = state.selectedLocalModelId == profile.id,
                                onClick = { vm.selectLocalModel(profile.id) }
                            )
                        }
                        Text(
                            "${selectedProfile.tierLabel} model: ${state.modelName} (${state.modelSizeMb} MB)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Keep at least ${selectedProfile.requiredFreeSpaceMb} MB free. Downloaded from Hugging Face into app storage. Wi-Fi is recommended.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            state.modelDescription,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (state.modelRequiresToken) {
                            SimpleInput(
                                label = "Hugging Face access token",
                                value = state.modelDownloadAuthToken,
                                onValue = vm::updateModelDownloadAuthToken,
                                modifier = Modifier.fillMaxWidth(),
                                isSecret = true,
                                supportingText = "Required for gated models. Users must accept the license on Hugging Face first."
                            )
                            TokenHelpCard(
                                profile = selectedProfile,
                                onOpenTokenPage = { uriHandler.openUri("https://huggingface.co/settings/tokens") },
                                onOpenModelPage = { uriHandler.openUri(state.modelRepoUrl) }
                            )
                        } else {
                            Text(
                                "This model can be downloaded without a Hugging Face token.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.localModelPath.isNotBlank()) {
                            Text(
                                "Installed file: ${state.localModelPath.substringAfterLast('/')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (state.downloadInProgress || state.downloadProgress > 0) {
                            LinearProgressIndicator(
                                progress = { (state.downloadProgress / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Text("${state.downloadProgress}% complete", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            state.downloadStatusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FilledTonalButton(
                            onClick = vm::downloadModel,
                            enabled = !state.downloadInProgress,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                when {
                                    state.downloadInProgress -> "Downloading local model..."
                                    state.localModelInstalled -> "Reinstall local model"
                                    else -> ModelConfig.installActionLabel(selectedProfile)
                                }
                            )
                        }
                    }
                }
            }
            if (state.showOllamaSetup) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Ollama", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Point the app at a reachable Ollama server before finishing this path.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        SimpleInput(
                            label = "Ollama base URL",
                            value = state.ollamaBaseUrl,
                            onValue = vm::updateBaseUrl,
                            modifier = Modifier.fillMaxWidth()
                        )
                        SimpleInput(
                            label = "Ollama API token (optional)",
                            value = state.ollamaApiToken,
                            onValue = vm::updateOllamaApiToken,
                            modifier = Modifier.fillMaxWidth(),
                            isSecret = true,
                            supportingText = "Only needed if your reverse proxy or server requires an Authorization header."
                        )
                        SimpleInput(
                            label = "Model name",
                            value = state.ollamaModelName,
                            onValue = vm::updateModelName,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedButton(onClick = vm::testOllama, modifier = Modifier.fillMaxWidth()) {
                            Text("Test Ollama connection")
                        }
                        if (state.ollamaTestMessage.isNotBlank()) {
                            Text(state.ollamaTestMessage, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            if (state.showLocalSetup && state.showOllamaSetup && !state.localModelInstalled) {
                TextButton(onClick = {
                    vm.setupLocalLater()
                    nav.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                        launchSingleTop = true
                    }
                }) {
                    Text("Set up local AI later")
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Demo content", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Add three sample items so you can demo bills, messages, and appointments without importing your own files yet.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = vm::addDemoData, modifier = Modifier.fillMaxWidth()) {
                        Text("Add demo items")
                    }
                    if (state.dataToolsMessage.isNotBlank()) {
                        Text(
                            state.dataToolsMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Button(
                onClick = {
                    vm.finish()
                    nav.navigate("home") {
                        popUpTo("onboarding") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                enabled = state.canFinish,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Finish setup")
            }
            Text(
                "You can adjust AI mode, Ollama settings, and the local model later from Settings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ModeCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = if (selected) 4.dp else 0.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusPill(text: String, containerColor: Color) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TokenHelpCard(
    profile: LocalModelProfile,
    onOpenTokenPage: () -> Unit,
    onOpenModelPage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("How to get a token", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("1. Open the ${profile.displayName} page and accept its Hugging Face access terms with your account.", style = MaterialTheme.typography.bodySmall)
            Text("2. Easiest option: create a Hugging Face token with the Read role.", style = MaterialTheme.typography.bodySmall)
            Text("3. If you use a fine-grained token instead, enable access to public gated repos or grant this model repo explicitly.", style = MaterialTheme.typography.bodySmall)
            Text("4. Paste that token here, then start the model download.", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onOpenModelPage) { Text("Open model page") }
                OutlinedButton(onClick = onOpenTokenPage) { Text("Open token page") }
            }
        }
    }
}

@Composable
private fun LocalModelOptionCard(
    profile: LocalModelProfile,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        onClick = onClick,
        border = BorderStroke(1.dp, borderColor),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(profile.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(profile.tierLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text("${profile.modelSizeMb} MB", style = MaterialTheme.typography.labelMedium)
            Text(profile.shortDescription, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
