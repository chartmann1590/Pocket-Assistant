package com.charles.pocketassistant.ui.components

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation

/**
 * Text field that keeps cursor/selection stable while typing: when focused, local [TextFieldValue]
 * is authoritative so parent recompositions (DataStore, model lists, etc.) cannot reset the caret.
 */
@Composable
fun SimpleInput(
    label: String,
    value: String,
    onValue: (String) -> Unit,
    modifier: Modifier = Modifier,
    isSecret: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true
) {
    var textFieldValue by remember { mutableStateOf(TextFieldValue(text = value)) }
    var isFocused by remember { mutableStateOf(false) }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            // Always sync when value is cleared (e.g. send button), or when unfocused
            if (value.isEmpty() || !isFocused) {
                textFieldValue = TextFieldValue(
                    text = value,
                    selection = TextRange(value.length)
                )
            }
        }
    }

    OutlinedTextField(
        value = textFieldValue,
        onValueChange = { new ->
            textFieldValue = new
            onValue(new.text)
        },
        label = { Text(label) },
        modifier = modifier.onFocusChanged { focusState ->
            val wasFocused = isFocused
            isFocused = focusState.isFocused
            if (focusState.isFocused && !wasFocused) {
                textFieldValue = TextFieldValue(
                    text = value,
                    selection = TextRange(value.length)
                )
            }
        },
        singleLine = singleLine,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = supportingText?.let { { Text(it) } }
    )
}
