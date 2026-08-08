package com.foundry.preview.dsl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * UiParser 单元测试。
 * 测试 JSON → UiDocument 的解析，包括合法/非法输入、字段缺失、嵌套结构。
 */
class UiParserTest {

    private val parser = UiParser()

    // ===== 合法输入 =====

    @Test
    fun `parse minimal valid document`() {
        val json = """
            {
              "version": "1.0",
              "root": { "type": "column" }
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals("1.0", doc.version)
        assertEquals("column", doc.root.type)
        assertTrue(doc.root.children.isEmpty())
    }

    @Test
    fun `parse document with theme`() {
        val json = """
            {
              "version": "1.0",
              "theme": {
                "primaryColor": "#FF1976D2",
                "backgroundColor": "#FFFFFFFF",
                "isDark": true
              },
              "root": { "type": "column" }
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals("#FF1976D2", doc.theme.primaryColor)
        assertEquals("#FFFFFFFF", doc.theme.backgroundColor)
        assertTrue(doc.theme.isDark)
    }

    @Test
    fun `parse document with nested children`() {
        val json = """
            {
              "version": "1.0",
              "root": {
                "type": "column",
                "children": [
                  { "type": "text", "attributes": { "text": "Hello" } },
                  { "type": "button", "attributes": { "text": "Click" } }
                ]
              }
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals(2, doc.root.children.size)
        assertEquals("text", doc.root.children[0].type)
        assertEquals("Hello", doc.root.children[0].attributes["text"])
        assertEquals("button", doc.root.children[1].type)
    }

    @Test
    fun `parse document with modifier`() {
        val json = """
            {
              "version": "1.0",
              "root": {
                "type": "column",
                "modifier": {
                  "fillMaxWidth": true,
                  "padding": { "all": 16 }
                }
              }
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertTrue(doc.root.modifier.fillMaxWidth)
        assertEquals(16f, doc.root.modifier.padding?.all)
    }

    @Test
    fun `parse deeply nested structure`() {
        val json = """
            {
              "version": "1.0",
              "root": {
                "type": "column",
                "children": [
                  {
                    "type": "row",
                    "children": [
                      {
                        "type": "box",
                        "children": [
                          { "type": "text", "attributes": { "text": "deep" } }
                        ]
                      }
                    ]
                  }
                ]
              }
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals("text", doc.root.children[0].children[0].children[0].type)
    }

    // ===== 宽松解析 =====

    @Test
    fun `parse tolerates unknown keys`() {
        val json = """
            {
              "version": "1.0",
              "unknownTopKey": "ignored",
              "root": {
                "type": "column",
                "unknownElementKey": "ignored"
              }
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
    }

    @Test
    fun `parse applies default theme when missing`() {
        val json = """
            {
              "version": "1.0",
              "root": { "type": "column" }
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        // ThemeConfig 默认值
        assertEquals("#FF6200EE", doc.theme.primaryColor)
        assertEquals("#FFF5F5F5", doc.theme.backgroundColor)
        assertFalse(doc.theme.isDark)
    }

    @Test
    fun `parse applies default modifier when missing`() {
        val json = """
            {
              "version": "1.0",
              "root": { "type": "column" }
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertFalse(doc.root.modifier.fillMaxWidth)
        assertNull(doc.root.modifier.padding)
    }

    @Test
    fun `parse coerces null values to defaults`() {
        val json = """
            {
              "version": "1.0",
              "theme": null,
              "root": { "type": "column" }
            }
        """.trimIndent()
        // coerceInputValues = true，null 会被替换为默认值
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals("#FF6200EE", doc.theme.primaryColor)
    }

    // ===== 非法输入 =====

    @Test
    fun `parse empty string fails`() {
        val result = parser.parse("")
        assertTrue(result.isFailure)
    }

    @Test
    fun `parse invalid json fails`() {
        val result = parser.parse("{not valid json")
        assertTrue(result.isFailure)
    }

    @Test
    fun `parse missing root fails`() {
        val json = """
            {
              "version": "1.0"
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun `parse missing type in root fails`() {
        val json = """
            {
              "version": "1.0",
              "root": {}
            }
        """.trimIndent()
        // type 是 UiElement 的必填字段（无默认值）
        val result = parser.parse(json)
        assertTrue(result.isFailure)
    }

    @Test
    fun `parse non_object json fails`() {
        val result = parser.parse("[]")
        assertTrue(result.isFailure)
    }

    @Test
    fun `parse null fails`() {
        val result = parser.parse("null")
        assertTrue(result.isFailure)
    }

    // ===== Result 语义 =====

    @Test
    fun `parse failure preserves exception`() {
        val result = parser.parse("invalid")
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun `parse success returns document`() {
        val json = """{"version":"1.0","root":{"type":"column"}}"""
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        assertNotNull(result.getOrNull())
    }

    // ===== 实际示例文件解析 =====

    @Test
    fun `parse realistic login screen layout`() {
        val json = """
            {
              "version": "1.0",
              "theme": {
                "primaryColor": "#FF1976D2",
                "backgroundColor": "#FFFFFFFF",
                "isDark": false
              },
              "root": {
                "type": "column",
                "modifier": { "fillMaxWidth": true, "padding": { "all": 32 } },
                "children": [
                  { "type": "text", "attributes": { "text": "Welcome Back", "fontSize": "28" } },
                  { "type": "textfield", "attributes": { "label": "Email" } },
                  { "type": "textfield", "attributes": { "label": "Password" } },
                  { "type": "button", "attributes": { "text": "Sign In" } }
                ]
              }
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals(4, doc.root.children.size)
        assertEquals("Welcome Back", doc.root.children[0].attributes["text"])
        assertEquals("28", doc.root.children[0].attributes["fontSize"])
    }

    @Test
    fun `parse document with gradient background`() {
        val json = """
            {
              "version": "1.0",
              "root": {
                "type": "box",
                "modifier": {
                  "background": "linear(135deg, #FF6200EE 0%, #FF9C27B0 100%)",
                  "cornerRadius": 16
                }
              }
            }
        """.trimIndent()
        val result = parser.parse(json)
        assertTrue(result.isSuccess)
        val doc = result.getOrThrow()
        assertEquals("linear(135deg, #FF6200EE 0%, #FF9C27B0 100%)", doc.root.modifier.background)
        assertEquals(16f, doc.root.modifier.cornerRadius)
    }
}
