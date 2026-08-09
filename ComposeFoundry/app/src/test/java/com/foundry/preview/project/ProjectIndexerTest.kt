package com.foundry.preview.project

import com.foundry.core.plugin.ArtifactKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class ProjectIndexerTest {

    private fun tempProject(): File {
        val root = File(System.getProperty("java.io.tmpdir"), "foundry-proj-${UUID.randomUUID()}")
        root.deleteOnExit()
        File(root, "ui").mkdirs()
        File(root, "ui/MainActivity.kt").writeText(
            """
            import androidx.compose.runtime.Composable
            @Composable fun Home() { }
            """.trimIndent()
        )
        File(root, "res/layout").mkdirs()
        File(root, "res/layout/activity_main.xml").writeText(
            """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"/>"""
        )
        File(root, "screen.json").writeText("""{"type":"column","root":{}}""")
        File(root, "README.md").writeText("# hi")
        return root
    }

    @Test
    fun `indexes previewable files and skips unrelated`() {
        val root = tempProject()
        val index = ProjectIndexer.index(root.absolutePath, readContentForDetection = true)
        val kinds = index.files.map { it.kind to it.previewable }
        assertTrue("kt composable previewable", kinds.any { it.first == ArtifactKind.KOTLIN_COMPOSE && it.second })
        assertTrue("xml layout previewable", kinds.any { it.first == ArtifactKind.ANDROID_XML_LAYOUT && it.second })
        assertTrue("json dsl previewable", kinds.any { it.first == ArtifactKind.JSON_DSL && it.second })
        assertTrue("md not previewable", kinds.any { it.first == ArtifactKind.UNKNOWN_TEXT && !it.second })
        assertEquals(3, index.previewableCount)
    }

    @Test
    fun `non-existent directory yields empty index`() {
        val index = ProjectIndexer.index("/no/such/dir/xyz", readContentForDetection = true)
        assertEquals(0, index.files.size)
        assertEquals(0, index.previewableCount)
    }

    @Test
    fun `plain kotlin file without compose is not previewable`() {
        val root = File(System.getProperty("java.io.tmpdir"), "foundry-proj-${UUID.randomUUID()}")
        root.deleteOnExit()
        File(root, "Util.kt").writeText(
            """
            fun greet(name: String): String = "hi $name"
            class Helper { val x = 1 }
            """.trimIndent()
        )
        val index = ProjectIndexer.index(root.absolutePath, readContentForDetection = true)
        val util = index.files.first { it.name == "Util.kt" }
        assertEquals(ArtifactKind.UNKNOWN_TEXT, util.kind)
        assertTrue("plain .kt must not be previewable", !util.previewable)
        assertEquals(0, index.previewableCount)
    }

    @Test
    fun `composable kotlin file is previewable`() {
        val root = File(System.getProperty("java.io.tmpdir"), "foundry-proj-${UUID.randomUUID()}")
        root.deleteOnExit()
        File(root, "Home.kt").writeText(
            """
            import androidx.compose.runtime.Composable
            @Composable fun Home() { }
            """.trimIndent()
        )
        val index = ProjectIndexer.index(root.absolutePath, readContentForDetection = true)
        val home = index.files.first { it.name == "Home.kt" }
        assertEquals(ArtifactKind.KOTLIN_COMPOSE, home.kind)
        assertTrue("composable .kt is previewable", home.previewable)
    }
}
