package com.foundry.preview.engine.renderers

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

class ProgressIndicatorRenderer : ComponentRenderer {
    override val type = "progressindicator"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val progress = (element.attributes["progress"]?.toFloatOrNull() ?: 0f).coerceIn(0f, 1f)
        when (element.attributes["style"]) {
            "linear" -> LinearProgressIndicator(progress = { progress }, modifier = modifier)
            else -> CircularProgressIndicator(progress = { progress }, modifier = modifier)
        }
    }
}
