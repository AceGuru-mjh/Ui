package com.foundry.preview.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundry.preview.state.FoundryViewModel

@Composable
fun CodeGeneratorScreen(viewModel: FoundryViewModel) {
    val generatedCode by viewModel.generatedCode.collectAsState()
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = "Compose Code Generator",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "Convert current DSL to compilable Kotlin Compose code",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = { viewModel.generateComposeCode() },
                modifier = Modifier.weight(1f)
            ) {
                Text("Generate Code")
            }
            Spacer(modifier = Modifier.padding(start = 8.dp))
            OutlinedButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(generatedCode))
                },
                enabled = generatedCode.isNotEmpty()
            ) {
                Text("Copy")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (generatedCode.isNotEmpty()) {
            Text(
                text = generatedCode,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            )
        } else {
            Text(
                text = "Click 'Generate Code' to convert current DSL to Compose Kotlin code.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}
