package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.engine.RenderElement

class ColumnRenderer : ComponentRenderer {
    override val type = "column"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val arrangement = when (element.attributes["arrangement"]) {
            "center" -> Arrangement.Center
            "spaceBetween" -> Arrangement.SpaceBetween
            "spaceAround" -> Arrangement.SpaceAround
            "spaceEvenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.Top
        }
        val horizontalAlignment = when (element.attributes["horizontalAlignment"]) {
            "center" -> Alignment.CenterHorizontally
            "end" -> Alignment.End
            else -> Alignment.Start
        }
        val scrollable = element.attributes["scrollable"] == "true"
        val colModifier = if (element.attributes["fillMaxWidth"] == "true") modifier.fillMaxWidth() else modifier

        val content: @Composable () -> Unit = {
            element.children.forEachIndexed { index, child ->
                RenderElement(child, diagnostics, "$path.col[$index]")
            }
        }

        if (scrollable) {
            Column(
                modifier = colModifier.verticalScroll(rememberScrollState()),
                verticalArrangement = arrangement,
                horizontalAlignment = horizontalAlignment
            ) { content() }
        } else {
            Column(
                modifier = colModifier,
                verticalArrangement = arrangement,
                horizontalAlignment = horizontalAlignment
            ) { content() }
        }
    }
}
