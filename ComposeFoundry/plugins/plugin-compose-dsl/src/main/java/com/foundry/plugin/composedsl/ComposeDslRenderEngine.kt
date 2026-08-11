package com.foundry.plugin.composedsl

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundry.core.renderer.IRenderEngine
import com.google.gson.Gson

/**
 * Compose JSON DSL 离线渲染引擎。
 *
 * 将描述 Compose UI 结构的 JSON 负载渲染为 Android View（内含 ComposeView），
 * 供沙箱截图管线使用。
 *
 * ## 设计要点
 * - **compileOnly Compose**: 编译时依赖 Compose BOM，打包不包含
 * - **运行时 ClassLoader 委托**: `DexClassLoader(parent=app.CL)` 提供 Compose 运行时
 * - **ComposeView 包裹**: 返回 FrameLayout(ComposeView)，而非直接 View
 * - **Material3 主题**: 默认使用 Material3 浅色主题
 *
 * ## JSON 协议
 * ```json
 * {
 *   "theme": { "useMaterial3": true },
 *   "components": [
 *     { "type": "Column", "modifier": {"padding": 16, ...},
 *       "children": [
 *         { "type": "Text", "text": "Hello", "style": {"fontSize": 24, "color": "#2196F3"} },
 *         { "type": "Spacer", "height": 12 },
 *         { "type": "Button", "text": "Click" }
 *       ]
 *     }
 *   ]
 * }
 * ```
 *
 * ## 支持的组件类型
 * Column, Row, Box, Text, Button, Spacer, Card
 */
class ComposeDslRenderEngine : IRenderEngine {

    private val gson = Gson()

