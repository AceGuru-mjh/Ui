package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

/**
 * 角标（Badge）。通常用子节点承载，这里独立渲染为一个小红点/数字徽标。
 * 文本取自属性 text；为空则渲染为无数字小红点。
 */
class BadgeRenderer : ComponentRenderer {
    override val type = "badge"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val text = element.attributes["text"] ?: element.attributes["label"]
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            if (text.isNullOrBlank()) {
                Badge(modifier = Modifier.size(8.dp))
            } else {
                Badge { Text(text, fontSize = 10.sp) }
            }
        }
    }
}
