package com.foundry.core.uimodel

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class UiModelTest {

    @Test
    fun uiValue_serialization_roundTrip() {
        val values = listOf(
            UiValue.Text("hi"),
            UiValue.Number(1.5),
            UiValue.Bool(true),
            UiValue.ColorVal("#FF0000"),
            UiValue.Dimen("8dp"),
            UiValue.ResourceRef("@string/x"),
            UiValue.Expression("if(a) b else c"),
            UiValue.Unknown("?")
        )
        val json = Json.encodeToString(values)
        val back = Json.decodeFromString<List<UiValue>>(json)
        assertEquals(values, back)
    }

    @Test
    fun uiGraph_serialization_roundTrip() {
        val node = UiNode(
            id = "root",
            type = "Column",
            attributes = mapOf("text" to UiValue.Text("t")),
            modifiers = listOf(UiModifier(fillMaxWidth = true))
        )
        val graph = UiGraph(
            id = "g",
            sourceArtifactId = "g",
            root = node,
            themes = listOf(ThemeSpec("default", attributes = mapOf("backgroundColor" to UiValue.ColorVal("#FFF5F5F5")))),
            capabilities = setOf(UiCapability.PARSE, UiCapability.RENDER_STATIC),
            meta = UiGraphMeta(
                title = "t",
                format = "json",
                parser = "p",
                parserVersion = "1.0",
                previewLevel = PreviewLevel.L3_STATIC_VISUAL,
                confidence = Confidence.HIGH
            )
        )
        val json = Json.encodeToString(graph)
        val back = Json.decodeFromString<UiGraph>(json)
        assertEquals(graph, back)
        assertNotNull(back.root)
    }

    @Test
    fun withDiagnostics_appendsWithoutMutation() {
        val g = UiGraphs.empty("x")
        val withD = g.withDiagnostic(Diagnostic(Severity.INFO, "C", "msg", "src"))
        assertEquals(0, g.diagnostics.size)
        assertEquals(1, withD.diagnostics.size)
    }

    @Test
    fun findNode_depthFirst() {
        val leaf = UiNode(id = "leaf", type = "Text")
        val root = UiNode(
            id = "root",
            type = "Column",
            children = listOf(UiNode(id = "mid", type = "Row", children = listOf(leaf)))
        )
        val graph = UiGraph(
            id = "g", sourceArtifactId = "g", root = root,
            meta = UiGraphMeta("t", "json", "p", "1.0", PreviewLevel.L1_STRUCTURE, Confidence.HIGH)
        )
        assertEquals("leaf", graph.findNode { it.type == "Text" }?.id)
        assertEquals(3, graph.nodeCount)
    }
}
