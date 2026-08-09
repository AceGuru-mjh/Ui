package com.foundry.preview.project

import com.foundry.core.plugin.ArtifactDetector
import com.foundry.core.plugin.ArtifactKind
import java.io.File

/**
 * 项目级索引器（Stage 4 预览原型的最小实现）。
 *
 * 扫描一个目录树，按扩展名 / 内容结构用 [ArtifactDetector] 把文件分类为
 * 「可预览工件」（JSON DSL / Android XML Layout / Compose 源码）与「其它资源」，
 * 供上层 UI 列出「这个项目里有哪些东西可以预览」。
 *
 * 纯 Kotlin（仅用 java.io.File），不依赖 Android，可在 CLI / 桌面 / 测试复用。
 * 当前不做跨文件资源合并（strings.xml 合并等），那是后续阶段。
 */
object ProjectIndexer {

    /** 可预览的工件种类（白名单）。 */
    private val PREVIEWABLE = setOf(
        ArtifactKind.JSON_DSL,
        ArtifactKind.ANDROID_XML_LAYOUT,
        ArtifactKind.KOTLIN_COMPOSE
    )

    data class IndexedFile(
        val path: String,
        val name: String,
        val kind: ArtifactKind,
        val previewable: Boolean,
        val sizeBytes: Long
    )

    data class ProjectIndex(
        val rootPath: String,
        val files: List<IndexedFile>,
        val previewableCount: Int,
        val scannedDirs: Int
    )

    /**
     * 扫描 [rootDir]（本地文件系统路径）。
     * @param maxDepth 最大递归深度（默认 8，避免无限遍历）。
     * @param readContentForDetection 是否读取文件内容做结构探测（更准但更慢）；
     *        关闭时仅按扩展名判定。
     */
    fun index(rootDir: String, maxDepth: Int = 8, readContentForDetection: Boolean = true): ProjectIndex {
        val root = File(rootDir)
        if (!root.exists() || !root.isDirectory) {
            return ProjectIndex(rootDir, emptyList(), 0, 0)
        }
        val files = mutableListOf<IndexedFile>()
        var dirs = 0
        walk(root, 0, maxDepth, readContentForDetection, files, { dirs++ })
        val previewable = files.filter { it.previewable }
        return ProjectIndex(rootDir, files.sortedBy { it.path }, previewable.size, dirs)
    }

    private fun walk(
        dir: File,
        depth: Int,
        maxDepth: Int,
        readContent: Boolean,
        acc: MutableList<IndexedFile>,
        onDir: () -> Unit
    ) {
        onDir()
        if (depth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (f in children) {
            when {
                f.isDirectory -> {
                    if (!f.name.startsWith(".") && f.name != "build" && f.name != "buildTmp") {
                        walk(f, depth + 1, maxDepth, readContent, acc, onDir)
                    }
                }
                f.isFile -> {
                    val kind = classify(f, readContent)
                    acc += IndexedFile(
                        path = f.absolutePath,
                        name = f.name,
                        kind = kind,
                        previewable = kind in PREVIEWABLE,
                        sizeBytes = f.length()
                    )
                }
            }
        }
    }

    private fun classify(file: File, readContent: Boolean): ArtifactKind {
        val ext = file.extension.lowercase()
        if (readContent && file.length() < 512 * 1024) {
            runCatching {
                val content = file.readText(Charsets.UTF_8)
                if (content.isNotBlank()) {
                    ArtifactDetector.detectFromContent(content)?.let { return it }
                }
            }
        }
        return ArtifactDetector.detectFromExtension(ext) ?: ArtifactKind.UNKNOWN_TEXT
    }
}
