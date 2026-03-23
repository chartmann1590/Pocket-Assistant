package com.charles.pocketassistant.ui.handwriting

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.charles.pocketassistant.ui.HandwritingViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandwritingScreen(
    nav: NavHostController,
    onTextRecognized: (String) -> Unit,
    vm: HandwritingViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Handwriting", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Write with your finger or stylus",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Drawing canvas
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                ) {
                    val strokeColor = MaterialTheme.colorScheme.onSurface
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentStroke = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentStroke = currentStroke + change.position
                                    },
                                    onDragEnd = {
                                        if (currentStroke.isNotEmpty()) {
                                            strokes.add(currentStroke)
                                            vm.addStroke(currentStroke)
                                            currentStroke = emptyList()
                                        }
                                    },
                                    onDragCancel = {
                                        currentStroke = emptyList()
                                    }
                                )
                            }
                    ) {
                        // Draw completed strokes
                        for (stroke in strokes) {
                            if (stroke.size < 2) continue
                            val path = Path().apply {
                                moveTo(stroke[0].x, stroke[0].y)
                                for (i in 1 until stroke.size) {
                                    lineTo(stroke[i].x, stroke[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = 4f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                        // Draw current stroke
                        if (currentStroke.size >= 2) {
                            val path = Path().apply {
                                moveTo(currentStroke[0].x, currentStroke[0].y)
                                for (i in 1 until currentStroke.size) {
                                    lineTo(currentStroke[i].x, currentStroke[i].y)
                                }
                            }
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = 4f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    if (strokes.isEmpty() && currentStroke.isEmpty()) {
                        Text(
                            "Draw here",
                            modifier = Modifier.align(Alignment.Center),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        strokes.clear()
                        currentStroke = emptyList()
                        vm.clearStrokes()
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Clear")
                }
                Button(
                    onClick = { vm.recognize() },
                    modifier = Modifier.weight(1f),
                    enabled = strokes.isNotEmpty() && !state.recognizing,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.recognizing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.size(6.dp))
                    Text("Recognize")
                }
            }

            // Recognized text
            if (state.recognizedText.isNotBlank()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Recognized text",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            state.recognizedText,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Button(
                            onClick = { onTextRecognized(state.recognizedText) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.size(6.dp))
                            Text("Import this text")
                        }
                    }
                }
            }

            if (state.error.isNotBlank()) {
                Text(
                    state.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
