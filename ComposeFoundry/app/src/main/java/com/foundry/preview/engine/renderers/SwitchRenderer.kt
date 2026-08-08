package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

class SwitchRenderer : ComponentRenderer {
    override val type = "switch"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        var checked by remember { mutableStateOf(element.attributes["checked"] == "true") }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            Switch(checked = checked, onCheckedChange = { checked = it })
            val label = element.attributes["label"]
            if (label != null) {
                Text(label, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
