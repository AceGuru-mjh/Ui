package com.foundry.preview.engine.renderers

import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

class ButtonRenderer : ComponentRenderer {
    override val type = "button"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val text = element.attributes["text"] ?: ""
        Button(onClick = {}, modifier = modifier) {
            Text(text)
        }
    }
}
