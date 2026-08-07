package com.foundry.animation

/**
 * 动画类型枚举。
 */
enum class FoundryAnimationType {
    NONE,
    FADE_IN,
    SLIDE_IN_LEFT,
    SLIDE_IN_RIGHT,
    SLIDE_IN_TOP,
    SLIDE_IN_BOTTOM,
    SCALE_IN,
    EXPAND_VERTICAL,
    BOUNCE,
    SHAKE,
    PULSE,
    ROTATE_IN
}

/**
 * 动画配置规格。
 * 从 UI 元素的 attributes map 中解析。
 *
 * @param type 动画类型
 * @param durationMs 持续时间（毫秒）
 * @param delayMs 延迟（毫秒）
 * @param triggerOnAppear 是否在出现时自动触发
 * @param springDampingRatio 弹簧阻尼比（null 则用 tween）
 * @param springStiffness 弹簧刚度
 */
data class FoundryAnimationSpec(
    val type: FoundryAnimationType = FoundryAnimationType.NONE,
    val durationMs: Int = 300,
    val delayMs: Int = 0,
    val triggerOnAppear: Boolean = true,
    val springDampingRatio: Float? = null,
    val springStiffness: Float? = null
)
