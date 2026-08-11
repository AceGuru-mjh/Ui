package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
 * 底部导航栏（Material3 NavigationBar / 旧版 BottomNavigation）。
 * 子节点 item 渲染为导航项（含可选 badge 文本）。
 */
class NavigationBarRenderer : ComponentRenderer {
    override val type = "navigationbar"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val items = element.children.filter { it.type == "navigationitem" || it.type == "item" }
        var selected by remember { mutableStateOf(0) }
        if (items.isEmpty()) {
            Box(
                modifier = modifier.fillMaxWidth().height(56.dp),
                contentAlignment = Alignment.Center
            ) { Text("No navigation items") }
        } else {
            NavigationBar(modifier = modifier.fillMaxWidth()) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
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
