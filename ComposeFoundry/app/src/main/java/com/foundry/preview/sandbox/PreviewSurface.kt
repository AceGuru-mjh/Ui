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
import com.foundry.preview.dsl.UiDocument
import com.foundry.preview.dsl.parseColor
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.engine.RenderElement

@Composable
fun PreviewSurface(
    document: UiDocument?,
    deviceWidth: Int,
    deviceHeight: Int,
    diagnostics: DiagnosticsEngine
) {
    if (document == null) {
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

    val bgColor = parseColor(document.theme.backgroundColor)

    Box(
        modifier = Modifier
            .width(deviceWidth.dp)
            .height(deviceHeight.dp)
            .background(bgColor)
            .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.TopStart
    ) {
        RenderElement(
            element = document.root,
            diagnostics = diagnostics,
            path = "root"
        )
    }
}
