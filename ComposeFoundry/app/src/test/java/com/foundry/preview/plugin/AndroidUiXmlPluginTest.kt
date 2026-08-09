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
    fun `ignored unsupported attribute is reported`() = runBlocking {
        val xml = """<TextView xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Hi" android:letterSpacing="0.1"/>"""
        val result = plugin.parse(xmlArtifact(xml), PreviewContext())
        val graph = (result as com.foundry.core.plugin.ParseResult.Success).graph
        assertTrue(
            "should report ignored attribute letterSpacing",
            graph.diagnostics.any { "letterSpacing" in it.message }
        )
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
}
