package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

/**
 * 下拉菜单（DropdownMenu / ExposedDropdownMenu）。预览中以展开态静态呈现菜单项。
 */
class DropdownMenuRenderer : ComponentRenderer {
    override val type = "dropdownmenu"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val items = element.children.filter { it.type == "menuitem" || it.type == "item" }
        Box(modifier = modifier, contentAlignment = Alignment.TopStart) {
            DropdownMenu(
                expanded = true,
                onDismissRequest = { },
            ) {
                if (items.isEmpty()) {
                    DropdownMenuItem(text = { Text("No items") }, onClick = { })
                } else {
                    items.forEach { item ->
                        DropdownMenuItem(
                            text = { Text(item.attributes["text"] ?: item.attributes["label"] ?: "Item") },
                            onClick = { }
                        )
                    }
                }
            }
        }
    }
}
