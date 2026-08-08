package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

class TabRowRenderer : ComponentRenderer {
    override val type = "tabrow"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val tabs = element.children.filter { it.type == "tab" }
        var selected by remember { mutableStateOf(0) }
        if (tabs.isEmpty()) {
            Box(
                modifier = modifier.fillMaxWidth().height(48.dp),
                contentAlignment = Alignment.Center
            ) { Text("No tabs") }
        } else {
            TabRow(selectedTabIndex = selected, modifier = modifier.fillMaxWidth()) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selected == index,
                        onClick = { selected = index },
                        text = { Text(tab.attributes["text"] ?: "Tab") }
                    )
                }
            }
        }
    }
}
