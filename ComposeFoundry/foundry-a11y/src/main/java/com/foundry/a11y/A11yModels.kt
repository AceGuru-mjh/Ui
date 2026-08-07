package com.foundry.a11y

/**
 * 无障碍审计器的输入树模型。
 * 独立于 app 的 UiElement，由 app 模块提供转换适配器。
 */
data class A11yNode(
    val type: String,
    val attributes: Map<String, String> = emptyMap(),
    val modifier: A11yModifier = A11yModifier(),
    val children: List<A11yNode> = emptyList()
)

data class A11yModifier(
    val width: Float? = null,
    val height: Float? = null,
    val background: String? = null
)

/**
 * 无障碍问题严重级别。
 */
enum class AccessibilitySeverity {
    CRITICAL,
    MAJOR,
    MINOR
}

/**
 * 单条无障碍问题。
 */
data class AccessibilityIssue(
    val severity: AccessibilitySeverity,
    val rule: String,
    val message: String,
    val path: String,
    val suggestion: String
)
