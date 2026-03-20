package com.charles.pocketassistant.ui.importing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.charles.pocketassistant.ui.components.SimpleInput

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onFile: () -> Unit,
    onPaste: (String) -> Unit
) {
    var pastedText by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Import") }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Choose a source, preview the text you want to analyze, then process it into summaries, tasks, and reminders.",
                style = MaterialTheme.typography.bodyLarge
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Capture or pick a file", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Button(onClick = onCamera, modifier = Modifier.fillMaxWidth()) { Text("Camera / photo") }
                    Button(onClick = onGallery, modifier = Modifier.fillMaxWidth()) { Text("Gallery / screenshot") }
                    Button(onClick = onFile, modifier = Modifier.fillMaxWidth()) { Text("File picker / PDF") }
                }
            }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Paste text", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    SimpleInput(
                        label = "Paste bill text, notes, or message text",
                        value = pastedText,
                        onValue = { pastedText = it },
                        modifier = Modifier.fillMaxWidth(),
                        supportingText = "This preview is processed locally unless your AI mode routes it to Ollama.",
                        singleLine = false
                    )
                    if (pastedText.isNotBlank()) {
                        Text(
                            pastedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = { onPaste(pastedText.trim()) },
                        enabled = pastedText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Process pasted text")
                    }
                }
            }
        }
    }
}
