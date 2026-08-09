package com.foundry.core.plugin

import com.foundry.core.uimodel.Confidence
import com.foundry.core.uimodel.UiCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManagerTest {

    private fun fakePlugin(id: String, kind: ArtifactKind, score: Double): UiFormatPlugin {
        return object : UiFormatPlugin {
            override val descriptor = PluginDescriptor(
                id = id,
                version = "1.0.0",
                displayName = id,
                capabilities = setOf(UiCapability.PARSE),
                supportedExtensions = setOf("x"),
                priority = 0
            )
            override fun canHandle(artifact: UiArtifact): PluginMatch =
                if (artifact.detectedKind == kind) {
                    PluginMatch(score, Confidence.HIGH, "match")
                } else {
                    PluginMatch(0.0, Confidence.NONE, "no")
                }
            override suspend fun parse(artifact: UiArtifact, context: PreviewContext): ParseResult =
                ParseResult.Failed(emptyList())
        }
    }

    private fun artifact(kind: ArtifactKind) =
        UiArtifact(id = "t", uri = "", displayName = "t", detectedKind = kind)

    @Test
    fun selectFor_picksHighestScore() {
        PluginManager.clear()
        PluginManager.register(fakePlugin("a", ArtifactKind.JSON_DSL, 0.5))
        PluginManager.register(fakePlugin("b", ArtifactKind.JSON_DSL, 0.9))
        assertEquals("b", PluginManager.selectFor(artifact(ArtifactKind.JSON_DSL))?.descriptor?.id)
    }

    @Test
    fun selectFor_respectsRequiredCapability() {
        PluginManager.clear()
        PluginManager.register(fakePlugin("a", ArtifactKind.JSON_DSL, 0.9))
        // fakePlugin only declares PARSE; requiring RENDER_INTERACTIVE must yield null
        assertNull(
            PluginManager.selectFor(
                artifact(ArtifactKind.JSON_DSL),
                setOf(UiCapability.RENDER_INTERACTIVE)
            )
        )
    }

    @Test
    fun selectFor_unmatchedReturnsNull() {
        PluginManager.clear()
        PluginManager.register(fakePlugin("a", ArtifactKind.JSON_DSL, 0.9))
        assertNull(PluginManager.selectFor(artifact(ArtifactKind.ANDROID_XML_LAYOUT)))
    }

    @Test
    fun register_ignoresDuplicate() {
        PluginManager.clear()
        val p = fakePlugin("dup", ArtifactKind.JSON_DSL, 0.9)
        PluginManager.register(p)
        PluginManager.register(p)
        assertEquals(1, PluginManager.all().size)
    }

    @Test
    fun registerIfAbsent_reportsNewVsExisting() {
        PluginManager.clear()
        val p = fakePlugin("x", ArtifactKind.JSON_DSL, 0.9)
        assertTrue(PluginManager.registerIfAbsent(p))
        assertFalse(PluginManager.registerIfAbsent(p))
    }

    @Test
    fun describe_listsRegisteredPlugins() {
        PluginManager.clear()
        PluginManager.register(fakePlugin("z", ArtifactKind.JSON_DSL, 0.9))
        assertTrue(PluginManager.describe().contains("z"))
    }
}
