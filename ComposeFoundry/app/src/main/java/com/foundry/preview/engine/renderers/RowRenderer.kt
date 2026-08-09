package com.foundry.preview.engine.renderers

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.engine.RenderElement

class RowRenderer : ComponentRenderer {
    override val type = "row"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val horizontalArrangement = when (element.attributes["horizontalArrangement"]) {
            "center" -> Arrangement.Center
            "spaceBetween" -> Arrangement.SpaceBetween
            "spaceAround" -> Arrangement.SpaceAround
            "spaceEvenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.Start
        }
        val verticalAlignment = when (element.attributes["verticalAlignment"]) {
            "center" -> Alignment.CenterVertically
            "bottom" -> Alignment.Bottom
            else -> Alignment.Top
        }
        val scrollable = element.attributes["scrollable"] == "true"
        val rowModifier = if (element.attributes["fillMaxWidth"] == "true") modifier.fillMaxWidth() else modifier

        val content: @Composable () -> Unit = {
            element.children.forEachIndexed { index, child ->
                RenderElement(child, diagnostics, "$path.row[$index]")
            }
        }

        if (scrollable) {
            Row(
                modifier = rowModifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = verticalAlignment
            ) { content() }
        } else {
            Row(
                modifier = rowModifier,
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = verticalAlignment
            ) { content() }
        }
    }
}
