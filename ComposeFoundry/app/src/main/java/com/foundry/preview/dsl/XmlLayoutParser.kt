package com.foundry.preview.dsl

/**
 * Android XML layout 解析器。
 *
 * 不依赖 Android 的 [org.xmlpull.v1.XmlPullParserFactory]（在纯 JVM 单元测试中无法 mock），
 * 改用内置的轻量纯 Kotlin 标签读取器 [SimpleXmlReader]，便于单元测试且对 KMP 友好。
 * 布局 XML 的语义全在属性中，因此本解析器只关心「开始标签 + 属性 + 结束标签」，忽略文本节点。
 */
class XmlLayoutParser {

    private class MutableNode(
        val type: String,
        val attributes: MutableMap<String, String> = mutableMapOf(),
        var modifier: UiModifierSpec = UiModifierSpec(),
        val children: MutableList<MutableNode> = mutableListOf()
    )

    /** 解析结果：成功解析的 DSL 根节点 + 本次解析产生的（降级/忽略）问题清单。 */
    data class XmlParseResult(val root: UiElement, val issues: List<String>)

    fun parse(xmlString: String): Result<XmlParseResult> {
        return try {
            val events = SimpleXmlReader.read(xmlString)
            var root: MutableNode? = null
            val stack = ArrayDeque<MutableNode>()
            val issues = mutableListOf<String>()

            for (event in events) {
                when (event) {
                    is SimpleXmlReader.XmlEvent.StartTag -> {
                        val node = mapTag(event.tag, event.attributes, issues)
                        if (stack.isEmpty()) {
                            root = node
                        } else {
                            stack.last().children.add(node)
                        }
                        if (!event.selfClosing) stack.addLast(node)
                    }
                    is SimpleXmlReader.XmlEvent.EndTag -> {
                        if (stack.isNotEmpty()) stack.removeLast()
                    }
                }
            }

            if (root != null) {
                Result.success(XmlParseResult(toUiElement(root), issues))
            } else {
                Result.failure(Exception("No root element found in XML"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 已支持的 android 命名空间属性白名单；白名单外属性视为不支持并忽略（记录诊断）
    private val SUPPORTED_ANDROID_ATTRS = setOf(
        "android:layout_width", "android:layout_height", "android:orientation",
        "android:padding", "android:paddingStart", "android:paddingTop",
        "android:paddingEnd", "android:paddingBottom",
        "android:background", "android:text", "android:hint",
        "android:contentDescription", "android:textSize", "android:textColor",
        "android:textStyle", "android:gravity", "android:src", "android:id",
        "android:layout_gravity", "android:layout_weight", "android:visibility",
        "android:onClick", "android:inputType", "android:maxLines",
        "android:ellipsize", "android:scaleType"
    )

    private fun mapTag(tag: String, attrs: Map<String, String>, issues: MutableList<String>): MutableNode {
        val supported = setOf(
            "LinearLayout", "FrameLayout", "RelativeLayout", "ConstraintLayout",
            "TextView", "Button", "ImageView", "EditText", "View", "ScrollView",
            "androidx.cardview.widget.CardView",
            "androidx.constraintlayout.widget.ConstraintLayout"
        )
        if (tag !in supported) {
            issues += "Unsupported tag '<$tag>' — downgraded to Box (rendering may differ)"
        }

        val type = when (tag) {
            "LinearLayout" -> {
                val orientation = attrs["android:orientation"] ?: "vertical"
                if (orientation == "horizontal") "Row" else "Column"
            }
            "FrameLayout" -> "Box"
            "RelativeLayout" -> "Box"
            "ConstraintLayout" -> "Box"
            "TextView" -> "Text"
            "Button" -> "Button"
            "ImageView" -> "Image"
            "EditText" -> "TextField"
            "View" -> "Spacer"
            "ScrollView" -> "Scroll"
            "androidx.cardview.widget.CardView" -> "Card"
            "androidx.constraintlayout.widget.ConstraintLayout" -> "Box"
            else -> "Box"
        }

        val elementAttrs = mutableMapOf<String, String>()
        var modifier = UiModifierSpec()

        attrs["android:layout_width"]?.let { w ->
            when {
                w == "match_parent" -> modifier = modifier.copy(fillMaxWidth = true)
                w.endsWith("dp") -> modifier = modifier.copy(width = w.removeSuffix("dp").toFloatOrNull() ?: 0f)
            }
        }

        attrs["android:layout_height"]?.let { h ->
            when {
                h == "match_parent" -> modifier = modifier.copy(fillMaxHeight = true)
                h.endsWith("dp") -> modifier = modifier.copy(height = h.removeSuffix("dp").toFloatOrNull() ?: 0f)
            }
        }

        attrs["android:padding"]?.let { p ->
            val dp = parseDim(p)
            if (dp != null) modifier = modifier.copy(padding = PaddingSpec(all = dp))
        }

        attrs["android:paddingStart"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(padding = (modifier.padding ?: PaddingSpec()).copy(start = it)) } }
        attrs["android:paddingTop"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(padding = (modifier.padding ?: PaddingSpec()).copy(top = it)) } }
        attrs["android:paddingEnd"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(padding = (modifier.padding ?: PaddingSpec()).copy(end = it)) } }
        attrs["android:paddingBottom"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(padding = (modifier.padding ?: PaddingSpec()).copy(bottom = it)) } }

        attrs["android:background"]?.let { bg ->
            modifier = if (bg.startsWith("#")) {
                modifier.copy(background = normalizeColor(bg))
            } else {
                // 资源引用（@color/...）等保留原始值，交给后续资源解析阶段处理
                modifier.copy(background = bg)
            }
        }

        attrs["android:text"]?.let { elementAttrs["text"] = it }
        attrs["android:hint"]?.let { elementAttrs["placeholder"] = it }
        attrs["android:contentDescription"]?.let { elementAttrs["contentDescription"] = it }

        attrs["android:textSize"]?.let { ts ->
            val sp = parseDim(ts)
            if (sp != null) elementAttrs["fontSize"] = sp.toString()
        }

        attrs["android:textColor"]?.let { tc ->
            if (tc.startsWith("#")) elementAttrs["color"] = normalizeColor(tc)
        }

        attrs["android:textStyle"]?.let { style ->
            if (style.contains("bold")) elementAttrs["fontWeight"] = "bold"
        }

        attrs["android:gravity"]?.let { gravity ->
            when {
                gravity.contains("center") -> {
                    if (type == "Column") modifier = modifier.copy(horizontalAlignment = "center")
                    if (type == "Row") modifier = modifier.copy(verticalAlignment = "center")
                    if (type == "Box") modifier = modifier.copy(contentAlignment = "center")
                }
            }
        }

        // 记录白名单外的 android 属性（视为未支持并忽略）
        attrs.keys.filter { it.startsWith("android:") && it !in SUPPORTED_ANDROID_ATTRS }
            .forEach { issues += "Ignored unsupported attribute '$it' on <$tag>" }

        return MutableNode(type, elementAttrs, modifier)
    }


    private fun parseDim(value: String): Float? {
        return when {
            value.endsWith("dp") -> value.removeSuffix("dp").toFloatOrNull()
            value.endsWith("sp") -> value.removeSuffix("sp").toFloatOrNull()
            value.endsWith("dip") -> value.removeSuffix("dip").toFloatOrNull()
            else -> value.toFloatOrNull()
        }
    }

    private fun normalizeColor(color: String): String {
        return when (color.length) {
            4 -> {
                val r = color[1]; val g = color[2]; val b = color[3]
                "#FF$r$r$g$g$b$b"
            }
            7 -> "#FF${color.substring(1)}"
            9 -> color
            else -> color
        }
    }

    private fun toUiElement(node: MutableNode): UiElement {
        return UiElement(
            type = node.type,
            modifier = node.modifier,
            attributes = node.attributes.toMap(),
            children = node.children.map { toUiElement(it) }
        )
    }
}
