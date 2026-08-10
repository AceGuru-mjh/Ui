package com.foundry.preview.engine.renderers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

/**
 * 运行时组件占位渲染器（WebView / VideoView / SurfaceView / TextureView 等）。
 * 这些控件依赖 Android 运行时（WebView 内核、MediaPlayer、Surface），
 * 静态预览无法真实呈现，这里给出明确的占位提示而非红色 Unknown 框，
 * 并保留其尺寸/背景等布局属性以便预览整体结构。
 */
class RuntimeViewRenderer : ComponentRenderer {
    override val type = "runtimeview"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val kind = element.attributes["runtimeKind"] ?: element.type
        val label = when (kind.lowercase()) {
            "webview" -> "WebView（运行时内核，预览不可用）"
            "videoview", "video" -> "VideoView（运行时播放，预览不可用）"
            "surfaceview" -> "SurfaceView（运行时绘制，预览不可用）"
            "textureview" -> "TextureView（运行时绘制，预览不可用）"
            else -> "$kind（运行时组件，预览不可用）"
        }
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(Color(0xFFECEFF1))
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.outline,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
