package com.foundry.preview.dsl

/**
 * 轻量 styles.xml 解析：把 <style name="X" parent="Y"> 解析为属性映射，并支持 parent 继承链。
 */
class StyleTable private constructor(
    private val styles: Map<String, StyleEntry>
) {
    private data class StyleEntry(
        val parent: String?,
        val attrs: Map<String, String>
    )

    /** 解析 @style/X（或 X）为合并后的属性表（含继承链，子覆盖父）。找不到返回空。 */
    fun resolve(styleRef: String): Map<String, String> {
        val name = styleRef.removePrefix("@style/").substringBefore('?')
        val out = mutableMapOf<String, String>()
        var current: String? = name
        val chain = mutableListOf<String>()
        // 防止循环继承
        while (current != null && chain.size < 16 && current !in chain) {
            chain += current
            current = styles[current]?.parent
        }
        // 从最外层父到子依次合并（子覆盖父）
        chain.asReversed().forEach { key ->
            styles[key]?.attrs?.let { out.putAll(it) }
        }
        return out
    }

    companion object {
        fun loadFrom(valuesDir: java.io.File?): StyleTable {
            if (valuesDir == null || !valuesDir.isDirectory) return StyleTable(emptyMap())
            val styles = mutableMapOf<String, StyleEntry>()
            valuesDir.listFiles()
                ?.filter { it.isFile && it.extension.equals("xml", ignoreCase = true) }
                ?.forEach { file ->
                    runCatching { parseFile(file, styles) }
                }
            return StyleTable(styles)
        }

        private fun parseFile(file: java.io.File, out: MutableMap<String, StyleEntry>) {
            val xml = file.readText(Charsets.UTF_8)
            // 匹配 <style name="X" parent="Y"> ... </style>
            val styleRe = Regex(
                "<style\\s+name=\"([^\"]+)\"(?:\\s+parent=\"([^\"]+)\")?\\s*>([\\s\\S]*?)</style>",
                RegexOption.IGNORE_CASE
            )
            styleRe.findAll(xml).forEach { m ->
                val name = m.groupValues[1]
                val parent = m.groupValues[2].ifEmpty { null }
                val body = m.groupValues[3]
                val attrs = parseItemAttrs(body)
                out[name] = StyleEntry(parent, attrs)
            }
        }

        private fun parseItemAttrs(body: String): Map<String, String> {
            val map = mutableMapOf<String, String>()
            // <item name="android:layout_width">match_parent</item>
            val itemRe = Regex(
                "<item\\s+name=\"([^\"]+)\"\\s*>([\\s\\S]*?)</item>",
                RegexOption.IGNORE_CASE
            )
            itemRe.findAll(body).forEach { m ->
                val key = m.groupValues[1].trim()
                val value = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
                if (value.isNotEmpty()) map[key] = value
            }
            return map
        }
    }
}
