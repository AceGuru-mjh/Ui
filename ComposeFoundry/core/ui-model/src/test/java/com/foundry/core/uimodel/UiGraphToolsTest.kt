package com.foundry.core.uimodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UiGraphToolsTest {

    @Test
    fun resourceTable_resolve_string_color_dimen() {
        val table = ResourceTable(
            strings = mapOf("title" to "Hello"),
            colors = mapOf("bg" to "#FFFFFF"),
            dimens = mapOf("pad" to "8dp")
        )
        assertEquals(UiValue.Text("Hello"), table.resolve("@string/title"))
        assertEquals(UiValue.ColorVal("#FFFFFF"), table.resolve("@color/bg"))
        assertEquals(UiValue.Dimen("8dp"), table.resolve("@dimen/pad"))
    }

    @Test
    fun resourceTable_resolve_unknownReturnsNull() {
        assertNull(ResourceTable().resolve("@string/missing"))
        assertNull(ResourceTable().resolve("plain"))
    }

    @Test
    fun graph_resolveResources_replacesRefs() {
        val table = ResourceTable(colors = mapOf("bg" to "#FF0000"))
        val node = UiNode(
            id = "r", type = "Box",
            attributes = mapOf("text" to UiValue.ResourceRef("@string/x")),
            modifiers = listOf(UiModifier(background = "@color/bg"))
        )
        val graph = sampleGraph(node, table)
        val resolved = graph.resolveResources()
        val rn = resolved.root!!
        // @string/x 无 strings 表 → 保留原 ResourceRef
        assertEquals(UiValue.ResourceRef("@string/x"), rn.attributes["text"])
        // @color/bg 解析为具体颜色
        assertEquals("#FF0000", rn.modifiers.first().background)
    }

    @Test
    fun graph_validate_emptyRoot() {
        val diags = UiGraphs.empty("x").validate()
        assertTrue(diags.any { it.code == "GRAPH_EMPTY" })
    }

    @Test
    fun graph_validate_flagsDisallowedType() {
        val node = UiNode(id = "r", type = "Weird")
        val diags = sampleGraph(node).validate(ValidationOptions(allowedTypes = setOf("box", "text")))
        assertTrue(diags.any { it.code == "GRAPH_TYPE" })
    }

    @Test
    fun graph_validate_depthExceeded() {
        var node = UiNode(id = "leaf", type = "Text")
        repeat(5) { i -> node = UiNode(id = "n$i", type = "Box", children = listOf(node)) }
        val diags = sampleGraph(node).validate(ValidationOptions(maxDepth = 3))
        assertTrue(diags.any { it.code == "GRAPH_DEPTH" })
    }

    @Test
    fun graph_normalize_assignsIds() {
        val node = UiNode(
            id = "", type = "Box",
            children = listOf(UiNode(id = "", type = "Text"))
        )
        val n = sampleGraph(node).normalize().root!!
        assertEquals("root", n.id)
        assertEquals("root_0", n.children.first().id)
    }

    @Test
    fun capability_negotiation() {
        val avail = setOf(
            UiCapability.PARSE, UiCapability.RENDER_STATIC, UiCapability.RESOURCE_RESOLVE
        )
        assertTrue(avail.satisfies(setOf(UiCapability.PARSE)))
        assertFalse(avail.satisfies(setOf(UiCapability.CODE_EDIT)))
        assertEquals(
            setOf(UiCapability.RENDER_STATIC),
            negotiateCapabilities(avail, setOf(UiCapability.RENDER_STATIC, UiCapability.CODE_EDIT))
        )
    }

    private fun sampleGraph(root: UiNode, resources: ResourceTable = ResourceTable()): UiGraph = UiGraph(
        id = "g",
        sourceArtifactId = "g",
        root = root,
        resources = resources,
        meta = UiGraphMeta("t", "json", "p", "1.0", PreviewLevel.L1_STRUCTURE, Confidence.HIGH)
    )
}
