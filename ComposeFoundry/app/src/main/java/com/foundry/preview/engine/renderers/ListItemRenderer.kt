package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

/**
 * 列表项（ListItem）。headline 取自 text/label，supporting 取自 supportingText，
 * 可选 trailing 文本。配合 LazyColumn 使用。
 */
class ListItemRenderer : ComponentRenderer {
    override val type = "listitem"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val headline = element.attributes["text"] ?: element.attributes["label"] ?: "Item"
        val supporting = element.attributes["supportingText"] ?: element.attributes["subtitle"]
        val trailing = element.attributes["trailingText"] ?: element.attributes["trailing"]
        ListItem(
            modifier = modifier.fillMaxWidth(),
            headlineContent = { Text(headline) },
            supportingContent = if (supporting != null) { { Text(supporting) } } else null,
            trailingContent = if (trailing != null) { { Text(trailing) } } else null
        )
    }
}
