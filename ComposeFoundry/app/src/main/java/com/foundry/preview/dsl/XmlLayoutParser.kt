package com.foundry.preview.dsl

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

class XmlLayoutParser {

    private class MutableNode(
        val type: String,
        val attributes: MutableMap<String, String> = mutableMapOf(),
        var modifier: UiModifierSpec = UiModifierSpec(),
        val children: MutableList<MutableNode> = mutableListOf()
    )

    fun parse(xmlString: String): Result<UiElement> {
        return try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlString))

            var root: MutableNode? = null
            val stack = ArrayDeque<MutableNode>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        val node = parseTag(parser)
                        if (stack.isEmpty()) {
                            root = node
                        } else {
                            stack.last().children.add(node)
                        }
                        stack.addLast(node)
                    }
                    XmlPullParser.END_TAG -> {
                        if (stack.isNotEmpty()) {
                            stack.removeLast()
                        }
                    }
                }
                eventType = parser.next()
            }

            if (root != null) {
                Result.success(toUiElement(root))
            } else {
                Result.failure(Exception("No root element found in XML"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseTag(parser: XmlPullParser): MutableNode {
        val tag = parser.name
        val attrs = mutableMapOf<String, String>()
        for (i in 0 until parser.attributeCount) {
            attrs[parser.getAttributeName(i)] = parser.getAttributeValue(i)
        }
        return mapTag(tag, attrs)
    }

    private fun mapTag(tag: String, attrs: Map<String, String>): MutableNode {
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
            if (bg.startsWith("#")) {
                modifier = modifier.copy(background = normalizeColor(bg))
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
