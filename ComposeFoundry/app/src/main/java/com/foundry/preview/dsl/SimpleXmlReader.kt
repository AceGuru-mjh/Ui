package com.foundry.preview.dsl

/**
 * 极简纯 Kotlin XML 标签读取器（无 Android / 外部依赖）。
 *
 * 仅支持布局解析所需的最小特性：开始标签（含属性）、结束标签、自闭合标签，
 * 并跳过注释、处理指令（<? ?>）、DOCTYPE（<! >）与文本节点。
 * 属性值支持单引号与双引号。足以解析 Android layout / drawable / values 等 XML。
 */
object SimpleXmlReader {

    sealed interface XmlEvent {
        data class StartTag(
            val tag: String,
            val attributes: Map<String, String>,
            val selfClosing: Boolean
        ) : XmlEvent
        data class EndTag(val tag: String) : XmlEvent
    }

    fun read(xml: String): List<XmlEvent> {
        val events = mutableListOf<XmlEvent>()
        var i = 0
        val n = xml.length
        while (i < n) {
            val c = xml[i]
            if (c == '<') {
                // 注释 / 处理指令 / DOCTYPE
                if (xml.startsWith("<!--", i)) {
                    val end = xml.indexOf("-->", i)
                    i = if (end >= 0) end + 3 else n
                    continue
                }
                if (xml.startsWith("<?", i)) {
                    val end = xml.indexOf("?>", i)
                    i = if (end >= 0) end + 2 else n
                    continue
                }
                if (xml.startsWith("<!", i)) {
                    val end = xml.indexOf(">", i)
                    i = if (end >= 0) end + 1 else n
                    continue
                }
                // 标签
                val close = xml.indexOf('>', i)
                if (close < 0) break
                val raw = xml.substring(i + 1, close)
                i = close + 1
                if (raw.startsWith("/")) {
                    // 结束标签
                    val tag = raw.substring(1).trim().substringBefore(' ').substringBefore('/')
                    events += XmlEvent.EndTag(tag)
                } else {
                    val selfClosing = raw.endsWith("/")
                    val body = if (selfClosing) raw.substring(0, raw.length - 1) else raw
                    val tag = body.trim().substringBefore(' ')
                    val attrs = parseAttributes(body)
                    events += XmlEvent.StartTag(tag, attrs, selfClosing)
                }
            } else {
                i++
            }
        }
        return events
    }

    private fun parseAttributes(body: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        var i = 0
        val n = body.length
        while (i < n) {
            // 跳到属性名起始（字母或下划线）
            while (i < n && !body[i].isLetter() && body[i] != '_' && body[i] != '@') i++
            if (i >= n) break
            val nameStart = i
            while (i < n && body[i] != '=' && body[i] != ' ' && body[i] != '>' && body[i] != '/') i++
            val name = body.substring(nameStart, i).trim()
            if (name.isEmpty()) { i++; continue }
            // 跳过 = 与空白
            while (i < n && (body[i] == '=' || body[i] == ' ')) i++
            if (i < n && (body[i] == '"' || body[i] == '\'')) {
                val quote = body[i]
                val valStart = i + 1
                val valEnd = body.indexOf(quote, valStart)
                if (valEnd >= 0) {
                    attrs[name] = body.substring(valStart, valEnd)
                    i = valEnd + 1
                } else {
                    i = n
                }
            } else {
                // 无引号值（罕见），读到空白
                val valStart = i
                while (i < n && body[i] != ' ' && body[i] != '>' && body[i] != '/') i++
                attrs[name] = body.substring(valStart, i)
            }
        }
        return attrs
    }
}
