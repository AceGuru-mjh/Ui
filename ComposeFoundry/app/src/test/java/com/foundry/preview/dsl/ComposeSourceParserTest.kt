package com.foundry.preview.dsl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 针对 [ComposeSourceParser] 的纯 JVM 单元测试（不依赖 Android）。
 * 覆盖 Stage 4 Compose 源码预览原型的核心解析能力：
 *  - @Composable fun 提取
 *  - 尾随 lambda 子组件解析
 *  - Modifier 链（fillMaxWidth / padding）
 *  - 文本与属性提取
 *  - 不支持组件降级
 */
class ComposeSourceParserTest {

    private val parser = ComposeSourceParser()

    private fun parseOrThrow(src: String) =
        parser.parse(src).getOrElse { throw it }

    @Test
    fun `extracts composable body root`() {
        val src = """
            @Composable
            fun Home() {
                Column {
                    Text("Hi")
                }
            }
        """
        val (roots, issues) = parseOrThrow(src)
        assertEquals(1, roots.size)
        assertEquals("Column", roots[0].type)
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `trailing lambda children are captured`() {
        val src = """
            @Composable fun Home() {
                Column {
                    Text("Hello")
                    Text(text = "World")
                }
            }
        """
        val (roots, _) = parseOrThrow(src)
        val children = roots[0].children
        assertEquals(2, children.size)
        assertEquals("Text", children[0].type)
        assertEquals("Hello", children[0].attributes["text"])
        assertEquals("World", children[1].attributes["text"])
    }

    @Test
    fun `modifier chain maps fillMaxWidth and padding`() {
        val src = """
            @Composable fun Home() {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { }
            }
        """
        val (roots, _) = parseOrThrow(src)
        val mod = roots[0].modifier
        assertTrue(mod.fillMaxWidth)
        assertEquals(16f, mod.padding?.all)
    }

    @Test
    fun `nested composables are parsed recursively`() {
        val src = """
            @Composable fun Home() {
                Column {
                    Row {
                        Text("A")
                        Text("B")
                    }
                }
            }
        """
        val (roots, _) = parseOrThrow(src)
        val row = roots[0].children.first { it.type == "Row" }
        assertEquals(2, row.children.size)
        assertEquals("A", row.children[0].attributes["text"])
    }

    @Test
    fun `unsupported composable is downgraded to box with issue`() {
        val src = """
            @Composable fun Weird() { MyCustomWidget(label = "x") }
        """
        val (roots, issues) = parseOrThrow(src)
        assertEquals("Box", roots[0].type)
        assertTrue(issues.any { "MyCustomWidget" in it })
    }

    @Test
    fun `no composable yields empty roots with issue`() {
        val src = """fun notUi() = 42"""
        val (roots, issues) = parseOrThrow(src)
        assertTrue(roots.isEmpty())
        assertFalse(issues.isEmpty())
    }

    @Test
    fun `text fontSize is captured as number`() {
        val src = """
            @Composable fun Home() {
                Text("Hello", fontSize = 18.sp)
            }
        """
        val (roots, _) = parseOrThrow(src)
        assertNotNull(roots[0].attributes["fontSize"])
        assertTrue(roots[0].attributes["fontSize"]!!.startsWith("18"))
    }
}
