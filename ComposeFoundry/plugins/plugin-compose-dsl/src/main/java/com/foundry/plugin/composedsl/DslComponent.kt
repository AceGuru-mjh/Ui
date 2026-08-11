package com.foundry.plugin.composedsl

/**
 * Compose DSL 组件树（从 JSON 反序列化）。
 *
 * ## 支持的组件类型
 * - Column / Row / Box: 布局容器
 * - Text: 文本
 * - Button: 按钮
 * - Spacer: 间距
 * - Card: 卡片容器
 * - Image: 占位图标
 */
data class DslPayload(
    val theme: DslTheme = DslTheme(),
    val components: List<DslComponent> = emptyList()
)

data class DslTheme(
    val useMaterial3: Boolean = true,
    val darkTheme: Boolean = false
)

data class DslComponent(
    val type: String = "Text",
    val text: String = "",
    val modifier: DslModifier = DslModifier(),
    val style: DslTextStyle = DslTextStyle(),
    val children: List<DslComponent> = emptyList(),
    val icon: String = ""  // 按钮图标名
)

data class DslModifier(
    val padding: Int = 0,
    val width: Int? = null,
    val height: Int? = null,
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val background: String? = null,
    val cornerRadius: Int = 0,
    val horizontalAlignment: String = "Start",  // Start / CenterHorizontally / End
    val verticalArrangement: String = "Top",      // Top / Center / Bottom / SpaceBetween
    val weight: Float? = null
)

data class DslTextStyle(
    val fontSize: Int = 16,
    val color: String = "#333333",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val alignment: String = "Start"  // Start / Center / End
)
