package com.foundry.preview.engine.renderers

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.engine.RenderElement

class LazyColumnRenderer : ComponentRenderer {
    override val type = "lazycolumn"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        LazyColumn(modifier = modifier) {
            items(element.children.size) { index ->
                RenderElement(element.children[index], diagnostics, "$path.lazy[$index]")
            }
        }
    }
}
