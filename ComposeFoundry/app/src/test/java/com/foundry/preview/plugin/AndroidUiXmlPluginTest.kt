package com.foundry.preview.plugin

import com.foundry.core.plugin.ArtifactKind
import com.foundry.core.plugin.PreviewContext
import com.foundry.core.plugin.UiArtifact
import com.foundry.core.uimodel.Confidence
import com.foundry.core.uimodel.ResourceTable
import com.foundry.core.uimodel.Severity
import com.foundry.core.uimodel.UiValue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class AndroidUiXmlPluginTest {

    private val plugin = AndroidUiXmlPlugin()

    private fun xmlArtifact(content: String, uri: String = "") = UiArtifact(
        id = "x", uri = uri, displayName = "x",
        content = content, extension = "xml",
        detectedKind = ArtifactKind.ANDROID_XML_LAYOUT
    )

    /** 创建带 res/layout + res/values 的临时 Android 工程，返回工程根。 */
    private fun tempAndroidProject(): File {
        val root = File(System.getProperty("java.io.tmpdir"), "foundry-android-${UUID.randomUUID()}")
        root.deleteOnExit()
        File(root, "res/layout").mkdirs()
        File(root, "res/values").mkdirs()
        // findAndroidProjectRoot 依赖 res 目录存在
        return root
    }

    @Test
    fun `unsupported tag is downgraded and reported as warning`() = runBlocking {
        val xml = """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent" android:layout_height="wrap_content">
            <FooBar android:layout_width="wrap_content"/>
        </LinearLayout>"""
        val result = plugin.parse(xmlArtifact(xml), PreviewContext())
        val graph = (result as com.foundry.core.plugin.ParseResult.Success).graph
        val warn = graph.diagnostics.filter { it.severity == Severity.WARNING }
        assertTrue("should report downgraded unsupported tag", warn.any { "FooBar" in it.message })
        // 降级为 Box，仍能解析出节点
        assertFalse(graph.root?.children.isNullOrEmpty())
    }

    @Test
    fun `expanded material tags map to registered component types without downgrade`() = runBlocking {
        val xml = """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical">
            <com.google.android.material.button.MaterialButton android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="OK"/>
            <com.google.android.material.switchMaterial.SwitchMaterial android:layout_width="wrap_content" android:layout_height="wrap_content"/>
            <com.google.android.material.chip.Chip android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Tag"/>
            <com.google.android.material.divider.MaterialDivider android:layout_width="match_parent" android:layout_height="wrap_content"/>
            <ProgressBar android:layout_width="wrap_content" android:layout_height="wrap_content"/>
            <Spinner android:layout_width="match_parent" android:layout_height="wrap_content"/>
            <RadioButton android:layout_width="wrap_content" android:layout_height="wrap_content" android:text="Pick"/>
        </LinearLayout>"""
        val result = plugin.parse(xmlArtifact(xml), PreviewContext())
        val graph = (result as com.foundry.core.plugin.ParseResult.Success).graph
        // 这些标签都应被识别，不应产生降级 WARNING
        val downgrades = graph.diagnostics.filter {
            it.severity == Severity.WARNING && "downgraded to Box" in it.message
        }
        assertTrue("no downgrade for expanded material tags, got: ${downgrades.map { it.message }}", downgrades.isEmpty())
        val types = graph.root?.children?.map { it.type } ?: emptyList()
        assertEquals(listOf("Button", "Switch", "Chip", "Divider", "ProgressIndicator", "Spinner", "RadioButton"), types)
    }

    @Test
    fun `expanded attributes are mapped without ignored-attribute warnings`() = runBlocking {
        val xml = """<TextView xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Hi" android:textSize="16sp" android:letterSpacing="0.05"
            android:layout_margin="8dp" android:gravity="center" android:maxLines="2"/>"""
        val result = plugin.parse(xmlArtifact(xml), PreviewContext())
        val graph = (result as com.foundry.core.plugin.ParseResult.Success).graph
        val ignored = graph.diagnostics.filter { it.severity == Severity.WARNING && "Ignored unsupported attribute" in it.message }
        assertTrue("no ignored-attribute warnings for expanded attrs, got: ${ignored.map { it.message }}", ignored.isEmpty())
        val mod = graph.root?.modifiers?.firstOrNull()
        assertEquals(8f, mod?.margin?.all)
        assertEquals("center", mod?.horizontalAlignment)
    }

    @Test
    fun `ignored unsupported attribute is reported`() = runBlocking {
        val xml = """<TextView xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Hi" android:fooBarUnsupported="1"/>"""
        val result = plugin.parse(xmlArtifact(xml), PreviewContext())
        val graph = (result as com.foundry.core.plugin.ParseResult.Success).graph
        assertTrue(
            "should report ignored attribute fooBarUnsupported",
            graph.diagnostics.any { "fooBarUnsupported" in it.message }
        )
    }

    @Test
    fun `onClick is captured as placeholder attribute`() = runBlocking {
        val xml = """<Button xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="OK" android:onClick="onOkClicked"/>"""
        val result = plugin.parse(xmlArtifact(xml), PreviewContext())
        val graph = (result as com.foundry.core.plugin.ParseResult.Success).graph
        assertEquals("onOkClicked", graph.root?.attributes?.get("onClick")?.raw)
    }

    @Test
    fun `letterSpacing is preserved as em ratio`() = runBlocking {
        val xml = """<TextView xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Hi" android:letterSpacing="0.08"/>"""
        val result = plugin.parse(xmlArtifact(xml), PreviewContext())
        val graph = (result as com.foundry.core.plugin.ParseResult.Success).graph
        assertEquals("0.08", graph.root?.attributes?.get("letterSpacing")?.raw)
    }

    @Test
    fun `resource reference is resolved when resource table provided`() = runBlocking {
        val xml = """<TextView xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/app_name" android:background="@color/bg"/>"""
        val table = ResourceTable(
            strings = mapOf("app_name" to "Foundry"),
            colors = mapOf("bg" to "#FFFFFF")
        )
        val result = plugin.parse(xmlArtifact(xml), PreviewContext(resourceTable = table))
        val graph = (result as com.foundry.core.plugin.ParseResult.Success).graph
        val textAttr = graph.root?.attributes?.get("text")
        assertEquals(UiValue.Text("Foundry"), textAttr)
        val bg = graph.root?.modifiers?.firstOrNull()?.background
        assertEquals("#FFFFFF", bg)
        // 已解析，不应再提示未解析资源
        assertTrue(graph.diagnostics.none { it.code == "XML_RESOURCE_UNRESOLVED" })
    }

    @Test
    fun `unresolved resource reference emits info diagnostic`() = runBlocking {
        val xml = """<TextView xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="@string/missing"/>"""
        val result = plugin.parse(xmlArtifact(xml), PreviewContext())
        val graph = (result as com.foundry.core.plugin.ParseResult.Success).graph
        // 文本属性保留为 ResourceRef
        assertTrue(graph.root?.attributes?.get("text") is UiValue.ResourceRef)
        assertTrue(
            "should warn unresolved resource",
            graph.diagnostics.any { it.code == "XML_RESOURCE_UNRESOLVED" && "@string/missing" in it.message }
        )
    }

    @Test
    fun `RadioGroup defaults to Column and horizontal becomes Row`() = runBlocking {
        val vertical = """<RadioGroup xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="wrap_content" android:layout_height="wrap_content"/>"""
        val vGraph = (plugin.parse(xmlArtifact(vertical), PreviewContext()) as com.foundry.core.plugin.ParseResult.Success).graph
        assertEquals("Column", vGraph.root?.type)

        val horizontal = """<RadioGroup xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:orientation="horizontal"/>"""
        val hGraph = (plugin.parse(xmlArtifact(horizontal), PreviewContext()) as com.foundry.core.plugin.ParseResult.Success).graph
        assertEquals("Row", hGraph.root?.type)
    }

    @Test
    fun `visibility attribute is preserved for rendering decisions`() = runBlocking {
        val xml = """<TextView xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Hi" android:visibility="gone"/>"""
        val graph = (plugin.parse(xmlArtifact(xml), PreviewContext()) as com.foundry.core.plugin.ParseResult.Success).graph
        assertEquals("gone", graph.root?.attributes?.get("visibility")?.raw)
    }

    @Test
    fun `ConstraintLayout child constraint is approximated to align`() = runBlocking {
        val xml = """<androidx.constraintlayout.widget.ConstraintLayout
            xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:layout_width="match_parent" android:layout_height="match_parent">
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="Hi"
                app:layout_constraintStart_toStartOf="parent"
                app:layout_constraintTop_toTopOf="parent"/>
        </androidx.constraintlayout.widget.ConstraintLayout>"""
        val graph = (plugin.parse(xmlArtifact(xml), PreviewContext()) as com.foundry.core.plugin.ParseResult.Success).graph
        val child = graph.root?.children?.first()
        assertEquals("Text", child?.type)
        assertEquals("topStart", child?.modifiers?.firstOrNull()?.align)
    }

    @Test
    fun `include layout is expanded from project root`() = runBlocking {
        val root = tempAndroidProject()
        // 被 include 的内嵌布局
        File(root, "res/layout/included.xml").writeText(
            """<TextView xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="Included"/>"""
        )
        val xml = """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical">
            <include layout="@layout/included"/>
        </LinearLayout>"""
        val graph = (plugin.parse(xmlArtifact(xml, root.resolve("res/layout/main.xml").absolutePath), PreviewContext()) as com.foundry.core.plugin.ParseResult.Success).graph
        // include 展开后，根（Column）应含一个 Text 子节点，文本来自被 include 的布局
        val child = graph.root?.children?.first()
        assertEquals("Text", child?.type)
        assertEquals("Included", child?.attributes?.get("text")?.raw)
    }

    @Test
    fun `style is merged into element attributes via style table`() = runBlocking {
        val root = tempAndroidProject()
        File(root, "res/values/styles.xml").writeText(
            """<resources>
                <style name="TitleText">
                    <item name="android:textColor">#FF0000</item>
                    <item name="android:textSize">20sp</item>
                </style>
            </resources>"""
        )
        val xml = """<TextView xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            style="@style/TitleText" android:text="Hello"/>
        """
        val graph = (plugin.parse(xmlArtifact(xml, root.resolve("res/layout/main.xml").absolutePath), PreviewContext()) as com.foundry.core.plugin.ParseResult.Success).graph
        // @style 合并的 textColor / textSize 应出现在解析结果中（经属性映射为 color / fontSize）
        // 注意：颜色经 normalizeColor 补全 alpha 通道
        assertEquals("#FFFF0000", graph.root?.attributes?.get("color")?.raw)
        assertEquals("20.0", graph.root?.attributes?.get("fontSize")?.raw)
        assertEquals("Hello", graph.root?.attributes?.get("text")?.raw)
    }

    @Test
    fun `gone child is dropped before rendering`() = runBlocking {
        val xml = """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent" android:layout_height="wrap_content" android:orientation="vertical">
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="Visible"/>
            <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                android:text="Hidden" android:visibility="gone"/>
        </LinearLayout>"""
        val graph = (plugin.parse(xmlArtifact(xml), PreviewContext()) as com.foundry.core.plugin.ParseResult.Success).graph
        // graph 层仍保留 visibility=gone 属性（用于诊断）
        val goneNode = graph.root?.children?.firstOrNull { it.attributes["visibility"]?.raw == "gone" }
        assertNotNull("gone node preserved at graph layer", goneNode)
        // 渲染层消费的是 toUiDocument 后的 UiElement 树，gone 节点应被剔除
        val doc = com.foundry.preview.plugin.toUiDocument(graph)
        val visibleChildren = doc.root.children
        assertEquals(1, visibleChildren.size)
        assertEquals("Visible", visibleChildren.first().attributes["text"])
    }
}
