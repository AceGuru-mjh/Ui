package com.foundry.preview.engine.renderers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

/**
 * 导航抽屉（NavigationView / ModalDrawer / NavigationDrawer）。
 * 静态呈现侧边面板：可选 header + 菜单项列表。交互展开在预览中无意义，
 * 这里直接以展开态呈现内容结构。
 */
class DrawerRenderer : ComponentRenderer {
    override val type = "drawer"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val items = element.children.filter { it.type == "navigationitem" || it.type == "item" || it.type == "draweritem" }
        val header = element.attributes["header"] ?: element.attributes["title"]
        Column(
            modifier = modifier
                .width(280.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
                .padding(8.dp),
            verticalArrangement = Arrangement.Top
        ) {
            if (header != null) {
                Text(header, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(12.dp))
                HorizontalDivider()
            }
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text("No drawer items", color = Color.Gray)
                }
            } else {
                items.forEach { item ->
                    Text(
                        item.attributes["label"] ?: item.attributes["text"] ?: "Item",
                        modifier = Modifier.fillMaxWidth().padding(12.dp)
                    )
                }
            }
        }
    }
}
