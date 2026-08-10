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
        assertEquals("Column", roots.values.first().type)
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
        val children = roots.values.first().children
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
        val mod = roots.values.first().modifier
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
        val row = roots.values.first().children.first { it.type == "Row" }
        assertEquals(2, row.children.size)
        assertEquals("A", row.children[0].attributes["text"])
    }

    @Test
    fun `unsupported composable is downgraded to box with issue`() {
        val src = """
            @Composable fun Weird() { MyCustomWidget(label = "x") }
        """
        val (roots, issues) = parseOrThrow(src)
        assertEquals("Box", roots.values.first().type)
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
        assertNotNull(roots.values.first().attributes["fontSize"])
        assertTrue(roots.values.first().attributes["fontSize"]!!.startsWith("18"))
    }

    @Test
    fun `named color constant is expanded to hex`() {
        val src = """
            @Composable fun Home() {
                Text("Hi", color = Color.Red)
            }
        """
        val (roots, _) = parseOrThrow(src)
        assertEquals("#FFFF0000", roots.values.first().attributes["color"])
    }

    @Test
    fun `if control flow keeps only the taken branch`() {
        val src = """
            @Composable fun Home() {
                Column {
                    if (true) {
                        Text("A")
                    } else {
                        Text("B")
                    }
                }
            }
        """
        val (roots, _) = parseOrThrow(src)
        val column = roots.values.first()
        val children = column.children
        assertEquals(1, children.size)
        assertEquals("A", children[0].attributes["text"])
    }

    @Test
    fun `if control flow with false condition takes else branch`() {
        val src = """
            @Composable fun Home() {
                Column {
                    if (false) {
                        Text("A")
                    } else {
                        Text("B")
                    }
                }
            }
        """
        val (roots, _) = parseOrThrow(src)
        val column = roots.values.first()
        assertEquals(1, column.children.size)
        assertEquals("B", column.children[0].attributes["text"])
    }

    @Test
    fun `for loop over literal list is statically expanded`() {
        val src = """
            @Composable fun Home() {
                Column {
                    for (i in listOf(1, 2, 3)) {
                        Text("item")
                    }
                }
            }
        """
        val (roots, _) = parseOrThrow(src)
        val column = roots.values.first()
        assertEquals(3, column.children.size)
        column.children.forEach { assertEquals("Text", it.type) }
    }

    @Test
    fun `multiple composable functions produce multiple roots`() {
        val src = """
            @Composable fun ScreenA() {
                Text("a")
            }
            @Composable fun ScreenB() {
                Text("b")
            }
        """
        val (roots, _) = parseOrThrow(src)
        assertEquals(2, roots.size)
        assertTrue(roots.containsKey("ScreenA"))
        assertTrue(roots.containsKey("ScreenB"))
    }

    @Test
    fun `navigation bar and bottom navigation map to NavigationBar`() {
        val src = """
            @Composable fun Home() {
                NavigationBar {
                    NavigationBarItem(true, {}, label = { Text("Home") })
                }
            }
        """
        val (roots, issues) = parseOrThrow(src)
        assertEquals("NavigationBar", roots.values.first().type)
        assertTrue("NavigationBar must not be downgraded", issues.none { "Unsupported Composable '<NavigationBar>'" in it })
    }

    @Test
    fun `alert dialog maps to AlertDialog`() {
        val src = """
            @Composable fun Home() {
                AlertDialog(onDismissRequest = {}, confirmButton = { }, title = { Text("Title") })
            }
        """
        val (roots, _) = parseOrThrow(src)
        assertEquals("AlertDialog", roots.values.first().type)
    }

    @Test
    fun `badge maps to Badge`() {
        val src = """
            @Composable fun Home() {
                Badge { Text("3") }
            }
        """
        val (roots, _) = parseOrThrow(src)
        assertEquals("Badge", roots.values.first().type)
    }

    @Test
    fun `web view maps to RuntimeView with runtimeKind`() {
        val src = """
            @Composable fun Home() {
                WebView(url = "https://example.com")
            }
        """
        val (roots, issues) = parseOrThrow(src)
        val root = roots.values.first()
        assertEquals("RuntimeView", root.type)
        assertEquals("webview", root.attributes["runtimeKind"])
        assertTrue("runtime component must not be downgraded to Box", issues.none { "WebView" in it && "Box" in it })
    }

    @Test
    fun `video view maps to RuntimeView with runtimeKind`() {
        val src = """
            @Composable fun Home() {
                VideoView()
            }
        """
        val (roots, _) = parseOrThrow(src)
        val root = roots.values.first()
        assertEquals("RuntimeView", root.type)
        assertEquals("videoview", root.attributes["runtimeKind"])
    }
}
