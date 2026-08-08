package com.foundry.gradient

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GradientParser 单元测试。
 * 基于实际库 API：linear( / radial( / conic( / gradient: 前缀。
 */
class GradientParserTest {

    // ===== isGradient =====

    @Test
    fun `isGradient returns true for linear`() {
        assertTrue(GradientParser.isGradient("linear(45deg, #FF0000 0%, #0000FF 100%)"))
    }

    @Test
    fun `isGradient returns true for radial`() {
        assertTrue(GradientParser.isGradient("radial(center=0.5,0.5, #FF0000 0%, #0000FF 100%)"))
    }

    @Test
    fun `isGradient returns true for conic`() {
        assertTrue(GradientParser.isGradient("conic(0deg, #FF0000 0%, #0000FF 100%)"))
    }

    @Test
    fun `isGradient returns true for preset gradient`() {
        assertTrue(GradientParser.isGradient("gradient:sunset"))
        assertTrue(GradientParser.isGradient("gradient:ocean"))
    }

    @Test
    fun `isGradient returns false for plain color`() {
        assertFalse(GradientParser.isGradient("#FF6200EE"))
    }

    @Test
    fun `isGradient returns false for empty string`() {
        assertFalse(GradientParser.isGradient(""))
    }

    @Test
    fun `isGradient returns false for whitespace`() {
        assertFalse(GradientParser.isGradient("   "))
    }

    @Test
    fun `isGradient trims input before checking`() {
        assertTrue(GradientParser.isGradient("  linear(45deg, #FF0000 0%, #0000FF 100%)  "))
    }

    // ===== parse - 线性渐变 =====

    @Test
    fun `parse linear with angle and percentage stops`() {
        val config = GradientParser.parse("linear(90deg, #FF0000 0%, #0000FF 100%)")
        assertEquals(GradientType.LINEAR, config.type)
        assertEquals(90f, config.angleDegrees)
        assertEquals(2, config.stops.size)
        assertEquals("#FF0000", config.stops[0].color)
        assertEquals(0f, config.stops[0].offset)
        assertEquals("#0000FF", config.stops[1].color)
        assertEquals(1f, config.stops[1].offset)
    }

    @Test
    fun `parse linear without angle defaults to zero`() {
        val config = GradientParser.parse("linear(#FF0000 0%, #0000FF 100%)")
        assertEquals(GradientType.LINEAR, config.type)
        assertEquals(0f, config.angleDegrees)
        assertEquals(2, config.stops.size)
    }

    @Test
    fun `parse linear with three stops distributes offsets evenly`() {
        val config = GradientParser.parse("linear(45deg, #A 0%, #B 50%, #C 100%)")
        assertEquals(3, config.stops.size)
        assertEquals(0f, config.stops[0].offset)
        assertEquals(0.5f, config.stops[1].offset)
        assertEquals(1f, config.stops[2].offset)
    }

    @Test
    fun `parse linear with bare colors assigns auto offsets`() {
        // 无 offset 的色标会自动均匀分布
        val config = GradientParser.parse("linear(0deg, #A, #B, #C, #D)")
        assertEquals(4, config.stops.size)
        assertEquals(0f, config.stops[0].offset)
        assertEquals(1f / 3f, config.stops[1].offset, 0.001f)
        assertEquals(2f / 3f, config.stops[2].offset, 0.001f)
        assertEquals(1f, config.stops[3].offset)
    }

    @Test
    fun `parse empty linear returns default config`() {
        val config = GradientParser.parse("linear()")
        assertEquals(GradientType.LINEAR, config.type)
        assertTrue(config.stops.isEmpty())
    }

    // ===== parse - 径向渐变 =====

    @Test
    fun `parse radial with center coordinates`() {
        val config = GradientParser.parse("radial(center=0.5,0.5, #FF0000 0%, #0000FF 100%)")
        assertEquals(GradientType.RADIAL, config.type)
        assertEquals(0.5f, config.centerX)
        assertEquals(0.5f, config.centerY)
        assertEquals(2, config.stops.size)
    }

    @Test
    fun `parse radial with custom center`() {
        val config = GradientParser.parse("radial(center=0.25,0.75, #FF0000 0%, #0000FF 100%)")
        assertEquals(0.25f, config.centerX)
        assertEquals(0.75f, config.centerY)
    }

    @Test
    fun `parse radial with explicit radius`() {
        val config = GradientParser.parse("radial(center=0.5,0.5, radius=0.8, #FF0000 0%, #0000FF 100%)")
        assertEquals(0.8f, config.radius)
    }

    @Test
    fun `parse radial without center defaults to 0_5`() {
        val config = GradientParser.parse("radial(#FF0000 0%, #0000FF 100%)")
        assertEquals(0.5f, config.centerX)
        assertEquals(0.5f, config.centerY)
    }

    @Test
    fun `parse empty radial returns default config`() {
        val config = GradientParser.parse("radial()")
        assertEquals(GradientType.RADIAL, config.type)
        assertTrue(config.stops.isEmpty())
    }

    // ===== parse - 锥形渐变 =====

    @Test
    fun `parse conic with angle`() {
        val config = GradientParser.parse("conic(0deg, #FF0000 0%, #00FF00 50%, #0000FF 100%)")
        assertEquals(GradientType.CONIC, config.type)
        assertEquals(0f, config.angleDegrees)
        assertEquals(3, config.stops.size)
    }

    @Test
    fun `parse conic with non_zero angle`() {
        val config = GradientParser.parse("conic(90deg, #FF0000 0%, #0000FF 100%)")
        assertEquals(GradientType.CONIC, config.type)
        assertEquals(90f, config.angleDegrees)
    }

    @Test
    fun `parse empty conic returns default config`() {
        val config = GradientParser.parse("conic()")
        assertEquals(GradientType.CONIC, config.type)
        assertTrue(config.stops.isEmpty())
    }

    // ===== parse - 预设渐变 =====

    @Test
    fun `parse preset sunset`() {
        val config = GradientParser.parse("gradient:sunset")
        assertEquals(GradientType.LINEAR, config.type)
        assertEquals(135f, config.angleDegrees)
        assertEquals(3, config.stops.size)
    }

    @Test
    fun `parse preset ocean`() {
        val config = GradientParser.parse("gradient:ocean")
        assertEquals(GradientType.LINEAR, config.type)
        assertEquals(180f, config.angleDegrees)
        assertEquals(2, config.stops.size)
    }

    @Test
    fun `parse preset aurora`() {
        val config = GradientParser.parse("gradient:aurora")
        assertEquals(GradientType.LINEAR, config.type)
        assertEquals(45f, config.angleDegrees)
        assertEquals(3, config.stops.size)
    }

    @Test
    fun `parse preset fire`() {
        val config = GradientParser.parse("gradient:fire")
        assertEquals(GradientType.LINEAR, config.type)
        assertEquals(0f, config.angleDegrees)
    }

    @Test
    fun `parse preset midnight`() {
        val config = GradientParser.parse("gradient:midnight")
        assertEquals(GradientType.LINEAR, config.type)
        assertEquals(135f, config.angleDegrees)
    }

    @Test
    fun `parse preset candy`() {
        val config = GradientParser.parse("gradient:candy")
        assertEquals(GradientType.LINEAR, config.type)
        assertEquals(90f, config.angleDegrees)
    }

    @Test
    fun `parse preset forest`() {
        val config = GradientParser.parse("gradient:forest")
        assertEquals(GradientType.LINEAR, config.type)
        assertEquals(180f, config.angleDegrees)
    }

    @Test
    fun `parse preset royal`() {
        val config = GradientParser.parse("gradient:royal")
        assertEquals(GradientType.LINEAR, config.type)
        assertEquals(45f, config.angleDegrees)
    }

    @Test
    fun `parse unknown preset falls back to solid`() {
        val config = GradientParser.parse("gradient:nonexistent")
        assertEquals(GradientType.SOLID, config.type)
    }

    @Test
    fun `parse preset is case insensitive`() {
        val config = GradientParser.parse("gradient:SUNSET")
        assertEquals(GradientType.LINEAR, config.type)
        assertEquals(135f, config.angleDegrees)
    }

    // ===== parse - 纯色 / 默认 =====

    @Test
    fun `parse plain color returns solid with single stop`() {
        val config = GradientParser.parse("#FF6200EE")
        assertEquals(GradientType.SOLID, config.type)
        assertEquals(1, config.stops.size)
        assertEquals("#FF6200EE", config.stops[0].color)
    }

    @Test
    fun `parse unknown format returns solid`() {
        val config = GradientParser.parse("not-a-gradient")
        assertEquals(GradientType.SOLID, config.type)
        assertEquals(1, config.stops.size)
    }

    // ===== parseStop 内部行为（通过 parse 间接测试） =====

    @Test
    fun `stops with explicit percentages keep their offsets`() {
        val config = GradientParser.parse("linear(0deg, #A 25%, #B 75%)")
        assertEquals(2, config.stops.size)
        assertEquals(0.25f, config.stops[0].offset)
        assertEquals(0.75f, config.stops[1].offset)
    }

    @Test
    fun `mixed explicit and bare stops preserve explicit offsets`() {
        // 第一个有显式 offset，第二个是 bare color（offset=0），不应触发自动分布
        val config = GradientParser.parse("linear(0deg, #A 30%, #B)")
        assertEquals(2, config.stops.size)
        assertEquals(0.30f, config.stops[0].offset, 0.001f)
        assertEquals(0f, config.stops[1].offset)
    }

    @Test
    fun `non_color non_angle parts are ignored as stops`() {
        // parseStop 只接受 # 开头的字符串，其他返回 null
        val config = GradientParser.parse("linear(0deg, #A 0%, notacolor, #B 100%)")
        // "notacolor" 不以 # 开头，parseStop 返回 null，被跳过
        assertEquals(2, config.stops.size)
        assertEquals("#A", config.stops[0].color)
        assertEquals("#B", config.stops[1].color)
    }

    // ===== 默认配置 =====

    @Test
    fun `default GradientConfig has solid type`() {
        val config = GradientConfig()
        assertEquals(GradientType.SOLID, config.type)
        assertEquals(0f, config.angleDegrees)
        assertEquals(0.5f, config.centerX)
        assertEquals(0.5f, config.centerY)
        assertNull(config.radius)
        assertTrue(config.stops.isEmpty())
        assertEquals("clamp", config.tileMode)
    }
}
