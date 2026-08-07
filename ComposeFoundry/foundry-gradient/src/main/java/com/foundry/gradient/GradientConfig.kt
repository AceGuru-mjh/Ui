package com.foundry.gradient

/**
 * 渐变类型。
 */
enum class GradientType {
    SOLID,
    LINEAR,
    RADIAL,
    CONIC
}

/**
 * 单个色标。
 * @param color 颜色字符串，如 "#FF6200EE"
 * @param offset 位置 0.0~1.0
 */
data class GradientStop(
    val color: String,
    val offset: Float
)

/**
 * 渐变配置。
 * 由 GradientParser 从 DSL 字符串解析生成。
 */
data class GradientConfig(
    val type: GradientType = GradientType.SOLID,
    val angleDegrees: Float = 0f,
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val radius: Float? = null,
    val stops: List<GradientStop> = emptyList(),
    val tileMode: String = "clamp"
)
