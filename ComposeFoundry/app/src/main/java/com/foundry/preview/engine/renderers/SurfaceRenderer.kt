package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.engine.RenderElement
import com.foundry.preview.engine.parseColor

class SurfaceRenderer : ComponentRenderer {
    override val type = "surface"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        Surface(
            modifier = modifier,
            color = element.attributes["color"]?.let { parseColor(it) } ?: MaterialTheme.colorScheme.surface,
            tonalElevation = (element.attributes["elevation"]?.toFloatOrNull() ?: 0f).dp
        ) {
            Column(Modifier.padding(8.dp)) {
                element.children.forEachIndexed { index, child ->
                    RenderElement(child, diagnostics, "$path.surface[$index]")
                }
            }
        }
    }
}
