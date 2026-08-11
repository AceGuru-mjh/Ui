package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
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

/**
 * 侧边导航栏（NavigationRail）。子节点作为导航项渲染。
 */
class NavigationRailRenderer : ComponentRenderer {
    override val type = "navigationrail"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val items = element.children.filter { it.type == "navigationitem" || it.type == "item" || it.type == "railitem" }
        var selected by remember { mutableStateOf(0) }
        if (items.isEmpty()) {
            Box(
                modifier = modifier.width(80.dp).fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) { Text("No rail items") }
        } else {
            NavigationRail(modifier = modifier.width(80.dp).fillMaxHeight()) {
                items.forEachIndexed { index, item ->
                    NavigationRailItem(
                        selected = selected == index,
                        onClick = { selected = index },
                        icon = { },
                        label = { Text(item.attributes["label"] ?: item.attributes["text"] ?: "Item") },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    }
}
