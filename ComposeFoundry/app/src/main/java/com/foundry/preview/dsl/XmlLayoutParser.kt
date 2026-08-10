package com.foundry.preview.dsl

import com.foundry.core.uimodel.normalizeColor
import java.io.File

/**
 * Android XML layout 解析器。
 *
 * 不依赖 Android 的 [org.xmlpull.v1.XmlPullParserFactory]（在纯 JVM 单元测试中无法 mock），
 * 改用内置的轻量纯 Kotlin 标签读取器 [SimpleXmlReader]，便于单元测试且对 KMP 友好。
 * 布局 XML 的语义全在属性中，因此本解析器只关心「开始标签 + 属性 + 结束标签」，忽略文本节点。
 *
 * 支持：android: 与 app: 命名空间属性、ConstraintLayout 约束近似还原、@style 合并、
 * <include layout="@layout/..."> 与 <merge> 展开。
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

    /**
     * @param styleTable 可选样式表（来自 res/values/styles.xml），用于 @style 合并。
     * @param projectRoot 可选工程根，用于解析 <include layout="@layout/..."> 的内嵌布局。
     */
    fun parse(
        xmlString: String,
        styleTable: StyleTable? = null,
        projectRoot: File? = null
    ): Result<XmlParseResult> {
        return try {
            val events = SimpleXmlReader.read(xmlString)
            var root: MutableNode? = null
            val stack = ArrayDeque<MutableNode>()
            val issues = mutableListOf<String>()
            val visitingIncludes = mutableSetOf<String>() // 防止 include 循环

            for (event in events) {
                when (event) {
                    is SimpleXmlReader.XmlEvent.StartTag -> {
                        if (event.tag == "include") {
                            // 展开 <include layout="@layout/xxx">
                            val layoutRef = event.attributes["layout"] ?: event.attributes["android:layout"]
                            val included = resolveInclude(layoutRef, projectRoot, styleTable, visitingIncludes, issues)
                            if (included != null) {
                                if (stack.isEmpty()) root = included else stack.last().children.add(included)
                            } else {
                                issues += "include 目标布局无法解析，已忽略: $layoutRef"
                            }
                            // include 为自闭合，不入栈
                            continue
                        }
                        val node = mapTag(event.tag, event.attributes, issues, styleTable)
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

    /** 解析 include 引用的内嵌布局，返回其根节点；找不到或循环则 null。 */
    private fun resolveInclude(
        layoutRef: String?,
        projectRoot: File?,
        styleTable: StyleTable?,
        visiting: MutableSet<String>,
        issues: MutableList<String>
    ): MutableNode? {
        if (layoutRef == null) return null
        val name = layoutRef.removePrefix("@layout/").substringBefore('?')
        if (!visiting.add(name)) { issues += "include 循环引用，已中断: $name"; return null }
        val layoutFile = projectRoot?.let { File(it, "res/layout/$name.xml") }
            ?: File("res/layout/$name.xml")
        if (!layoutFile.isFile) { visiting.remove(name); return null }
        val inner = runCatching { layoutFile.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val innerRoot = parse(inner, styleTable, projectRoot).getOrNull()?.root ?: return null
        // <merge> 根：展开其子节点，不引入额外容器
        if (innerRoot.type == "Box" && innerRoot.attributes["__mergeRoot"] == "true") {
            val merged = MutableNode(type = "Box")
            innerRoot.children.forEach { merged.children.add(toMutable(it)) }
            visiting.remove(name)
            return merged
        }
        visiting.remove(name)
        return toMutable(innerRoot)
    }

    private fun toMutable(el: UiElement): MutableNode {
        val n = MutableNode(el.type, el.attributes.toMutableMap(), el.modifier)
        el.children.forEach { n.children.add(toMutable(it)) }
        return n
    }

    // 已支持的 android 命名空间属性白名单；白名单外属性视为不支持并忽略（记录诊断）
    private val SUPPORTED_ANDROID_ATTRS = setOf(
        "android:layout_width", "android:layout_height", "android:layout_margin",
        "android:layout_marginStart", "android:layout_marginTop",
        "android:layout_marginEnd", "android:layout_marginBottom",
        "android:layout_marginHorizontal", "android:layout_marginVertical",
        "android:orientation",
        "android:padding", "android:paddingStart", "android:paddingTop",
        "android:paddingEnd", "android:paddingBottom",
        "android:paddingHorizontal", "android:paddingVertical",
        "android:background", "android:backgroundTint",
        "android:text", "android:hint", "android:contentDescription",
        "android:textSize", "android:textColor", "android:textStyle",
        "android:fontFamily", "android:letterSpacing", "android:lineSpacingExtra",
        "android:gravity", "android:layout_gravity", "android:layout_weight",
        "android:visibility", "android:onClick", "android:inputType",
        "android:maxLines", "android:minLines", "android:lines",
        "android:ellipsize", "android:scaleType", "android:src",
        "android:id", "android:clickable", "android:focusable",
        "android:enabled", "android:alpha", "android:checked",
        "android:progress", "android:max", "android:min",
        "android:scrollbars", "android:fadeScrollbars",
        "android:elevation", "android:translationZ",
        "android:weightSum", "android:importantForAccessibility"
    )

    private fun mapTag(
        tag: String,
        attrs: Map<String, String>,
        issues: MutableList<String>,
        styleTable: StyleTable? = null
    ): MutableNode {
        // <merge> 标签：作为内嵌布局根的特殊处理（展开其子节点，不引入容器）
        if (tag == "merge") {
            val node = MutableNode("Box")
            node.attributes["__mergeRoot"] = "true"
            return node
        }

        // @style 合并：在白名单校验前，把 style 属性浅合并进 attrs（style 优先，attributes 覆盖）
        val attrs = if (styleTable != null) {
            val styleRef = attrs["style"] ?: attrs["android:style"]
            if (styleRef != null) {
                val merged = styleTable.resolve(styleRef).toMutableMap()
                merged.putAll(attrs) // 内联属性覆盖 style
                merged
            } else attrs
        } else attrs

        // 已支持的标签白名单（无重复元素；集中维护，避免多处散落）。
        // 不在白名单内的标签会在下方降级为 Box 并记录 issue。
        val supported = setOf(
            // 原生布局 / 容器
            "LinearLayout", "FrameLayout", "RelativeLayout", "ConstraintLayout",
            "androidx.constraintlayout.widget.ConstraintLayout",
            "androidx.compose.ui.platform.ComposeView",
            "ScrollView", "HorizontalScrollView", "NestedScrollView",
            "androidx.swiperefreshlayout.widget.SwipeRefreshLayout",
            "View", "Space", "Toolbar", "AppBarLayout", "CollapsingToolbarLayout",
            "ViewPager", "WebView", "SurfaceView", "TextureView", "VideoView",
            // 基础控件
            "TextView", "Button", "ImageButton", "ImageView",
            "EditText", "TextInputLayout", "TextInputEditText",
            "ProgressBar", "SeekBar", "CheckBox", "RadioButton", "RadioGroup",
            "Switch", "SwitchCompat", "ToggleButton", "Spinner",
            // Material 组件
            "androidx.cardview.widget.CardView",
            "MaterialButton",
            "com.google.android.material.button.MaterialButton",
            "com.google.android.material.textfield.TextInputLayout",
            "com.google.android.material.textview.MaterialTextView",
            "com.google.android.material.switchMaterial.SwitchMaterial",
            "com.google.android.material.chip.Chip",
            "com.google.android.material.chip.ChipGroup",
            "com.google.android.material.floatingactionbutton.FloatingActionButton",
            "com.google.android.material.tabs.TabLayout",
            "com.google.android.material.divider.MaterialDivider",
            // 列表 / 分页
            "androidx.recyclerview.widget.RecyclerView",
            "androidx.viewpager2.widget.ViewPager2"
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
            "androidx.constraintlayout.widget.ConstraintLayout" -> "Box"
            "TextView" -> "Text"
            "Button" -> "Button"
            "ImageButton" -> "Button"
            "com.google.android.material.button.MaterialButton" -> "Button"
            "com.google.android.material.textview.MaterialTextView" -> "Text"
            "ImageView" -> "Image"
            "EditText" -> "TextField"
            "TextInputEditText" -> "TextField"
            "com.google.android.material.textfield.TextInputLayout" -> "TextField"
            "View" -> "Spacer"
            "Space" -> "Spacer"
            "ScrollView" -> "Scroll"
            "HorizontalScrollView" -> "Scroll"
            "NestedScrollView" -> "Scroll"
            "androidx.cardview.widget.CardView" -> "Card"
            "com.google.android.material.card.MaterialCardView" -> "Card"
            "ProgressBar" -> "ProgressIndicator"
            "CheckBox" -> "Checkbox"
            "RadioButton" -> "RadioButton"
            "RadioGroup" -> if ((attrs["android:orientation"] ?: "vertical") == "horizontal") "Row" else "Column"
            "Switch" -> "Switch"
            "SwitchCompat" -> "Switch"
            "com.google.android.material.switchMaterial.SwitchMaterial" -> "Switch"
            "ToggleButton" -> "Switch"
            "SeekBar" -> "Slider"
            "Spinner" -> "Spinner"
            "com.google.android.material.tabs.TabLayout" -> "TabRow"
            "com.google.android.material.chip.Chip" -> "Chip"
            "com.google.android.material.chip.ChipGroup" -> "Column"
            "com.google.android.material.divider.MaterialDivider" -> "Divider"
            "com.google.android.material.floatingactionbutton.FloatingActionButton" -> "Button"
            "Toolbar" -> "Box"
            "AppBarLayout" -> "Box"
            "androidx.recyclerview.widget.RecyclerView" -> "LazyColumn"
            "androidx.viewpager2.widget.ViewPager2" -> "LazyColumn"
            "androidx.swiperefreshlayout.widget.SwipeRefreshLayout" -> "Scroll"
            // 运行时组件：静态预览无法真实呈现，映射到占位渲染器
            "WebView" -> "RuntimeView"
            "VideoView" -> "RuntimeView"
            "SurfaceView" -> "RuntimeView"
            "TextureView" -> "RuntimeView"
            // 抽屉：DrawerLayout 容器 + NavigationView 侧边菜单
            "androidx.drawerlayout.widget.DrawerLayout" -> "Box"
            "com.google.android.material.navigation.NavigationView" -> "Drawer"
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

        attrs["android:paddingHorizontal"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(padding = (modifier.padding ?: PaddingSpec()).copy(horizontal = it)) } }
        attrs["android:paddingVertical"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(padding = (modifier.padding ?: PaddingSpec()).copy(vertical = it)) } }
        attrs["android:paddingStart"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(padding = (modifier.padding ?: PaddingSpec()).copy(start = it)) } }
        attrs["android:paddingTop"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(padding = (modifier.padding ?: PaddingSpec()).copy(top = it)) } }
        attrs["android:paddingEnd"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(padding = (modifier.padding ?: PaddingSpec()).copy(end = it)) } }
        attrs["android:paddingBottom"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(padding = (modifier.padding ?: PaddingSpec()).copy(bottom = it)) } }

        // margin 系列：合并到已有 padding 槽位不影响渲染（用独立 margin 概念映射为外边距近似）
        attrs["android:layout_margin"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(margin = PaddingSpec(all = it)) } }
        attrs["android:layout_marginHorizontal"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(margin = (modifier.margin ?: PaddingSpec()).copy(horizontal = it)) } }
        attrs["android:layout_marginVertical"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(margin = (modifier.margin ?: PaddingSpec()).copy(vertical = it)) } }
        attrs["android:layout_marginStart"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(margin = (modifier.margin ?: PaddingSpec()).copy(start = it)) } }
        attrs["android:layout_marginTop"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(margin = (modifier.margin ?: PaddingSpec()).copy(top = it)) } }
        attrs["android:layout_marginEnd"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(margin = (modifier.margin ?: PaddingSpec()).copy(end = it)) } }
        attrs["android:layout_marginBottom"]?.let { v -> parseDim(v)?.let { modifier = modifier.copy(margin = (modifier.margin ?: PaddingSpec()).copy(bottom = it)) } }

        attrs["android:background"]?.let { bg ->
            modifier = if (bg.startsWith("#")) {
                modifier.copy(background = normalizeColor(bg))
            } else {
                // 资源引用（@color/...）等保留原始值，交给后续资源解析阶段处理
                modifier.copy(background = bg)
            }
        }

        attrs["android:backgroundTint"]?.let { tint ->
            if (tint.startsWith("#")) modifier = modifier.copy(borderColor = normalizeColor(tint))
        }

        attrs["android:elevation"]?.let { e -> parseDim(e)?.let { modifier = modifier.copy(elevation = it) } }
        attrs["android:alpha"]?.let { a -> a.toFloatOrNull()?.let { modifier = modifier.copy(alpha = it) } }

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

        attrs["android:fontFamily"]?.let { elementAttrs["fontFamily"] = it }
        // Android 的 letterSpacing 是 em 比例（相对于字号），这里原样保留数值并在渲染端当作相对字距使用
        attrs["android:letterSpacing"]?.let { ls -> ls.toFloatOrNull()?.let { elementAttrs["letterSpacing"] = it.toString() } }
        attrs["android:lineSpacingExtra"]?.let { ls -> parseDim(ls)?.let { elementAttrs["lineSpacing"] = it.toString() } }

        attrs["android:onClick"]?.let { elementAttrs["onClick"] = it }

        // 可见性：gone 在渲染端应跳过/折叠，invisible 保留占位，visible 为默认
        attrs["android:visibility"]?.let { elementAttrs["visibility"] = it }

        // ConstraintLayout 偏置（bias）：提取供更精确的对齐还原（align 已由 applyConstraintAlign 推导）
        attrs["app:layout_constraintHorizontal_bias"]?.let { elementAttrs["constraintBiasHorizontal"] = it }
        attrs["app:layout_constraintVertical_bias"]?.let { elementAttrs["constraintBiasVertical"] = it }

        attrs["android:maxLines"]?.let { elementAttrs["maxLines"] = it }
        attrs["android:minLines"]?.let { elementAttrs["minLines"] = it }
        attrs["android:lines"]?.let { elementAttrs["lines"] = it }
        attrs["android:ellipsize"]?.let { elementAttrs["ellipsize"] = it }

        attrs["android:src"]?.let { elementAttrs["src"] = it }
        attrs["android:scaleType"]?.let { elementAttrs["scaleType"] = it }

        attrs["android:checked"]?.let { elementAttrs["checked"] = it }
        attrs["android:progress"]?.let { elementAttrs["progress"] = it }
        attrs["android:max"]?.let { elementAttrs["max"] = it }
        attrs["android:min"]?.let { elementAttrs["min"] = it }
        attrs["android:enabled"]?.let { elementAttrs["enabled"] = it }
        attrs["android:clickable"]?.let { elementAttrs["clickable"] = it }

        attrs["android:gravity"]?.let { gravity ->
            when {
                gravity.contains("center") -> {
                    if (type == "Column") modifier = modifier.copy(horizontalAlignment = "center")
                    if (type == "Row") modifier = modifier.copy(verticalAlignment = "center")
                    if (type == "Text") modifier = modifier.copy(horizontalAlignment = "center")
                    if (type == "Box") modifier = modifier.copy(contentAlignment = "center")
                }
                gravity.contains("start") || gravity.contains("left") -> {
                    if (type == "Column") modifier = modifier.copy(horizontalAlignment = "start")
                    if (type == "Box") modifier = modifier.copy(contentAlignment = "topStart")
                }
                gravity.contains("end") || gravity.contains("right") -> {
                    if (type == "Column") modifier = modifier.copy(horizontalAlignment = "end")
                    if (type == "Box") modifier = modifier.copy(contentAlignment = "topEnd")
                }
            }
        }

        attrs["android:layout_gravity"]?.let { gravity ->
            when {
                gravity.contains("center") -> modifier = modifier.copy(align = "center")
                gravity.contains("start") || gravity.contains("left") -> modifier = modifier.copy(align = "topStart")
                gravity.contains("end") || gravity.contains("right") -> modifier = modifier.copy(align = "topEnd")
                gravity.contains("bottom") -> modifier = modifier.copy(align = "bottomCenter")
            }
        }

        attrs["android:layout_weight"]?.let { w -> w.toFloatOrNull()?.let { elementAttrs["weight"] = it.toString() } }
        attrs["android:weightSum"]?.let { s -> s.toFloatOrNull()?.let { elementAttrs["weightSum"] = it.toString() } }

        // 运行时组件：记录原始标签，供 RuntimeViewRenderer 给出精确占位提示
        if (type == "RuntimeView" && !elementAttrs.containsKey("runtimeKind")) {
            elementAttrs["runtimeKind"] = tag.lowercase()
        }

        // ConstraintLayout 约束近似还原：把 app:layout_constraint*_to*Of="parent" 转成 Box 子节点 align
        modifier = applyConstraintAlign(attrs, modifier)

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

    /**
     * 把 ConstraintLayout 的子节点约束（app:layout_constraint*_to*Of="parent"）近似还原为
     * Box 子节点对齐（UiModifierSpec.align）。仅基于 parent 边约束推导九宫格定位；
     * bias / ratio / 相对其它 View 的约束暂不支持（保持默认）。
     */
    private fun applyConstraintAlign(attrs: Map<String, String>, base: UiModifierSpec): UiModifierSpec {
        val has = { key: String -> attrs["app:layout_constraint${key}_toTopOf"] == "parent" }
        val hasBottom = attrs["app:layout_constraintBottom_toBottomOf"] == "parent"
        val hasStart = attrs["app:layout_constraintStart_toStartOf"] == "parent"
        val hasEnd = attrs["app:layout_constraintEnd_toEndOf"] == "parent"
        val hasLeft = attrs["app:layout_constraintLeft_toLeftOf"] == "parent"
        val hasRight = attrs["app:layout_constraintRight_toRightOf"] == "parent"

        val vertical = when {
            has("Top") && hasBottom -> "center"
            has("Top") -> "top"
            hasBottom -> "bottom"
            else -> null
        }
        val horizontal = when {
            (hasStart || hasLeft) && (hasEnd || hasRight) -> "center"
            hasStart || hasLeft -> "start"
            hasEnd || hasRight -> "end"
            else -> null
        }
        val align = when {
            vertical == "center" && horizontal == "center" -> "center"
            vertical == "center" && horizontal == "start" -> "centerStart"
            vertical == "center" && horizontal == "end" -> "centerEnd"
            vertical == "top" && horizontal == "center" -> "topCenter"
            vertical == "top" && horizontal == "start" -> "topStart"
            vertical == "top" && horizontal == "end" -> "topEnd"
            vertical == "bottom" && horizontal == "center" -> "bottomCenter"
            vertical == "bottom" && horizontal == "start" -> "bottomStart"
            vertical == "bottom" && horizontal == "end" -> "bottomEnd"
            else -> null
        }
        return if (align != null) base.copy(align = align) else base
    }

    private fun toUiElement(node: MutableNode): UiElement {
        val cleanAttrs = node.attributes.filterKeys { it != "__mergeRoot" }.toMap()
        return UiElement(
            type = node.type,
            modifier = node.modifier,
            attributes = cleanAttrs,
            children = node.children.map { toUiElement(it) }
        )
    }
}
