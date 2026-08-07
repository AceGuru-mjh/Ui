package com.foundry.animation

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * 从 attributes map 中解析动画配置。
 *
 * 键名约定：
 *   "animation.type"     → 动画类型名
 *   "animation.duration" → 持续时间 ms
 *   "animation.delay"    → 延迟 ms
 *   "animation.damping"  → 弹簧阻尼比
 *   "animation.stiffness"→ 弹簧刚度
 */
object AnimationParser {

    fun parseFromAttributes(attributes: Map<String, String>): FoundryAnimationSpec {
        val typeStr = attributes["animation.type"] ?: return FoundryAnimationSpec()
        val type = try {
            FoundryAnimationType.valueOf(typeStr.uppercase())
        } catch (e: Exception) {
            FoundryAnimationType.NONE
        }

        return FoundryAnimationSpec(
            type = type,
            durationMs = attributes["animation.duration"]?.toIntOrNull() ?: 300,
            delayMs = attributes["animation.delay"]?.toIntOrNull() ?: 0,
            triggerOnAppear = attributes["animation.trigger"] != "click",
            springDampingRatio = attributes["animation.damping"]?.toFloatOrNull(),
            springStiffness = attributes["animation.stiffness"]?.toFloatOrNull()
        )
    }

    /**
     * 构建 Compose FiniteAnimationSpec。
     * 有 spring 参数用 spring()，否则用 tween()。
     */
    fun buildAnimationSpec(spec: FoundryAnimationSpec): FiniteAnimationSpec<Float> {
        return if (spec.springDampingRatio != null || spec.springStiffness != null) {
            spring(
                dampingRatio = spec.springDampingRatio ?: Spring.DampingRatioNoBouncy,
                stiffness = spec.springStiffness ?: Spring.StiffnessMedium,
                visibilityThreshold = 0.01f
            )
        } else {
            tween(
                durationMillis = spec.durationMs,
                delayMillis = spec.delayMs
            )
        }
    }

    /**
     * 构建 IntOffset 类型的动画规格。
     * 用于 slideInHorizontally/slideOutHorizontally/slideInVertically/slideOutVertically。
     * 推理：这些函数的 animationSpec 参数类型是 FiniteAnimationSpec<IntOffset>，
     * 因为偏移量以整数像素为单位。
     */
    fun buildOffsetAnimationSpec(spec: FoundryAnimationSpec): FiniteAnimationSpec<IntOffset> {
        return if (spec.springDampingRatio != null || spec.springStiffness != null) {
            spring(
                dampingRatio = spec.springDampingRatio ?: Spring.DampingRatioNoBouncy,
                stiffness = spec.springStiffness ?: Spring.StiffnessMedium,
                visibilityThreshold = IntOffset(1, 1)
            )
        } else {
            tween(
                durationMillis = spec.durationMs,
                delayMillis = spec.delayMs
            )
        }
    }

    /**
     * 构建 IntSize 类型的动画规格。
     * 用于 expandVertically/shrinkVertically。
     * 推理：这些函数的 animationSpec 参数类型是 FiniteAnimationSpec<IntSize>，
     * 因为尺寸以整数像素为单位。
     */
    fun buildSizeAnimationSpec(spec: FoundryAnimationSpec): FiniteAnimationSpec<IntSize> {
        return if (spec.springDampingRatio != null || spec.springStiffness != null) {
            spring(
                dampingRatio = spec.springDampingRatio ?: Spring.DampingRatioNoBouncy,
                stiffness = spec.springStiffness ?: Spring.StiffnessMedium,
                visibilityThreshold = IntSize(1, 1)
            )
        } else {
            tween(
                durationMillis = spec.durationMs,
                delayMillis = spec.delayMs
            )
        }
    }
}
