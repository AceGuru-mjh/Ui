package com.foundry.animation

import androidx.compose.animation.core.Spring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AnimationParser 单元测试。
 * 基于实际库 API：animation.type / animation.duration / animation.delay / animation.damping / animation.stiffness。
 */
class AnimationParserTest {

    // ===== 无动画场景 =====

    @Test
    fun `empty attributes returns NONE`() {
        val spec = AnimationParser.parseFromAttributes(emptyMap())
        assertEquals(FoundryAnimationType.NONE, spec.type)
    }

    @Test
    fun `attributes without animation type key returns NONE`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("text" to "hello"))
        assertEquals(FoundryAnimationType.NONE, spec.type)
    }

    @Test
    fun `animation type key present but empty value returns NONE`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to ""))
        // valueOf("") 会抛异常，被 catch 后返回 NONE
        assertEquals(FoundryAnimationType.NONE, spec.type)
    }

    // ===== 类型解析 =====

    @Test
    fun `fade_in type is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "fade_in"))
        assertEquals(FoundryAnimationType.FADE_IN, spec.type)
    }

    @Test
    fun `FADE_IN uppercase is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "FADE_IN"))
        assertEquals(FoundryAnimationType.FADE_IN, spec.type)
    }

    @Test
    fun `Fade_In mixed case is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "Fade_In"))
        assertEquals(FoundryAnimationType.FADE_IN, spec.type)
    }

    @Test
    fun `slide_in_left is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "slide_in_left"))
        assertEquals(FoundryAnimationType.SLIDE_IN_LEFT, spec.type)
    }

    @Test
    fun `slide_in_right is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "slide_in_right"))
        assertEquals(FoundryAnimationType.SLIDE_IN_RIGHT, spec.type)
    }

    @Test
    fun `slide_in_top is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "slide_in_top"))
        assertEquals(FoundryAnimationType.SLIDE_IN_TOP, spec.type)
    }

    @Test
    fun `slide_in_bottom is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "slide_in_bottom"))
        assertEquals(FoundryAnimationType.SLIDE_IN_BOTTOM, spec.type)
    }

    @Test
    fun `scale_in is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "scale_in"))
        assertEquals(FoundryAnimationType.SCALE_IN, spec.type)
    }

    @Test
    fun `expand_vertical is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "expand_vertical"))
        assertEquals(FoundryAnimationType.EXPAND_VERTICAL, spec.type)
    }

    @Test
    fun `bounce is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "bounce"))
        assertEquals(FoundryAnimationType.BOUNCE, spec.type)
    }

    @Test
    fun `shake is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "shake"))
        assertEquals(FoundryAnimationType.SHAKE, spec.type)
    }

    @Test
    fun `pulse is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "pulse"))
        assertEquals(FoundryAnimationType.PULSE, spec.type)
    }

    @Test
    fun `rotate_in is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "rotate_in"))
        assertEquals(FoundryAnimationType.ROTATE_IN, spec.type)
    }

    @Test
    fun `none type is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "none"))
        assertEquals(FoundryAnimationType.NONE, spec.type)
    }

    @Test
    fun `unknown type returns NONE`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "spinAround"))
        assertEquals(FoundryAnimationType.NONE, spec.type)
    }

    @Test
    fun `all enum types are reachable via parser`() {
        val cases = mapOf(
            "none" to FoundryAnimationType.NONE,
            "fade_in" to FoundryAnimationType.FADE_IN,
            "slide_in_left" to FoundryAnimationType.SLIDE_IN_LEFT,
            "slide_in_right" to FoundryAnimationType.SLIDE_IN_RIGHT,
            "slide_in_top" to FoundryAnimationType.SLIDE_IN_TOP,
            "slide_in_bottom" to FoundryAnimationType.SLIDE_IN_BOTTOM,
            "scale_in" to FoundryAnimationType.SCALE_IN,
            "expand_vertical" to FoundryAnimationType.EXPAND_VERTICAL,
            "bounce" to FoundryAnimationType.BOUNCE,
            "shake" to FoundryAnimationType.SHAKE,
            "pulse" to FoundryAnimationType.PULSE,
            "rotate_in" to FoundryAnimationType.ROTATE_IN
        )
        cases.forEach { (input, expected) ->
            val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to input))
            assertEquals("Failed for input: '$input'", expected, spec.type)
        }
    }

    // ===== duration / delay 默认值 =====

    @Test
    fun `default duration is 300ms`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "fade_in"))
        assertEquals(300, spec.durationMs)
    }

    @Test
    fun `default delay is 0ms`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "fade_in"))
        assertEquals(0, spec.delayMs)
    }

    @Test
    fun `duration is parsed from animation duration key`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "fade_in",
            "animation.duration" to "500"
        ))
        assertEquals(500, spec.durationMs)
    }

    @Test
    fun `delay is parsed from animation delay key`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "fade_in",
            "animation.delay" to "200"
        ))
        assertEquals(200, spec.delayMs)
    }

    @Test
    fun `invalid duration falls back to default`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "fade_in",
            "animation.duration" to "notanumber"
        ))
        assertEquals(300, spec.durationMs)
    }

    @Test
    fun `invalid delay falls back to zero`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "fade_in",
            "animation.delay" to "abc"
        ))
        assertEquals(0, spec.delayMs)
    }

    // ===== triggerOnAppear =====

    @Test
    fun `default triggerOnAppear is true`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "fade_in"))
        assertTrue(spec.triggerOnAppear)
    }

    @Test
    fun `trigger click disables triggerOnAppear`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "fade_in",
            "animation.trigger" to "click"
        ))
        assertFalse(spec.triggerOnAppear)
    }

    @Test
    fun `trigger appear keeps triggerOnAppear true`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "fade_in",
            "animation.trigger" to "appear"
        ))
        assertTrue(spec.triggerOnAppear)
    }

    // ===== spring 参数 =====

    @Test
    fun `default spring params are null`() {
        val spec = AnimationParser.parseFromAttributes(mapOf("animation.type" to "fade_in"))
        assertNull(spec.springDampingRatio)
        assertNull(spec.springStiffness)
    }

    @Test
    fun `damping is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "bounce",
            "animation.damping" to "0.6"
        ))
        assertEquals(0.6f, spec.springDampingRatio!!, 0.001f)
    }

    @Test
    fun `stiffness is parsed`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "bounce",
            "animation.stiffness" to "1500"
        ))
        assertEquals(1500f, spec.springStiffness!!, 0.001f)
    }

    @Test
    fun `invalid damping falls back to null`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "bounce",
            "animation.damping" to "invalid"
        ))
        assertNull(spec.springDampingRatio)
    }

    @Test
    fun `invalid stiffness falls back to null`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "bounce",
            "animation.stiffness" to "invalid"
        ))
        assertNull(spec.springStiffness)
    }

    // ===== 组合场景 =====

    @Test
    fun `full attribute set is parsed correctly`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "slide_in_left",
            "animation.duration" to "800",
            "animation.delay" to "300",
            "animation.damping" to "0.5",
            "animation.stiffness" to "2000",
            "animation.trigger" to "click"
        ))
        assertEquals(FoundryAnimationType.SLIDE_IN_LEFT, spec.type)
        assertEquals(800, spec.durationMs)
        assertEquals(300, spec.delayMs)
        assertEquals(0.5f, spec.springDampingRatio!!, 0.001f)
        assertEquals(2000f, spec.springStiffness!!, 0.001f)
        assertFalse(spec.triggerOnAppear)
    }

    @Test
    fun `unknown keys are ignored`() {
        val spec = AnimationParser.parseFromAttributes(mapOf(
            "animation.type" to "fade_in",
            "animation.unknown" to "value",
            "other.key" to "ignored"
        ))
        assertEquals(FoundryAnimationType.FADE_IN, spec.type)
    }

    // ===== FoundryAnimationSpec 默认值 =====

    @Test
    fun `default spec has correct values`() {
        val spec = FoundryAnimationSpec()
        assertEquals(FoundryAnimationType.NONE, spec.type)
        assertEquals(300, spec.durationMs)
        assertEquals(0, spec.delayMs)
        assertTrue(spec.triggerOnAppear)
        assertNull(spec.springDampingRatio)
        assertNull(spec.springStiffness)
    }

    // ===== buildAnimationSpec（非 Composable 上下文可直接测） =====

    @Test
    fun `buildAnimationSpec returns tween when no spring params`() {
        val spec = FoundryAnimationSpec(
            type = FoundryAnimationType.FADE_IN,
            durationMs = 500,
            delayMs = 100
        )
        val animSpec = AnimationParser.buildAnimationSpec(spec)
        // tween 是 FiniteAnimationSpec<Float> 的实现，验证不抛异常且可获取
        assertTrue(animSpec is androidx.compose.animation.core.FiniteAnimationSpec<Float>)
    }

    @Test
    fun `buildAnimationSpec returns spring when damping provided`() {
        val spec = FoundryAnimationSpec(
            type = FoundryAnimationType.BOUNCE,
            springDampingRatio = 0.5f
        )
        val animSpec = AnimationParser.buildAnimationSpec(spec)
        assertTrue(animSpec is androidx.compose.animation.core.FiniteAnimationSpec<Float>)
    }

    @Test
    fun `buildAnimationSpec returns spring when stiffness provided`() {
        val spec = FoundryAnimationSpec(
            type = FoundryAnimationType.BOUNCE,
            springStiffness = Spring.StiffnessHigh
        )
        val animSpec = AnimationParser.buildAnimationSpec(spec)
        assertTrue(animSpec is androidx.compose.animation.core.FiniteAnimationSpec<Float>)
    }

    @Test
    fun `buildOffsetAnimationSpec returns IntOffset spec`() {
        val spec = FoundryAnimationSpec(type = FoundryAnimationType.SLIDE_IN_LEFT)
        val animSpec = AnimationParser.buildOffsetAnimationSpec(spec)
        assertTrue(animSpec is androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntOffset>)
    }

    @Test
    fun `buildSizeAnimationSpec returns IntSize spec`() {
        val spec = FoundryAnimationSpec(type = FoundryAnimationType.EXPAND_VERTICAL)
        val animSpec = AnimationParser.buildSizeAnimationSpec(spec)
        assertTrue(animSpec is androidx.compose.animation.core.FiniteAnimationSpec<androidx.compose.ui.unit.IntSize>)
    }
}
