package com.foundry.preview.engine.renderers

import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.engine.parseColor

class DividerRenderer : ComponentRenderer {
    override val type = "divider"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        HorizontalDivider(
            modifier = modifier,
            thickness = (element.attributes["thickness"]?.toFloatOrNull() ?: 1f).dp,
            color = element.attributes["color"]?.let { parseColor(it) } ?: MaterialTheme.colorScheme.outline
        )
    }
}
