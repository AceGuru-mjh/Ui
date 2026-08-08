package com.foundry.preview.engine.renderers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.engine.RenderElement

class ScrollRenderer : ComponentRenderer {
    override val type = "scroll"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        when (element.attributes["orientation"]) {
            "horizontal" -> {
                Row(modifier = modifier.horizontalScroll(rememberScrollState())) {
                    element.children.forEachIndexed { index, child ->
                        RenderElement(child, diagnostics, "$path.scroll[$index]")
                    }
                }
            }
            else -> {
                Column(modifier = modifier.verticalScroll(rememberScrollState())) {
                    element.children.forEachIndexed { index, child ->
                        RenderElement(child, diagnostics, "$path.scroll[$index]")
                    }
                }
            }
        }
    }
}
