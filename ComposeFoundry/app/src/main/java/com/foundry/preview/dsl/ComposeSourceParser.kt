package com.foundry.preview.dsl

/**
 * 极简 Kotlin Composable 源码解析器（原型）。
 *
 * 目标：把一段 Jetpack Compose Kotlin 源码（.kt）中的 `@Composable fun` 反序列化为
 * 平台内部的 [UiElement] 树，从而经 UiGraphAdapters 进入统一的 UiGraph 渲染管线。
 *
 * 设计取舍（原型阶段，刻意简化，不追求完整 Kotlin 语法）：
 *  - 不依赖 Android / Kotlin 编译器，纯字符串 + 括号/大括号平衡扫描；
 *  - 仅识别「首字母大写的函数调用」（即 Compose 组件）与 Modifier 链；
 *  - 无法识别的组件降级为 Box 并给出问题清单（与 XmlLayoutParser 同策略）；
 *  - 提取 Text 文本、Modifier 的常用修饰，足以支撑静态预览原型。
 *
 * 这与 foundry-codegen 方向正好相反：codegen 是 UiNode→Kotlin，本解析器是 Kotlin→UiNode。
 */
class ComposeSourceParser {

    data class ParseResult(val roots: List<UiElement>, val issues: List<String>)

    fun parse(source: String): Result<ParseResult> = runCatching {
        val issues = mutableListOf<String>()
        val roots = mutableListOf<UiElement>()

        // 1) 切出每个顶层 @Composable fun 的函数体
        val funBodies = extractComposableBodies(source)
        if (funBodies.isEmpty()) {
            issues += "No '@Composable fun' found — nothing to preview"
            return@runCatching ParseResult(emptyList(), issues)
        }

        funBodies.forEachIndexed { idx, body ->
            val root = parseBlock(body, issues, "composable[$idx]")
            if (root != null) roots += root
        }
        ParseResult(roots, issues)
    }

    /** 扫描源码，返回每个 @Composable fun 的函数体字符串（含外层大括号）。 */
    private fun extractComposableBodies(src: String): List<String> {
        val bodies = mutableListOf<String>()
        val text = stripCommentsAndStrings(src)
        var i = 0
        val n = text.length
        while (i < n) {
            // 寻找 "@Composable"
            val at = text.indexOf("@Composable", i)
            if (at < 0) break
            // 其后需紧跟 fun
            val funKw = text.indexOf("fun", at)
            if (funKw < 0) break
            // 跳到函数体第一个 '{'
            val open = text.indexOf('{', funKw)
            if (open < 0) { i = at + 1; continue }
            val close = matchBrace(text, open)
            if (close < 0) { i = at + 1; continue }
            bodies += text.substring(open, close + 1)
            i = close + 1
        }
        return bodies
    }

    /**
     * 解析一个代码块（大括号内容），返回其组件树的根（取第一个组件）；
     * 若块内有多个顶层组件，则包一层 Box 容纳它们。
     */
    private fun parseBlock(block: String, issues: MutableList<String>, path: String): UiElement? {
        val inner = stripBraces(block)
        val calls = extractTopLevelCalls(inner)
        if (calls.isEmpty()) return null
        val nodes = calls.mapNotNull { parseCall(it, issues, path) }
        if (nodes.isEmpty()) return null
        return if (nodes.size == 1) nodes.first() else UiElement(type = "Box", children = nodes)
    }

