package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

/**
 * 顶部应用栏（TopAppBar）。title 取自属性，子节点可作为 action 区呈现。
 */
class TopAppBarRenderer : ComponentRenderer {
    override val type = "topappbar"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val title = element.attributes["title"] ?: element.attributes["text"] ?: "App"
        TopAppBar(
            modifier = modifier.fillMaxWidth(),
            title = { Text(title) },
            actions = {
                element.children.forEach { child ->
                    Text(child.attributes["text"] ?: child.attributes["label"] ?: "•")
                }
            }
        )
    }
}
