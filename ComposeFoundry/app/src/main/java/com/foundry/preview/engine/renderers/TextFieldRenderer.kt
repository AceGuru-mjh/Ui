package com.foundry.preview.engine.renderers

import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

class TextFieldRenderer : ComponentRenderer {
    override val type = "textfield"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val label = element.attributes["label"] ?: element.attributes["hint"] ?: ""
        val value = element.attributes["value"] ?: ""
        val placeholder = element.attributes["placeholder"] ?: ""
        TextField(
            value = value,
            onValueChange = {},
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            modifier = modifier,
            singleLine = element.attributes["singleLine"] != "false",
            isError = element.attributes["error"] == "true"
        )
    }
}