    /** 在一段代码里找出「顶层」的组件调用（不在任何内层 lambda/括号内）。 */
    private fun extractTopLevelCalls(code: String): List<String> {
        val calls = mutableListOf<String>()
        var i = 0
        val n = code.length
        while (i < n) {
            val c = code[i]
            if (c.isLetter() && (c.isUpperCase() || code[i] == 'M' && code.startsWith("Modifier", i))) {
                // 读取标识符
                val idStart = i
                while (i < n && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                val id = code.substring(idStart, i)
                // 需要紧跟 '(' 才是调用
                var j = i
                while (j < n && code[j].isWhitespace()) j++
                if (j < n && code[j] == '(') {
                    val close = matchParen(code, j)
                    if (close > 0) {
                        // 纳入尾随 lambda：Name(args) { ... }，使子组件能被解析
                        var end = close
                        var k = close + 1
                        while (k < n && code[k].isWhitespace()) k++
                        if (k < n && code[k] == '{') {
                            val lb = matchBrace(code, k)
                            if (lb > 0) end = lb
                        }
                        calls += code.substring(idStart, end + 1)
                        i = end + 1
                        continue
                    }
                }
                i = i // 不是调用，继续
            } else {
                i++
            }
        }
        return calls
    }

    /** 解析单个组件调用字符串，如 `Text("hi", modifier = Modifier.padding(8.dp))`。 */
    private fun parseCall(call: String, issues: MutableList<String>, path: String): UiElement? {
        // 分离 标识符( 与 )
        val open = call.indexOf('(')
        if (open < 0) return null
        val id = call.substring(0, open).trim()
        val close = matchParen(call, open)
        // 仅取本调用自身的 (args) 部分，剥离尾随 lambda 与多余的括号
        val argsPart = if (close > 0) call.substring(open + 1, close) else call.substring(open + 1)

        val type = mapComposableType(id)
        if (type == null) {
            issues += "Unsupported Composable '<$id>' — downgraded to Box"
        }
        val resolvedType = type ?: "Box"

        val modifier = parseModifierChain(argsPart)
        val attributes = parseAttributes(id, argsPart)

        // 提取尾随 lambda：Name(args) { ... }，与 args 分离后再递归解析
        var lambdaStart = if (close > 0) close + 1 else call.length
        while (lambdaStart < call.length && call[lambdaStart].isWhitespace()) lambdaStart++
        val lambdaText = if (lambdaStart < call.length && call[lambdaStart] == '{') {
            val lb = matchBrace(call, lambdaStart)
            if (lb > 0) call.substring(lambdaStart, lb + 1) else ""
        } else ""
        val children = parseLambdaChildren(lambdaText, issues, path)

        return UiElement(type = resolvedType, modifier = modifier, attributes = attributes, children = children)
    }

    /** 识别参数中的尾随 lambda（最后一个顶层 '{...}'）并递归解析其组件。 */
    private fun parseLambdaChildren(args: String, issues: MutableList<String>, path: String): List<UiElement> {
        // 找到最外层括号后的首个顶层 '{'
        var depth = 0
        var i = 0
        val n = args.length
        while (i < n) {
            when (args[i]) {
                '(' -> depth++
                ')' -> depth--
                '{' -> {
                    if (depth == 0) {
                        val close = matchBrace(args, i)
                        if (close > 0) {
                            val lambdaBody = args.substring(i, close + 1)
                            val inner = stripBraces(lambdaBody)
                            return extractTopLevelCalls(inner).mapNotNull { parseCall(it, issues, path) }
                        }
                    }
                }
            }
            i++
        }
        return emptyList()
    }

    /** 解析 Modifier 链：Modifier.fillMaxWidth().padding(16.dp).background(Color.Red) ... */
    private fun parseModifierChain(args: String): UiModifierSpec {
        var modifier = UiModifierSpec()
        // 找到 "modifier = Modifier..." 或独立 "Modifier."
        val kw = "Modifier"
        var idx = args.indexOf(kw)
        while (idx >= 0) {
            // 从 Modifier 起点继续读取链，直到遇到顶层逗号/右括号/左大括号
            var j = idx + kw.length
            var depth = 0
            val chainEnd = buildString {
                while (j < args.length) {
                    val ch = args[j]
                    when (ch) {
                        '(' -> depth++
                        ')' -> depth--
                        ',' -> if (depth == 0) { append('\u0000'); return@buildString }
                        '{' -> { // lambda 终止链
                            append('\u0000'); return@buildString
                        }
                    }
                    append(ch)
                    if (depth < 0) return@buildString
                    j++
                }
            }
            val chain = chainEnd.replace('\u0000', ' ').trim()
            modifier = applyModifierCalls(modifier, chain)
            idx = args.indexOf(kw, j)
        }
        return modifier
    }

    private fun applyModifierCalls(base: UiModifierSpec, chain: String): UiModifierSpec {
        var mod = base
        // 按顶层 '.' 拆分调用：fillMaxWidth() / padding(16.dp) / background(...)
        val calls = splitTopLevel(chain, '.')
        calls.forEach { seg ->
            val open = seg.indexOf('(')
            if (open < 0) return@forEach
            val name = seg.substring(0, open).trim()
            val argsStr = seg.substring(open + 1, seg.length - 1)
            mod = when (name) {
                "fillMaxWidth" -> mod.copy(fillMaxWidth = true)
                "fillMaxHeight" -> mod.copy(fillMaxHeight = true)
                "fillMaxSize" -> mod.copy(fillMaxSize = true)
                "wrapContentWidth" -> mod
                "wrapContentHeight" -> mod
                "padding" -> mod.copy(padding = parsePaddingArgs(argsStr, mod.padding))
                "size" -> applySize(mod, argsStr)
                "width" -> mod.copy(width = dimDp(argsStr))
                "height" -> mod.copy(height = dimDp(argsStr))
                "background" -> mod.copy(background = parseColorArg(argsStr, mod.background))
                "border" -> mod // 简化：保留已有
                "clip" -> mod
                "clickable" -> mod
                "align" -> mod
                "weight" -> mod
                "shadow" -> mod.copy(elevation = dimDp(argsStr))
                "backgroundColor" -> mod.copy(background = parseColorArg(argsStr, mod.background))
                else -> mod
            }
        }
        return mod
    }

    private fun applySize(mod: UiModifierSpec, args: String): UiModifierSpec {
        val parts = splitTopLevel(args, ',')
        return when (parts.size) {
            1 -> mod.copy(width = dimDp(parts[0]), height = dimDp(parts[0]))
            2 -> mod.copy(width = dimDp(parts[0]), height = dimDp(parts[1]))
            else -> mod
        }
    }

    private fun parsePaddingArgs(args: String, existing: PaddingSpec?): PaddingSpec {
        val base = existing ?: PaddingSpec()
        val parts = splitTopLevel(args, ',')
        return when (parts.size) {
            1 -> base.copy(all = dimDp(parts[0]))
            2 -> base.copy(horizontal = dimDp(parts[0]), vertical = dimDp(parts[1]))
            4 -> base.copy(start = dimDp(parts[0]), top = dimDp(parts[1]), end = dimDp(parts[2]), bottom = dimDp(parts[3]))
            else -> base
        }
    }

    /** 解析组件普通属性（非 modifier），如 Text 的 text / style。 */
    private fun parseAttributes(id: String, args: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        // 去除 modifier 参数，避免污染
        val argsNoMod = removeNamedArg(args, "modifier")
        when (id) {
            "Text" -> {
                val positional = firstPositional(argsNoMod)
                if (positional != null) attrs["text"] = stripQuotes(positional)
                namedArg(argsNoMod, "text")?.let { attrs["text"] = stripQuotes(it) }
                namedArg(argsNoMod, "fontSize")?.let { attrs["fontSize"] = dimSp(it).toString() }
                namedArg(argsNoMod, "color")?.let { attrs["color"] = parseColorArg(it, null) ?: it }
                namedArg(argsNoMod, "fontWeight")?.let { if ("bold" in it) attrs["fontWeight"] = "bold" }
                namedArg(argsNoMod, "maxLines")?.let { attrs["maxLines"] = it }
            }
            "Button", "FloatingActionButton", "AssistChip" -> {
                namedArg(argsNoMod, "onClick")?.let { /* 交互占位，无属性 */ }
                namedArg(argsNoMod, "enabled")?.let { attrs["enabled"] = it }
            }
            "Image" -> {
                namedArg(argsNoMod, "painter")?.let { attrs["src"] = it }
                namedArg(argsNoMod, "contentDescription")?.let { attrs["contentDescription"] = stripQuotes(it) }
            }
            "TextField", "OutlinedTextField", "BasicTextField" -> {
                namedArg(argsNoMod, "value")?.let { attrs["text"] = stripQuotes(it) }
                namedArg(argsNoMod, "placeholder")?.let { attrs["placeholder"] = stripQuotes(it) }
                namedArg(argsNoMod, "label")?.let { attrs["placeholder"] = stripQuotes(it) }
            }
            "Checkbox" -> {
                namedArg(argsNoMod, "checked")?.let { attrs["checked"] = it }
            }
            "Switch" -> {
                namedArg(argsNoMod, "checked")?.let { attrs["checked"] = it }
            }
            "LinearProgressIndicator", "CircularProgressIndicator" -> {
                namedArg(argsNoMod, "progress")?.let { attrs["progress"] = it }
            }
            "Slider" -> {
                namedArg(argsNoMod, "value")?.let { attrs["value"] = it }
            }
        }
        return attrs
    }

    // ---------------- 辅助 ----------------

    private fun mapComposableType(id: String): String? = when (id) {
        "Column" -> "Column"
        "Row" -> "Row"
        "Box" -> "Box"
        "Text" -> "Text"
        "Button" -> "Button"
        "FloatingActionButton" -> "Button"
        "Image" -> "Image"
        "Scaffold" -> "Box"
        "Card" -> "Card"
        "Spacer" -> "Spacer"
        "LazyColumn" -> "LazyColumn"
        "LazyRow" -> "LazyColumn"
        "Surface" -> "Surface"
        "Divider", "HorizontalDivider" -> "Divider"
        "VerticalDivider" -> "Divider"
        "Checkbox" -> "Checkbox"
        "Switch" -> "Switch"
        "Slider" -> "Slider"
        "TextField", "OutlinedTextField", "BasicTextField" -> "TextField"
        "TabRow", "ScrollableTabRow" -> "TabRow"
        "LinearProgressIndicator", "CircularProgressIndicator" -> "ProgressIndicator"
        "Icon" -> "Image"
        "IconButton" -> "Button"
        "AssistChip", "FilterChip", "ElevatedAssistChip" -> "Chip"
        else -> null
    }

    /** 把颜色参数解析为 #AARRGGBB；支持 Color.Red / 0xFFFF0000 / "#FF0000" / @color/..。 */
    private fun parseColorArg(raw: String, fallback: String?): String? {
        val s = raw.trim()
        if (s.startsWith("#")) return normalizeColor(s)
        if (s.startsWith("0x") || s.startsWith("0X")) {
            // 0xFFFF0000 -> #FFFF0000
            val hex = s.removePrefix("0x").removePrefix("0X")
            return if (hex.length == 6) "#FF$hex" else "#$hex"
        }
        if (s.startsWith("@")) return s // 资源引用原样保留，交给资源解析阶段
        if (s.startsWith("Color.")) return fallback // 命名颜色暂不展开
        return fallback
    }

    /** 规范化颜色字符串为 #AARRGGBB（与 XmlLayoutParser 同逻辑，本地副本避免跨文件耦合）。 */
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

    private fun dimDp(raw: String): Float? {
        val s = raw.trim()
        return when {
            s.endsWith("dp") -> s.removeSuffix("dp").toFloatOrNull()
            s.endsWith("sp") -> s.removeSuffix("sp").toFloatOrNull()
            s.endsWith("dip") -> s.removeSuffix("dip").toFloatOrNull()
            else -> s.toFloatOrNull()
        }
    }

    private fun dimSp(raw: String): Float {
        val s = raw.trim()
        return when {
            s.endsWith("sp") -> s.removeSuffix("sp").toFloatOrNull() ?: 0f
            else -> s.toFloatOrNull() ?: 0f
        }
    }

    private fun stripQuotes(s: String): String {
        val t = s.trim()
        return if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))
            t.substring(1, t.length - 1) else t
    }

    /** 取第一个位置参数（顶层逗号分隔）。 */
    private fun firstPositional(args: String): String? {
        val parts = splitTopLevel(args, ',')
        return parts.firstOrNull()?.takeIf { !it.contains('=') }?.trim()
    }

    private fun namedArg(args: String, name: String): String? {
        val parts = splitTopLevel(args, ',')
        return parts.firstOrNull { it.trim().startsWith("$name=") }
            ?.substringAfter('=')?.trim()
    }

    private fun removeNamedArg(args: String, name: String): String {
        val parts = splitTopLevel(args, ',').filterNot { it.trim().startsWith("$name=") }
        return parts.joinToString(",")
    }

    /** 顶层按分隔符拆分，忽略括号内/大括号内的嵌套。 */
    private fun splitTopLevel(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        var depthP = 0
        var depthB = 0
        var cur = StringBuilder()
        for (ch in s) {
            when (ch) {
                '(' -> { depthP++; cur.append(ch) }
                ')' -> { depthP--; cur.append(ch) }
                '{' -> { depthB++; cur.append(ch) }
                '}' -> { depthB--; cur.append(ch) }
                sep -> if (depthP == 0 && depthB == 0) {
                    out += cur.toString()
                    cur = StringBuilder()
                } else cur.append(ch)
                else -> cur.append(ch)
            }
        }
        if (cur.isNotBlank()) out += cur.toString()
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 移除注释与字符串字面量，避免误解析。 */
    private fun stripCommentsAndStrings(src: String): String {
        val sb = StringBuilder()
        var i = 0
        val n = src.length
        while (i < n) {
            when {
                src.startsWith("//", i) -> { while (i < n && src[i] != '\n') i++ }
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i)
                    i = if (end >= 0) end + 2 else n
                }
                src[i] == '"' || src[i] == '\'' -> {
                    val q = src[i]
                    sb.append(' ') // 用空格占位保持位置
                    i++
                    while (i < n && src[i] != q) {
                        if (src[i] == '\\') i++ // 跳转义
                        i++
                    }
                    if (i < n) i++ // 闭合引号
                    sb.append(' ')
                }
                else -> { sb.append(src[i]); i++ }
            }
        }
        return sb.toString()
    }

    private fun stripBraces(block: String): String {
        val open = block.indexOf('{')
        val close = if (open >= 0) matchBrace(block, open) else -1
        return if (open >= 0 && close > open) block.substring(open + 1, close) else block
    }

    private fun matchBrace(s: String, open: Int): Int {
        var depth = 0
        for (k in open until s.length) {
            when (s[k]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return k }
            }
        }
        return -1
    }

    private fun matchParen(s: String, open: Int): Int {
        var depth = 0
        for (k in open until s.length) {
            when (s[k]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return k }
            }
        }
        return -1
    }
}
