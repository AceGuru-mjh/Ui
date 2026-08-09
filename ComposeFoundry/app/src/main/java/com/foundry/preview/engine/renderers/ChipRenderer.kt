package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

class ChipRenderer : ComponentRenderer {
    override val type = "chip"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        AssistChip(
            onClick = { },
            label = { Text(element.attributes["text"] ?: "Chip") },
            modifier = modifier.padding(4.dp)
        )
    }
}
