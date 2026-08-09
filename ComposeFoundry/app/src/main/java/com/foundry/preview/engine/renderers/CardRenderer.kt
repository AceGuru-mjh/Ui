package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.engine.RenderElement

class CardRenderer : ComponentRenderer {
    override val type = "card"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        Card(
            modifier = modifier,
            elevation = CardDefaults.cardElevation((element.attributes["elevation"]?.toIntOrNull() ?: 1).dp)
        ) {
            Column(Modifier.padding(8.dp)) {
                element.children.forEachIndexed { index, child ->
                    RenderElement(child, diagnostics, "$path.card[$index]")
                }
            }
        }
    }
}
