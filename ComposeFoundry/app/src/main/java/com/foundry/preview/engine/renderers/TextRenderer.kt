package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.dsl.parseColor

class TextRenderer : ComponentRenderer {
    override val type = "text"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val text = element.attributes["text"] ?: ""
        val fontSize = element.attributes["fontSize"]?.toFloatOrNull() ?: 14f
        val color: Color = element.attributes["color"]?.let { parseColor(it) } ?: MaterialTheme.colorScheme.onSurface
        val weight = when (element.attributes["weight"]) {
            "bold" -> FontWeight.Bold
            "light" -> FontWeight.Light
            "medium" -> FontWeight.Medium
            else -> FontWeight.Normal
        }
        val align = when (element.attributes["align"]) {
            "center" -> TextAlign.Center
            "end", "right" -> TextAlign.End
            else -> TextAlign.Start
        }
        val maxLines = element.attributes["maxLines"]?.toIntOrNull() ?: Int.MAX_VALUE
        val overflow = if (element.attributes["ellipsis"] == "true") TextOverflow.Ellipsis else TextOverflow.Clip
        val textModifier = if (element.attributes["fillMaxWidth"] == "true") modifier.fillMaxWidth() else modifier

        Text(
            text = text,
            fontSize = fontSize.sp,
            color = color,
            fontWeight = weight,
            textAlign = align,
            maxLines = maxLines,
            overflow = overflow,
            modifier = textModifier
        )
    }
}
