package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

/**
 * Android Spinner 的近似预览：用 Material3 ExposedDropdownMenuBox 表达下拉选择意图。
 * entries 可能来自 @array 资源引用（当前仅原样提示），渲染占位选项。
 */
class SpinnerRenderer : ComponentRenderer {
    override val type = "spinner"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        var expanded by remember { mutableStateOf(false) }
        var selected by remember { mutableStateOf(element.attributes["text"] ?: "") }
        val entries = element.attributes["entries"]?.split("|")?.filter { it.isNotBlank() } ?: listOf("Option 1", "Option 2")
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = selected,
                onValueChange = {},
                readOnly = true,
                label = { Text(element.attributes["hint"] ?: "Spinner") },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                entries.forEach { entry ->
                    DropdownMenuItem(
                        text = { Text(entry) },
                        onClick = {
                            selected = entry
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
