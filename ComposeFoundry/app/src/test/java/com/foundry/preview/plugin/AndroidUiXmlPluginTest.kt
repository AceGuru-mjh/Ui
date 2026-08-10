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
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUiXmlPluginTest {

    private val plugin = AndroidUiXmlPlugin()

    private fun xmlArtifact(content: String) = UiArtifact(
        id = "x", uri = "", displayName = "x",
        content = content, extension = "xml",
        detectedKind = ArtifactKind.ANDROID_XML_LAYOUT
    )

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
}
