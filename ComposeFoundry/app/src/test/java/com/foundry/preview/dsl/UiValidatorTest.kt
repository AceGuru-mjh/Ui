package com.foundry.preview.dsl

import com.foundry.preview.engine.DiagnosticLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UiValidator 单元测试。
 * 测试元素类型校验、属性缺失警告、嵌套深度限制。
 */
class UiValidatorTest {

    private val validator = UiValidator()

    private fun doc(root: UiElement): UiDocument {
        return UiDocument(root = root)
    }

    // ===== 合法类型 =====

    @Test
    fun `all supported types pass validation`() {
        UiValidator.SUPPORTED_TYPES.forEach { type ->
            val d = doc(UiElement(type = type))
            val diagnostics = validator.validate(d)
            val errors = diagnostics.filter { it.level == DiagnosticLevel.ERROR }
            assertTrue("Type '$type' should not produce errors", errors.isEmpty())
        }
    }

    @Test
    fun `supported types are case insensitive`() {
        val d = doc(UiElement(type = "COLUMN"))
        val diagnostics = validator.validate(d)
        val errors = diagnostics.filter { it.level == DiagnosticLevel.ERROR }
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `supported type with children passes`() {
        val d = doc(UiElement(
            type = "column",
            children = listOf(
                UiElement(type = "text", attributes = mapOf("text" to "Hello")),
                UiElement(type = "button", attributes = mapOf("text" to "Click"))
            )
        ))
        val diagnostics = validator.validate(d)
        val errors = diagnostics.filter { it.level == DiagnosticLevel.ERROR }
        assertTrue(errors.isEmpty())
    }

    // ===== 非法类型 =====

    @Test
    fun `unsupported type produces error`() {
        val d = doc(UiElement(type = "nonexistent"))
        val diagnostics = validator.validate(d)
        val errors = diagnostics.filter { it.level == DiagnosticLevel.ERROR }
        assertEquals(1, errors.size)
        assertTrue(errors[0].message.contains("Unsupported element type"))
        assertTrue(errors[0].message.contains("nonexistent"))
    }

    @Test
    fun `unsupported type error includes path`() {
        val d = doc(UiElement(type = "column", children = listOf(UiElement(type = "badtype"))))
        val diagnostics = validator.validate(d)
        val errors = diagnostics.filter { it.level == DiagnosticLevel.ERROR }
        assertEquals(1, errors.size)
        assertEquals("root.children[0]", errors[0].path)
    }

    // ===== 属性缺失警告 =====

    @Test
    fun `text without text attribute produces warning`() {
        val d = doc(UiElement(type = "text"))
        val diagnostics = validator.validate(d)
        val warnings = diagnostics.filter { it.level == DiagnosticLevel.WARNING }
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].message.contains("missing 'text' attribute"))
    }

    @Test
    fun `text with text attribute produces no warning`() {
        val d = doc(UiElement(type = "text", attributes = mapOf("text" to "Hello")))
        val diagnostics = validator.validate(d)
        val warnings = diagnostics.filter { it.level == DiagnosticLevel.WARNING }
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `button without text attribute produces warning`() {
        val d = doc(UiElement(type = "button"))
        val diagnostics = validator.validate(d)
        val warnings = diagnostics.filter { it.level == DiagnosticLevel.WARNING }
        assertEquals(1, warnings.size)
        assertTrue(warnings[0].message.contains("Button"))
    }

    @Test
    fun `button with text attribute produces no warning`() {
        val d = doc(UiElement(type = "button", attributes = mapOf("text" to "OK")))
        val diagnostics = validator.validate(d)
        val warnings = diagnostics.filter { it.level == DiagnosticLevel.WARNING }
        assertTrue(warnings.isEmpty())
    }

    @Test
    fun `non text element without text attribute produces no warning`() {
        val d = doc(UiElement(type = "column"))
        val diagnostics = validator.validate(d)
        val warnings = diagnostics.filter { it.level == DiagnosticLevel.WARNING }
        assertTrue(warnings.isEmpty())
    }

    // ===== 嵌套深度 =====

    @Test
    fun `normal nesting depth is allowed`() {
        // 5 层嵌套应正常
        var element = UiElement(type = "text", attributes = mapOf("text" to "deep"))
        repeat(5) { element = UiElement(type = "column", children = listOf(element)) }
        val d = doc(element)
        val diagnostics = validator.validate(d)
        val errors = diagnostics.filter { it.level == DiagnosticLevel.ERROR }
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `excessive nesting depth produces error`() {
        // 构造 MAX_DEPTH + 5 层嵌套
        var element = UiElement(type = "text", attributes = mapOf("text" to "bottom"))
        repeat(UiValidator.MAX_DEPTH + 5) {
            element = UiElement(type = "column", children = listOf(element))
        }
        val d = doc(element)
        val diagnostics = validator.validate(d)
        val errors = diagnostics.filter { it.level == DiagnosticLevel.ERROR }
        assertTrue(errors.any { it.message.contains("Nesting depth") })
    }

    @Test
    fun `max depth constant is 32`() {
        assertEquals(32, UiValidator.MAX_DEPTH)
    }

    // ===== 综合场景 =====

    @Test
    fun `multiple issues in one document are all reported`() {
        val d = doc(UiElement(
            type = "column",
            children = listOf(
                UiElement(type = "badtype"),  // 错误：不支持类型
                UiElement(type = "text"),     // 警告：缺 text 属性
                UiElement(type = "button")    // 警告：缺 text 属性
            )
        ))
        val diagnostics = validator.validate(d)
        val errors = diagnostics.filter { it.level == DiagnosticLevel.ERROR }
        val warnings = diagnostics.filter { it.level == DiagnosticLevel.WARNING }
        assertEquals(1, errors.size)
        assertEquals(2, warnings.size)
    }

    @Test
    fun `nested children paths are correct`() {
        val d = doc(UiElement(
            type = "column",
            children = listOf(
                UiElement(type = "row", children = listOf(
                    UiElement(type = "badtype")
                ))
            ]
        ))
        val diagnostics = validator.validate(d)
        val errors = diagnostics.filter { it.level == DiagnosticLevel.ERROR }
        assertEquals(1, errors.size)
        assertEquals("root.children[0].children[0]", errors[0].path)
    }

    @Test
    fun `clean valid document produces no diagnostics`() {
        val d = doc(UiElement(
            type = "column",
            children = listOf(
                UiElement(type = "text", attributes = mapOf("text" to "Hello")),
                UiElement(type = "button", attributes = mapOf("text" to "Click"))
            )
        ))
        val diagnostics = validator.validate(d)
        assertTrue(diagnostics.isEmpty())
    }

    @Test
    fun `supported types count is 18`() {
        // column, row, box, text, button, spacer, card, divider, image,
        // textfield, scroll, surface, lazycolumn, switch, checkbox, slider,
        // progressindicator, tabrow
        assertEquals(18, UiValidator.SUPPORTED_TYPES.size)
    }

    @Test
    fun `all 18 expected types are in supported set`() {
        val expected = setOf(
            "column", "row", "box", "text", "button",
            "spacer", "card", "divider", "image",
            "textfield", "scroll", "surface",
            "lazycolumn", "switch", "checkbox",
            "slider", "progressindicator", "tabrow"
        )
        assertEquals(expected, UiValidator.SUPPORTED_TYPES)
    }

    @Test
    fun `flowrow and flowcolumn are not in supported types`() {
        // v1.9 集成时这两个分支被移除，因渲染函数不存在
        assertFalse(UiValidator.SUPPORTED_TYPES.contains("flowrow"))
        assertFalse(UiValidator.SUPPORTED_TYPES.contains("flowcolumn"))
    }
}
