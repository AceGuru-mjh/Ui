package com.foundry.preview.sandbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundry.core.uimodel.UiGraph
import com.foundry.preview.dsl.parseColor
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.engine.RenderElement
import com.foundry.preview.plugin.toUiElement

@Composable
fun PreviewSurface(
    graph: UiGraph?,
    deviceWidth: Int,
    deviceHeight: Int,
    diagnostics: DiagnosticsEngine
) {
    if (graph?.root == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No document loaded. Enter DSL and click Render.",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
        return
    }

    // Stage 3: 渲染源切换为规范化 UiGraph（UiNode）。
    // 现有 ComponentRenderer 仍消费 legacy dsl.UiElement，这里用 toUiElement 做薄适配，
    // 待后续把 renderer 直接改为消费 UiNode 后即可移除该适配层。
    val bgColor = graph.themes.firstOrNull()
        ?.attributes?.get("backgroundColor")
        ?.raw
        ?.let { parseColor(it) } ?: parseColor("#FFF5F5F5")

    Box(
        modifier = Modifier
            .width(deviceWidth.dp)
            .height(deviceHeight.dp)
            .background(bgColor)
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.TopStart
    ) {
        RenderElement(
            element = toUiElement(graph.root),
            diagnostics = diagnostics,
            path = "root"
        )
    }
}
