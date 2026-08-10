package com.foundry.preview.project

import com.foundry.core.uimodel.ResourceTable
import java.io.File

private val VALUE_TAG_RE = Regex(
    "<(string|color|dimen)\\s+name=\"([^\"]+)\"\\s*>([\\s\\S]*?)</\\1>",
    RegexOption.IGNORE_CASE
)

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
