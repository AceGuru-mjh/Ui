package com.foundry.preview.plugin

import com.foundry.core.uimodel.*
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 验证 Stage 3 渲染适配层：规范化 UiGraph(UiNode) → legacy dsl.UiElement 的无损映射，
 * 使现有 ComponentRenderer 可直接以 UiGraph 为渲染源。
 */
class UiGraphAdaptersTest {

    @Test
    fun toUiElement_mapsTypeAttributesModifierAndChildren() {
        val node = UiNode(
            id = "root",
            type = "column",
            attributes = mapOf("arrangement" to UiValue.Text("center")),
            modifiers = listOf(
                UiModifier(fillMaxWidth = true, padding = PaddingSpec(all = 8f))
            ),
            children = listOf(
                UiNode(
                    id = "root.children[0]",
                    type = "text",
                    attributes = mapOf("text" to UiValue.Text("hi"))
                )
            )
        )

        val el = toUiElement(node)

        assertEquals("column", el.type)
        assertEquals("center", el.attributes["arrangement"])
        assertEquals(true, el.modifier.fillMaxWidth)
        assertEquals(8f, el.modifier.padding?.all)
        assertEquals(1, el.children.size)
        assertEquals("text", el.children[0].type)
        assertEquals("hi", el.children[0].attributes["text"])
    }

    @Test
    fun toUiElement_preservesResourceRefAsRaw() {
        val node = UiNode(
            id = "r",
            type = "text",
            attributes = mapOf("text" to UiValue.ResourceRef("@string/title_home"))
        )
        assertEquals("@string/title_home", toUiElement(node).attributes["text"])
    }

    @Test
    fun toUiDocument_usesFirstThemeForBackgroundAndText() {
        val graph = UiGraph(
            id = "g",
            sourceArtifactId = "g",
            root = UiNode(id = "root", type = "Box"),
            themes = listOf(
                ThemeSpec(
                    name = "t",
                    attributes = mapOf(
                        "backgroundColor" to UiValue.ColorVal("#FF000000"),
                        "textColor" to UiValue.ColorVal("#FFFFFFFF")
                    )
                )
            ),
            meta = UiGraphMeta(
                title = "g",
                format = "x",
                parser = "p",
                parserVersion = "1",
                previewLevel = PreviewLevel.L1_STRUCTURE,
                confidence = Confidence.HIGH
            )
        )

        val doc = toUiDocument(graph)
        assertEquals("#FF000000", doc.theme.backgroundColor)
        assertEquals("#FFFFFFFF", doc.theme.textColor)
    }

    @Test
    fun toUiElement_nullRootFallsBackToBox() {
        val graph = UiGraph(
            id = "g",
            sourceArtifactId = "g",
            root = null,
            meta = UiGraphMeta(
                title = "g",
                format = "x",
                parser = "p",
                parserVersion = "1",
                previewLevel = PreviewLevel.L0_METADATA,
                confidence = Confidence.NONE
            )
        )
        assertEquals("Box", toUiDocument(graph).root.type)
    }
}
