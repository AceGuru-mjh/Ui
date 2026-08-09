package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.engine.RenderElement

class BoxRenderer : ComponentRenderer {
    override val type = "box"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val contentAlignment = when (element.attributes["contentAlignment"]) {
            "center" -> Alignment.Center
            "topStart" -> Alignment.TopStart
            "topEnd" -> Alignment.TopEnd
            "bottomStart" -> Alignment.BottomStart
            "bottomEnd" -> Alignment.BottomEnd
            "bottomCenter" -> Alignment.BottomCenter
            "topCenter" -> Alignment.TopCenter
            "centerStart" -> Alignment.CenterStart
            "centerEnd" -> Alignment.CenterEnd
            else -> Alignment.TopStart
        }

        Box(modifier = modifier, contentAlignment = contentAlignment) {
            element.children.forEachIndexed { index, child ->
                val align = child.modifier.align
                val childModifier = if (align != null) {
                    Modifier.align(
                        when (align) {
                            "center" -> Alignment.Center
                            "topStart" -> Alignment.TopStart
                            "topEnd" -> Alignment.TopEnd
                            "bottomStart" -> Alignment.BottomStart
                            "bottomEnd" -> Alignment.BottomEnd
                            "bottomCenter" -> Alignment.BottomCenter
                            "topCenter" -> Alignment.TopCenter
                            "centerStart" -> Alignment.CenterStart
                            "centerEnd" -> Alignment.CenterEnd
                            else -> Alignment.TopStart
                        }
                    )
                } else Modifier
                Box(modifier = childModifier) {
                    RenderElement(child, diagnostics, "$path.box[$index]")
                }
            }
        }
    }
}
