package com.charles.pocketassistant.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProcessingSheet(ocrProgress: Int, aiProgress: Int, mode: String, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator()
        Text("OCR progress: $ocrProgress%")
        Text("AI progress: $aiProgress%")
        Text("Mode: $mode")
        Button(onClick = onCancel) { Text("Cancel") }
    }
}