    override fun render(payload: String, context: Context): View {
        return try {
            val dsl = gson.fromJson(payload, DslPayload::class.java)
            buildComposeContainer(dsl, context)
        } catch (e: Exception) {
            createErrorView("Compose DSL 解析失败: ${e.message}\n\nPayload: ${payload.take(200)}", context)
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  ComposeView 容器构建
    // ═══════════════════════════════════════════════════════════

    private fun buildComposeContainer(dsl: DslPayload, context: Context): FrameLayout {
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(600, 400)
            setBackgroundColor(android.graphics.Color.WHITE)
        }

        val composeView = ComposeView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            // 关键（P0）：Compose 强依赖 ViewTreeLifecycleOwner。
            // 显式声明 DisposeOnViewTreeLifecycleDestroyed：
            // - 确保 Compose 在宿主 Activity/View 销毁时正确清理 Composition，防内存泄漏
            // - 防止 context 非 Activity（如 Service）时未绑定 lifecycle 导致白屏
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PluginTheme(dsl.theme) {
                    RenderComponentTree(dsl.components)
                }
            }
        }
        container.addView(composeView)
        return container
    }

    // ═══════════════════════════════════════════════════════════
    //  Material3 主题包裹
    // ═══════════════════════════════════════════════════════════

    @Composable
    private fun PluginTheme(theme: DslTheme, content: @Composable () -> Unit) {
        if (theme.useMaterial3) {
            MaterialTheme(
                colorScheme = if (theme.darkTheme)
                    androidx.compose.material3.darkColorScheme()
                else
                    androidx.compose.material3.lightColorScheme()
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    content()
                }
            }
        } else {
            content()
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  组件树递归渲染
    // ═══════════════════════════════════════════════════════════

    @Composable
    private fun RenderComponentTree(components: List<DslComponent>) {
        // 如果只有一个组件且是容器类型，直接渲染
        // 否则用 Column 包裹
        if (components.size == 1) {
            RenderComponent(components[0])
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                components.forEach { RenderComponent(it) }
            }
        }
    }

    @Composable
    private fun RenderComponent(c: DslComponent) {
        val modifier = buildModifier(c.modifier)
        when (c.type) {
            "Column" -> Column(
                modifier = modifier,
                verticalArrangement = parseVerticalArrangement(c.modifier.verticalArrangement),
                horizontalAlignment = parseHorizontalAlignment(c.modifier.horizontalAlignment)
            ) { c.children.forEach { RenderComponent(it) } }

            "Row" -> Row(
                modifier = modifier,
                horizontalArrangement = parseHorizontalArrangementCompat(c.modifier.verticalArrangement),
                verticalAlignment = parseVerticalAlignmentCompat(c.modifier.horizontalAlignment)
            ) { c.children.forEach { RenderComponent(it) } }

            "Box" -> Box(modifier = modifier) {
                c.children.forEach { RenderComponent(it) }
            }

            "Text" -> Text(
                text = c.text,
                fontSize = c.style.fontSize.sp,
                color = parseColor(c.style.color),
                fontWeight = if (c.style.bold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (c.style.italic) FontStyle.Italic else FontStyle.Normal,
                textAlign = parseTextAlign(c.style.alignment),
                modifier = modifier
            )

            "Button" -> Button(
                onClick = { /* 沙箱截图模式，无交互 */ },
                modifier = modifier,
                colors = ButtonDefaults.buttonColors(
                    containerColor = parseColor(c.style.color)
                )
            ) {
                Text(text = c.text, fontSize = c.style.fontSize.sp)
            }

            "Spacer" -> Spacer(
                modifier = Modifier
                    .then(if (c.modifier.width != null) Modifier.width(c.modifier.width!!.dp) else Modifier)
                    .then(if (c.modifier.height != null) Modifier.height(c.modifier.height!!.dp) else Modifier)
                    .then(if (c.modifier.height == null && c.modifier.width == null) Modifier.height(16.dp) else Modifier)
            )

            "Card" -> Card(
                modifier = modifier,
                shape = RoundedCornerShape(c.modifier.cornerRadius.dp),
                colors = CardDefaults.cardColors(
                    containerColor = parseColor(c.modifier.background ?: "#FFFFFF")
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    c.children.forEach { RenderComponent(it) }
                }
            }

            else -> Text(
                text = "Unknown type: ${c.type}",
                color = Color.Red,
                modifier = modifier
            )
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  Modifier 构建
    // ═══════════════════════════════════════════════════════════

    @Composable
    private fun buildModifier(m: DslModifier): Modifier {
        var mod: Modifier = Modifier
        if (m.padding > 0) mod = mod.padding(m.padding.dp)
        if (m.fillMaxWidth) mod = mod.fillMaxWidth()
        if (m.fillMaxHeight) mod = mod.fillMaxHeight()
        if (m.width != null) mod = mod.width(m.width!!.dp)
        if (m.height != null) mod = mod.height(m.height!!.dp)
        if (m.background != null) {
            mod = mod.background(parseColor(m.background!!))
        }
        if (m.cornerRadius > 0) {
            mod = mod.clip(RoundedCornerShape(m.cornerRadius.dp))
        }
        if (m.weight != null) mod = mod.then(Modifier)
        return mod
    }

    // ═══════════════════════════════════════════════════════════
    //  辅助解析
    // ═══════════════════════════════════════════════════════════

    private fun parseColor(hex: String): Color {
        return try {
            val colorInt = android.graphics.Color.parseColor(hex)
            Color(colorInt)
        } catch (_: Exception) {
            Color.Black
        }
    }

    private fun parseHorizontalAlignment(a: String): Alignment.Horizontal {
        return when (a) {
            "Center", "CenterHorizontally" -> Alignment.CenterHorizontally
            "End" -> Alignment.End
            else -> Alignment.Start
        }
    }

    private fun parseVerticalArrangement(a: String): Arrangement.Vertical {
        return when (a) {
            "Center" -> Arrangement.Center
            "Bottom" -> Arrangement.Bottom
            "SpaceBetween" -> Arrangement.SpaceBetween
            "SpaceAround" -> Arrangement.SpaceAround
            "SpaceEvenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.Top
        }
    }

    private fun parseHorizontalArrangementCompat(a: String): Arrangement.Horizontal {
        return when (a) {
            "Center" -> Arrangement.Center
            "End" -> Arrangement.End
            "SpaceBetween" -> Arrangement.SpaceBetween
            "SpaceAround" -> Arrangement.SpaceAround
            "SpaceEvenly" -> Arrangement.SpaceEvenly
            else -> Arrangement.Start
        }
    }

    private fun parseVerticalAlignmentCompat(a: String): Alignment.Vertical {
        return when (a) {
            "Center", "CenterVertically" -> Alignment.CenterVertically
            "Bottom" -> Alignment.Bottom
            else -> Alignment.Top
        }
    }

    private fun parseTextAlign(a: String): TextAlign {
        return when (a) {
            "Center" -> TextAlign.Center
            "End" -> TextAlign.End
            else -> TextAlign.Start
        }
    }

    private fun createErrorView(message: String, context: Context): View {
        return TextView(context).apply {
            text = "Compose DSL Engine Error\n\n$message"
            setTextColor(android.graphics.Color.RED)
            textSize = 14f
            setPadding(24, 24, 24, 24)
            setBackgroundColor(android.graphics.Color.parseColor("#FFF3F3"))
        }
    }
}
