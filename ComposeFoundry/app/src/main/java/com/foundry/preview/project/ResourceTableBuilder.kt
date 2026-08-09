package com.foundry.preview.project

import com.foundry.core.uimodel.ResourceTable
import java.io.File

/**
 * 从 Android 资源目录扫描并构建 [ResourceTable]，用于解析 @string/@color/@dimen 引用。
 *
 * 解析 res/values/*.xml 中的 <string>/<color>/<dimen> 条目；对目录/文件缺失或
 * 解析失败完全容错（跳过并继续，绝不抛异常）。
 */

private val VALUE_TAG_RE = Regex(
    "<(string|color|dimen)\\s+name=\"([^\"]+)\"\\s*>([\\s\\S]*?)</\\1>",
    RegexOption.IGNORE_CASE
)

/** 扫描 [start] 所在工程（向上查找含 res/ 或 AndroidManifest.xml 的根），构建资源表。 */
fun buildResourceTable(start: File): ResourceTable {
    val root = findAndroidProjectRoot(start) ?: start
    val valuesDir = File(root, "res/values")
    if (!valuesDir.isDirectory) return ResourceTable()

    val strings = mutableMapOf<String, String>()
    val colors = mutableMapOf<String, String>()
    val dimens = mutableMapOf<String, String>()

    valuesDir.listFiles()
        ?.filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
        ?.forEach { file -> runCatching { parseValuesFile(file, strings, colors, dimens) } }

    return ResourceTable(strings = strings, colors = colors, dimens = dimens)
}

/** 向上回溯目录树（最多 6 层），找到含 res/ 或 AndroidManifest.xml 的工程根；找不到返回 null。 */
fun findAndroidProjectRoot(start: File): File? {
    var dir = if (start.isDirectory) start else start.parentFile ?: return null
    repeat(6) {
        if (File(dir, "AndroidManifest.xml").isFile || File(dir, "res").isDirectory) return dir
        dir = dir.parentFile ?: return null
    }
    return null
}

private fun parseValuesFile(
    file: File,
    strings: MutableMap<String, String>,
    colors: MutableMap<String, String>,
    dimens: MutableMap<String, String>
) {
    val xml = file.readText(Charsets.UTF_8)
    VALUE_TAG_RE.findAll(xml).forEach { m ->
        val type = m.groupValues[1].lowercase()
        val name = m.groupValues[2]
        val raw = m.groupValues[3]
        val text = stripInnerTags(raw).unescapeXml().trim()
        if (text.isNotBlank()) {
            when (type) {
                "string" -> strings[name] = text
                "color" -> colors[name] = text
                "dimen" -> dimens[name] = text
            }
        }
    }
}

private fun stripInnerTags(s: String): String = s.replace(Regex("<[^>]+>"), "")

private fun String.unescapeXml(): String =
    this.replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&#(\\d+);".toRegex()) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: it.value }
        .replace("&#x([0-9a-fA-F]+);".toRegex()) {
            it.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: it.value
        }
