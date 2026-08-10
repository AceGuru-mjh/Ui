package com.foundry.preview.dsl

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

/**
 * ComposeFoundry DSL 顶层文档模型。
 * 对应 .androidui.json 文件的根结构。
 */
@Serializable
data class UiDocument(
    val version: String = "1.0",
    val theme: ThemeConfig = ThemeConfig(),
    val root: UiElement
)

/**
 * 主题配置。
 */
@Serializable
data class ThemeConfig(
    val primaryColor: String = "#FF6200EE",
    val backgroundColor: String = "#FFF5F5F5",
    val surfaceColor: String = "#FFFFFFFF",
    val textColor: String = "#FF1A1A2E",
    val isDark: Boolean = false
)

/**
 * UI 元素节点，DSL 的核心递归结构。
 */
@Serializable
data class UiElement(
    val type: String,
    val modifier: UiModifierSpec = UiModifierSpec(),
    val attributes: Map<String, String> = emptyMap(),
    val children: List<UiElement> = emptyList()
)

/**
 * Modifier 描述，对应 Compose Modifier 链。
 */
@Serializable
data class UiModifierSpec(
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val fillMaxSize: Boolean = false,
    val width: Float? = null,
    val height: Float? = null,
    val padding: PaddingSpec? = null,
    val background: String? = null,
    val cornerRadius: Float? = null,
    val borderWidth: Float? = null,
    val borderColor: String? = null,
    val elevation: Float? = null,
    val horizontalAlignment: String? = null,
    val verticalArrangement: String? = null,
    val verticalAlignment: String? = null,
    val horizontalArrangement: String? = null,
    val contentAlignment: String? = null,
    val align: String? = null,
    val scrollable: Boolean = false,
    val margin: PaddingSpec? = null,
    val alpha: Float? = null
)

/**
 * Padding 描述，支持 all / horizontal / vertical / 四方向。
 */
@Serializable
data class PaddingSpec(
    val all: Float? = null,
    val horizontal: Float? = null,
    val vertical: Float? = null,
    val start: Float? = null,
    val top: Float? = null,
    val end: Float? = null,
    val bottom: Float? = null
)

/**
 * 将颜色字符串（如 "#FF6200EE"）解析为 Compose Color。
 * 解析失败时返回 Color.Gray 并记录日志。
 */
fun parseColor(colorString: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorString))
    } catch (e: Exception) {
        Color.Gray
    }
}
