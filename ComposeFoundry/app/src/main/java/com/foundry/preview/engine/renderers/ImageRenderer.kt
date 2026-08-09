package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

class ImageRenderer : ComponentRenderer {
    override val type = "image"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val contentDesc = element.attributes["contentDescription"] ?: "Image"
        val size = element.attributes["size"]?.toIntOrNull() ?: 100
        val src = element.attributes["src"]
        Box(
            modifier = modifier.size(size.dp),
            contentAlignment = Alignment.Center
        ) {
            if (src != null && src.isNotBlank()) {
                // Coil 异步加载真实图片
                AsyncImage(
                    model = src,
                    contentDescription = contentDesc,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // 无 src 时显示占位符
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(contentDesc, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
                }
            }
        }
    }
}
