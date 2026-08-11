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
        root.mkdirs()
        File(root, "Util.kt").writeText(
            """
            fun greet(name: String): String = "hi ${'$'}name"
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
        root.mkdirs()
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

    @Test
    fun `nested directories are all scanned`() {
        val root = File(System.getProperty("java.io.tmpdir"), "foundry-proj-${UUID.randomUUID()}")
        root.deleteOnExit()
        File(root, "a/b/c").mkdirs()
        File(root, "a/b/c/deep.json").writeText("""{"type":"column","root":{}}""")
        File(root, "a/level.kt").writeText(
            """
            import androidx.compose.runtime.Composable
            @Composable fun L() { }
            """.trimIndent()
        )
        val index = ProjectIndexer.index(root.absolutePath, readContentForDetection = true)
        assertTrue("deep json found", index.files.any { it.name == "deep.json" && it.previewable })
        assertTrue("nested kt found", index.files.any { it.name == "level.kt" && it.previewable })
        assertEquals(2, index.previewableCount)
    }

    @Test
    fun `dot directories and build directories are skipped`() {
        val root = File(System.getProperty("java.io.tmpdir"), "foundry-proj-${UUID.randomUUID()}")
        root.deleteOnExit()
        File(root, ".git").mkdirs()
        File(root, "build").mkdirs()
        File(root, "buildTmp").mkdirs()
        File(root, ".git/ignore_me.json").writeText("""{"type":"column","root":{}}""")
        File(root, "build/compiled.json").writeText("""{"type":"column","root":{}}""")
        File(root, "buildTmp/also.json").writeText("""{"type":"column","root":{}}""")
        File(root, "real.json").writeText("""{"type":"column","root":{}}""")
        val index = ProjectIndexer.index(root.absolutePath, readContentForDetection = true)
        // 隐藏/构建目录中的文件不应被扫描
        assertEquals(1, index.previewableCount)
        assertTrue(index.files.all { !it.path.contains("/.git/") && !it.path.contains("/build/") })
    }

    @Test
    fun `maxDepth limits recursion`() {
        val root = File(System.getProperty("java.io.tmpdir"), "foundry-proj-${UUID.randomUUID()}")
        root.deleteOnExit()
        // 构造深度 5 的嵌套：root/d1/d2/d3/d4/d5/file.json
        var dir = root
        val depthDirs = listOf("d1", "d2", "d3", "d4", "d5")
        for (d in depthDirs) {
            dir = File(dir, d)
            dir.mkdirs()
        }
        File(dir, "deep.json").writeText("""{"type":"column","root":{}}""")
        File(root, "shallow.json").writeText("""{"type":"column","root":{}}""")
        // maxDepth=2：从 root(0)→d1(1)→d2(2) 后即停止，d5 内的文件不应被扫描
        val index = ProjectIndexer.index(root.absolutePath, maxDepth = 2, readContentForDetection = true)
        assertTrue("shallow file indexed", index.files.any { it.name == "shallow.json" })
        assertTrue("deep file beyond maxDepth excluded", index.files.none { it.name == "deep.json" })
    }

    @Test
    fun `binary files are not classified as previewable text`() {
        val root = File(System.getProperty("java.io.tmpdir"), "foundry-proj-${UUID.randomUUID()}")
        root.deleteOnExit()
        root.mkdirs()
        // 写入一个包含控制字符的"二进制"文件（伪装成 .bin）
        File(root, "blob.bin").writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x1B.toByte(), 0x7F.toByte()))
        // 纯二进制（无可打印 token）应落到 UNKNOWN_BINARY 且不可预览
        val index = ProjectIndexer.index(root.absolutePath, readContentForDetection = true)
        val blob = index.files.first { it.name == "blob.bin" }
        assertEquals(ArtifactKind.UNKNOWN_BINARY, blob.kind)
        assertTrue("binary not previewable", !blob.previewable)
    }

    @Test
    fun `large files skip content detection and fall back to extension`() {
        val root = File(System.getProperty("java.io.tmpdir"), "foundry-proj-${UUID.randomUUID()}")
        root.deleteOnExit()
        root.mkdirs()
        // 超过 512KB 的内容探测阈值，仅靠扩展名判定
        val big = StringBuilder().apply {
            repeat(600_000) { append("a") }
            append("""{"type":"column","root":{}}""") // 内容探测会被跳过
        }.toString()
        File(root, "big.json").writeText(big)
        val index = ProjectIndexer.index(root.absolutePath, readContentForDetection = true)
        val file = index.files.first { it.name == "big.json" }
        // 因内容探测跳过，仅按扩展名 → JSON_DSL
        assertEquals(ArtifactKind.JSON_DSL, file.kind)
        assertTrue(file.previewable)
    }

    @Test
    fun `manifest and menu xml are not previewable layouts`() {
        val root = File(System.getProperty("java.io.tmpdir"), "foundry-proj-${UUID.randomUUID()}")
        root.deleteOnExit()
        root.mkdirs()
        File(root, "AndroidManifest.xml").writeText(
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application><activity android:name=".Main"/></application>
            </manifest>"""
        )
        File(root, "menu_main.xml").writeText(
            """<menu xmlns:android="http://schemas.android.com/apk/res/android">
                <item android:id="@+id/action" android:title="X"/>
            </menu>"""
        )
        val index = ProjectIndexer.index(root.absolutePath, readContentForDetection = true)
        val manifest = index.files.first { it.name == "AndroidManifest.xml" }
        val menu = index.files.first { it.name == "menu_main.xml" }
        assertEquals(ArtifactKind.ANDROID_MANIFEST, manifest.kind)
        assertEquals(ArtifactKind.ANDROID_MENU, menu.kind)
        assertTrue("manifest not previewable", !manifest.previewable)
        assertTrue("menu not previewable", !menu.previewable)
    }

    @Test
    fun `scanned dirs count excludes skipped directories`() {
        val root = File(System.getProperty("java.io.tmpdir"), "foundry-proj-${UUID.randomUUID()}")
        root.deleteOnExit()
        File(root, "sub").mkdirs()
        File(root, "build").mkdirs()
        File(root, ".gradle").mkdirs()
        File(root, "sub/a.xml").writeText("""<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"/>""")
        val index = ProjectIndexer.index(root.absolutePath, readContentForDetection = true)
        // root + sub = 2 个被扫描目录；build / .gradle 应被跳过不计入
        assertEquals(2, index.scannedDirs)
    }
}
