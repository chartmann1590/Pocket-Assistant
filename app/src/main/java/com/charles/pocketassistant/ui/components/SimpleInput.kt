package com.charles.pocketassistant.ui.components

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

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
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        modifier = modifier,
        singleLine = singleLine,
        visualTransformation = if (isSecret) PasswordVisualTransformation() else VisualTransformation.None,
        supportingText = supportingText?.let { { Text(it) } }
    )
}
