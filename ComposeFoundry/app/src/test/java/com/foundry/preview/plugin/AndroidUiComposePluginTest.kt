package com.foundry.preview.plugin

import com.foundry.core.plugin.ArtifactDetector
import com.foundry.core.plugin.ArtifactKind
import com.foundry.core.plugin.ParseResult
import com.foundry.core.plugin.PreviewContext
import com.foundry.core.plugin.UiArtifact
import com.foundry.core.uimodel.Confidence
import com.foundry.core.uimodel.Severity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidUiComposePluginTest {

    private val plugin = AndroidUiComposePlugin()

    private fun ktArtifact(content: String) = UiArtifact(
        id = "k", uri = "", displayName = "k",
        content = content, extension = "kt",
        detectedKind = ArtifactDetector.detect(UiArtifact(id = "k", uri = "", displayName = "k", content = content, extension = "kt"))
    )

    @Test
    fun `detects compose source by content`() {
        val src = """import androidx.compose.runtime.Composable
            import androidx.compose.foundation.layout.Column
            @Composable fun Home() { Column { } }"""
        val artifact = ktArtifact(src)
        assertEquals(ArtifactKind.KOTLIN_COMPOSE, artifact.detectedKind)
        assertTrue(plugin.canHandle(artifact).score > 0)
    }

    @Test
    fun `parses column with text child into ui graph`() = runBlocking {
        val src = """
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable

            @Composable
            fun Home() {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Hello", fontSize = 18.sp)
                    Text(text = "World")
                }
            }
        """
        val result = plugin.parse(ktArtifact(src), PreviewContext())
        val graph = (result as ParseResult.Success).graph
        assertEquals("Column", graph.root?.type)
        assertTrue(graph.root?.modifiers?.firstOrNull()?.fillMaxWidth == true)
        assertEquals(16f, graph.root?.modifiers?.firstOrNull()?.padding?.all)
        val children = graph.root?.children ?: emptyList()
        assertEquals(2, children.size)
        assertEquals("Text", children[0].type)
        assertEquals("Hello", children[0].attributes["text"]?.raw)
        assertEquals("18", children[1].attributes["fontSize"]?.raw)
    }

    @Test
    fun `unsupported composable is downgraded to box with warning`() = runBlocking {
        val src = """
            import androidx.compose.runtime.Composable
            @Composable fun Weird() { MyCustomWidget(label = "x") }
        """
        val result = plugin.parse(ktArtifact(src), PreviewContext())
        val graph = (result as ParseResult.Success).graph
        assertEquals("Box", graph.root?.type)
        assertTrue(graph.diagnostics.any { it.severity == Severity.WARNING && "MyCustomWidget" in it.message })
    }

    @Test
    fun `no composable function yields failed result`() = runBlocking {
        val src = """fun notUi() = 42"""
        val result = plugin.parse(ktArtifact(src), PreviewContext())
        assertTrue(result is ParseResult.Failed)
    }
}
