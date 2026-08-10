package com.foundry.preview.dsl

import com.foundry.core.uimodel.normalizeColor

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

    data class ParseResult(
        /** 预览入口：函数名 -> 组件树根（多 @Composable / @Preview 各为独立入口）。 */
        val roots: Map<String, UiElement>,
        val issues: List<String>
    )

    fun parse(source: String): Result<ParseResult> = runCatching {
        val issues = mutableListOf<String>()
        val roots = linkedMapOf<String, UiElement>()

        // 1) 切出每个顶层 @Composable fun 的函数体（保活函数名，支持多预览入口）
        val funBodies = extractComposableBodies(source)
        if (funBodies.isEmpty()) {
            issues += "No '@Composable fun' found — nothing to preview"
            return@runCatching ParseResult(emptyMap(), issues)
        }

        funBodies.forEach { (name, body) ->
            val root = parseBlock(body, issues, name)
            if (root != null) roots[name] = root
        }
        ParseResult(roots, issues)
    }

    /** 扫描源码，返回「函数名 -> 函数体」列表（含外层大括号），支持 @Preview / @Composable。 */
    private fun extractComposableBodies(src: String): List<Pair<String, String>> {
        val bodies = mutableListOf<Pair<String, String>>()
        val text = stripCommentsAndStrings(src)
        var i = 0
        val n = text.length
        while (i < n) {
            // 从 i 起寻找最近的 "@Composable" 或 "@Preview" 注解位置
            val atComp = text.indexOf("@Composable", i)
            val atPrev = text.indexOf("@Preview", i)
            val at = minOf(
                if (atComp < 0) Int.MAX_VALUE else atComp,
                if (atPrev < 0) Int.MAX_VALUE else atPrev
            )
            if (at == Int.MAX_VALUE) break
            // 其后需紧跟 fun（中间可夹其它注解，如 @Preview @Composable fun）
            val funKw = text.indexOf("fun", at)
            if (funKw < 0) break
            // 提取函数名：fun 之后到首个 '(' 的标识符
            val nameStart = funKw + 3
            var ns = nameStart
            while (ns < n && text[ns].isWhitespace()) ns++
            val nameEnd = run {
                var e = ns
                while (e < n && (text[e].isLetterOrDigit() || text[e] == '_')) e++
                e
            }
            val name = if (nameEnd > ns) text.substring(ns, nameEnd) else "preview${bodies.size}"
            // 跳到函数体第一个 '{'
            val open = text.indexOf('{', nameEnd)
            if (open < 0) { i = at + 1; continue }
            val close = matchBrace(text, open)
            if (close < 0) { i = at + 1; continue }
            bodies += name to text.substring(open, close + 1)
            i = close + 1
        }
        return bodies
    }

    /**
     * 解析一个代码块（大括号内容），返回其组件树的根（取第一个组件）；
     * 若块内有多个顶层组件，则包一层 Box 容纳它们。
     * 支持最小控制流：if (常量) / for(x in listOf(...)) / items(listOf(...)) 静态展开。
     */
    private fun parseBlock(block: String, issues: MutableList<String>, path: String): UiElement? {
        val inner = stripBraces(block)
        val statements = expandStatements(inner)
        if (statements.isEmpty()) return null
        val nodes = statements.mapNotNull { parseStatement(it, issues, path) }
        if (nodes.isEmpty()) return null
        return if (nodes.size == 1) nodes.first() else UiElement(type = "Box", children = nodes)
    }

    /**
     * 把代码块内顶层语句拆分为片段，并对已知的最小控制流做静态展开：
     *  - if (常量条件) { ... } else { ... } → 仅保留成立分支内的语句
     *  - for (x in listOf(a, b, c)) { ... } / items(listOf(...)) { ... } → 按字面量列表复制展开
     * 无法判定或非控制流则原样保留（交给 parseCall 处理）。
     */
    private fun expandStatements(code: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        val n = code.length
        while (i < n) {
            i = skipString(code, i)
            if (i >= n) break
            // 命中 if / for / 已知列表遍历调用
            val kw = when {
                code.startsWith("if", i) && (i + 2 >= n || !code[i + 2].isLetter()) -> "if"
                code.startsWith("for", i) && (i + 3 >= n || !code[i + 3].isLetter()) -> "for"
                code.startsWith("items", i) && (i + 5 >= n || !code[i + 5].isLetter()) -> "items"
                else -> null
            }
            if (kw == null) {
                // 非控制流：作为普通语句推进（交给 extractTopLevelCalls 简化片段）
                val next = nextStatement(code, i)
                if (next != null) { result += next.first; i = next.second } else { i++ }
                continue
            }
            // 找控制流后的 '('
            var p = i + kw.length
            while (p < n && code[p].isWhitespace()) p++
            if (p >= n || code[p] != '(') { i++; continue }
            val condClose = matchParen(code, p)
            if (condClose < 0) { i++; continue }
            val cond = code.substring(p + 1, condClose).trim()

            // 控制流体：紧跟的 '{ ... }'
            var b = condClose + 1
            while (b < n && code[b].isWhitespace()) b++
            if (b >= n || code[b] != '{') { i = b + 1; continue }
            val bodyClose = matchBrace(code, b)
            if (bodyClose < 0) { i++; continue }
            val body = code.substring(b, bodyClose + 1)

            when (kw) {
                "if" -> {
                    val elseBranch = findElseBranch(code, bodyClose)
                    val chosen = if (evalCondition(cond)) body else elseBranch
                    if (chosen != null) result += expandStatements(stripBraces(chosen))
                    i = if (elseBranch != null) bodyClose + elseBranch.length + 1 else bodyClose + 1
                }
                "for", "items" -> {
                    val listExpr = extractListLiteral(cond)
                    if (listExpr != null) {
                        // 把列表里每个元素对应的 body 展开（列表元素作为占位变量名，这里仅复制 body）
                        listExpr.forEach { _ -> result += body }
                    } else {
                        result += body
                    }
                    i = bodyClose + 1
                }
                else -> { i = bodyClose + 1 }
            }
        }
        return result
    }

    /** 取下一个顶层语句（组件调用或带尾随 lambda 的块），返回片段与结束下标。 */
    private fun nextStatement(code: String, start: Int): Pair<String, Int>? {
        val calls = extractTopLevelCalls(code.substring(start))
        // extractTopLevelCalls 从 0 开始，这里转换一下：取第一个调用并映射回原文下标
        var i = start
        val n = code.length
        while (i < n) {
            i = skipString(code, i)
            if (i >= n) return null
            val c = code[i]
            if (c.isLetter()) {
                val idStart = i
                while (i < n && (code[i].isLetterOrDigit() || code[i] == '_')) i++
                var j = i
                while (j < n && code[j].isWhitespace()) j++
                if (j < n && (code[j] == '(' || code[j] == '{')) {
                    val open = j
                    val close = if (code[j] == '(') matchParen(code, open) else matchBrace(code, open)
                    if (close > 0) {
                        var end = close
                        var k = close + 1
                        while (k < n && code[k].isWhitespace()) k++
                        if (k < n && code[k] == '{') {
                            val lb = matchBrace(code, k)
                            if (lb > 0) end = lb
                        }
                        return code.substring(idStart, end + 1) to (end + 1)
                    }
                }
                return null
            } else { i++ }
        }
        return null
    }

    /** 在 if 块结束位置之后寻找 else / else if 分支，返回其 body 片段（否则 null）。 */
    private fun findElseBranch(code: String, afterIfBody: Int): String? {
        var i = afterIfBody + 1
        val n = code.length
        while (i < n) {
            i = skipString(code, i)
            if (i >= n) return null
            if (code.startsWith("else", i) && (i + 4 >= n || !code[i + 4].isLetter())) {
                var p = i + 4
                while (p < n && code[p].isWhitespace()) p++
                if (p < n && code.startsWith("if", p)) {
                    // else if：跳过整个 else-if 分支（不展开嵌套条件）
                    var q = p + 2
                    while (q < n && code[q].isWhitespace()) q++
                    if (q < n && code[q] == '(') { val c = matchParen(code, q); if (c > 0) { var r = c + 1; while (r < n && code[r].isWhitespace()) r++; if (r < n && code[r] == '{') { val lb = matchBrace(code, r); if (lb > 0) return code.substring(r, lb + 1) } } }
                    return null
                }
                if (p < n && code[p] == '{') {
                    val lb = matchBrace(code, p)
                    if (lb > 0) return code.substring(p, lb + 1)
                }
                return null
            }
            if (code[i] == '{' || code[i] == '}' || code[i] == '(' || code[i] == ')') i++
            else i++
        }
        return null
    }

    /** 极简常量布尔/比较求值（仅支持 true/false/字面量相等）。 */
    private fun evalCondition(cond: String): Boolean {
        val c = cond.trim()
        return when {
            c == "true" -> true
            c == "false" -> false
            c.contains("==") -> {
                val (a, b) = c.split("==", limit = 2).map { it.trim().trim('"').trim('\'') }
                a == b
            }
            c.contains("!=") -> {
                val (a, b) = c.split("!=", limit = 2).map { it.trim().trim('"').trim('\'') }
                a != b
            }
            else -> false
        }
    }

    /** 从 `x in listOf(...)` 或 `items(listOf(...))` 的括号表达式里提取字面量列表元素。 */
    private fun extractListLiteral(cond: String): List<String>? {
        val m = "listOf\\s*\\(([^)]*)\\)".toRegex().find(cond) ?: return null
        val inner = m.groupValues[1]
        if (inner.isBlank()) return emptyList()
        return splitTopLevel(inner, ',').map { it.trim() }
    }

    /** 解析单个顶层语句：组件调用 → parseCall；裸代码块 → 递归 parseBlock。 */
    private fun parseStatement(stmt: String, issues: MutableList<String>, path: String): UiElement? {
        val trimmed = stmt.trim()
        if (trimmed.startsWith("{")) return parseBlock(trimmed, issues, path)
        return parseCall(trimmed, issues, path)
    }

    /** 在一段代码里找出「顶层」的组件调用（不在任何内层 lambda/括号内）。 */
    private fun extractTopLevelCalls(code: String): List<String> {
        val calls = mutableListOf<String>()
        var i = 0
        val n = code.length
        while (i < n) {
            i = skipString(code, i)
            if (i >= n) break
            val c = code[i]
            if (c.isLetter() && (c.isUpperCase() || code[i] == 'M' && code.startsWith("Modifier", i))) {
                // 读取标识符
                val idStart = i
                while (i < n && (code[i].isLetterOrDigit() || code[i] == '_')) i++
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
                } else if (j < n && code[j] == '{') {
                    // 无参调用 + 尾随 lambda：Name { ... }
                    val lb = matchBrace(code, j)
                    if (lb > 0) {
                        calls += code.substring(idStart, lb + 1)
                        i = lb + 1
                        continue
                    }
                }
                // 不是调用，继续
            } else {
                i++
            }
        }
        return calls
    }

    /** 解析单个组件调用字符串，如 `Text("hi", modifier = Modifier.padding(8.dp))`。 */
    private fun parseCall(call: String, issues: MutableList<String>, path: String): UiElement? {
        // 先取组件标识符：到首个空白 / '(' / '{' 为止（避免误吞内部子调用的 '('）
        val idEnd = call.indexOfFirst { it.isWhitespace() || it == '(' || it == '{' }
        val id = call.substring(0, if (idEnd < 0) call.length else idEnd).trim()
        // 标识符后第一个非空白字符决定本调用是否带参数括号
        var p = idEnd
        while (p < call.length && call[p].isWhitespace()) p++
        val open = if (p < call.length && call[p] == '(') p else -1
        val argsPart: String
        val lambdaStart: Int
        if (open < 0) {
            // 无参调用：Name { ... }，参数区为空，lambda 紧随标识符之后
            argsPart = ""
            lambdaStart = call.indexOf('{')
        } else {
            val close = matchParen(call, open)
            argsPart = if (close > 0) call.substring(open + 1, close) else call.substring(open + 1)
            lambdaStart = if (close > 0) close + 1 else call.length
        }

        val type = mapComposableType(id)
        if (type == null) {
            issues += "Unsupported Composable '<$id>' — downgraded to Box"
        }
        val resolvedType = type ?: "Box"

        val modifier = parseModifierChain(argsPart)
        val attributes = parseAttributes(id, argsPart)

        // 提取尾随 lambda：Name(args) { ... } 或 Name { ... }，与 args 分离后再递归解析
        var ls = lambdaStart
        while (ls < call.length && call[ls].isWhitespace()) ls++
        val lambdaText = if (ls >= 0 && ls < call.length && call[ls] == '{') {
            val lb = matchBrace(call, ls)
            if (lb > 0) call.substring(ls, lb + 1) else ""
        } else ""
        val children = parseLambdaChildren(lambdaText, issues, path)

        return UiElement(type = resolvedType, modifier = modifier, attributes = attributes, children = children)
    }

    /** 识别参数中的尾随 lambda（最后一个顶层 '{...}'）并递归解析其组件（含控制流展开）。 */
    private fun parseLambdaChildren(args: String, issues: MutableList<String>, path: String): List<UiElement> {
        // 找到最外层括号后的首个顶层 '{'
        var depth = 0
        var i = 0
        val n = args.length
        while (i < n) {
            i = skipString(args, i)
            if (i >= n) break
            when (args[i]) {
                '(' -> depth++
                ')' -> depth--
                '{' -> {
                    if (depth == 0) {
                        val close = matchBrace(args, i)
                        if (close > 0) {
                            val lambdaBody = args.substring(i, close + 1)
                            return expandStatements(stripBraces(lambdaBody)).mapNotNull { parseStatement(it, issues, path) }
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
                "wrapContentWidth" -> mod.copy(fillMaxWidth = false)
                "wrapContentHeight" -> mod.copy(fillMaxHeight = false)
                "padding" -> mod.copy(padding = parsePaddingArgs(argsStr, mod.padding))
                "size" -> applySize(mod, argsStr)
                "width" -> mod.copy(width = dimDp(argsStr))
                "height" -> mod.copy(height = dimDp(argsStr))
                "background" -> mod.copy(background = parseColorArg(argsStr, mod.background))
                // border(width, color?)：取首个参数为边框宽度，若含 #颜色则取边框色
                "border" -> {
                    val parts = splitTopLevel(argsStr, ',')
                    val width = dimDp(parts.firstOrNull() ?: "")
                    val color = parseColorArg(parts.getOrNull(1) ?: "", null)
                    mod.copy(
                        borderWidth = width ?: mod.borderWidth,
                        borderColor = color ?: mod.borderColor
                    )
                }
                "clip" -> mod.copy(cornerRadius = clipCornerRadius(argsStr) ?: mod.cornerRadius)
                "clickable" -> mod // 原型阶段仅静态结构，交互回调不展开
                "align" -> mod.copy(align = argsStr.trim().trim('"'))
                "weight" -> mod // weight 作用于 Row/Column 子项，由组件属性层处理，Modifier 层保留
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

    /** clip(RoundedCornerShape(N.dp)) / clip(CircleShape) → 圆角半径(dp)；CircleShape 返回较大值近似。 */
    private fun clipCornerRadius(argsStr: String): Float? {
        val s = argsStr.trim()
        if (s.contains("CircleShape")) return 999f
        val m = "RoundedCornerShape\\s*\\(([^)]*)\\)".toRegex().find(s)
        val inner = m?.groupValues?.getOrNull(1)?.trim() ?: s
        return dimDp(inner)
    }

    /** 从 `remember { mutableStateOf(DEFAULT) }` 提取初始值（用于静态预览回填）。 */
    private fun extractRememberDefault(args: String): String? {
        val m = "mutableStateOf\\s*\\(([^)]*)\\)".toRegex().find(args) ?: return null
        val raw = m.groupValues[1].trim()
        return if (raw.isNotEmpty()) raw else null
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
                // 状态回填：Text(value = remember { mutableStateOf("x") }) 的初值
                if (!attrs.containsKey("text")) {
                    extractRememberDefault(argsNoMod)?.let { attrs["text"] = stripQuotes(it) }
                }
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

    /** Compose 命名颜色常量 → #AARRGGBB（原型阶段覆盖常用集合）。 */
    private val NAMED_COLORS: Map<String, String> = mapOf(
        "Black" to "#FF000000",
        "DarkGray" to "#FF444444",
        "Gray" to "#FF888888",
        "LightGray" to "#FFCCCCCC",
        "White" to "#FFFFFFFF",
        "Red" to "#FFFF0000",
        "Green" to "#FF00FF00",
        "Blue" to "#FF0000FF",
        "Yellow" to "#FFFFFF00",
        "Cyan" to "#FF00FFFF",
        "Magenta" to "#FFFF00FF",
        "Transparent" to "#00000000"
    )

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
        if (s.startsWith("Color.")) return resolveNamedColor(s) // 展开命名颜色常量
        return fallback
    }

    /** 展开 Compose 常见命名颜色常量为 #AARRGGBB（原型阶段覆盖常用集合）。 */
    private fun resolveNamedColor(expr: String): String? {
        val name = expr.removePrefix("Color.").substringBefore('.').substringBefore('(').trim()
        return NAMED_COLORS[name]
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
        return parts.firstOrNull { it.replace(" ", "").startsWith("$name=") }
            ?.substringAfter('=')?.trim()
    }

    private fun removeNamedArg(args: String, name: String): String {
        val parts = splitTopLevel(args, ',').filterNot { it.trim().startsWith("$name=") }
        return parts.joinToString(",")
    }

    /** 顶层按分隔符拆分，忽略括号内/大括号内/字符串内的嵌套。 */
    private fun splitTopLevel(s: String, sep: Char): List<String> {
        val out = mutableListOf<String>()
        var depthP = 0
        var depthB = 0
        var cur = StringBuilder()
        var i = 0
        while (i < s.length) {
            // 保留字符串字面量内容（如 Text("Hello")），仅借助 skipString 越过转义
            if (s[i] == '"' || s[i] == '\'') {
                val end = skipString(s, i)
                cur.append(s.substring(i, end))
                i = end
                continue
            }
            val ch = s[i]
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
            i++
        }
        if (cur.isNotBlank()) out += cur.toString()
        return out.map { it.trim() }.filter { it.isNotEmpty() }
    }

    /** 仅移除注释，保留字符串字面量内容（供后续提取 Text 等文本）。 */
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
        var k = open
        while (k < s.length) {
            k = skipString(s, k)
            if (k >= s.length) break
            when (s[k]) {
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) return k }
            }
            k++
        }
        return -1
    }

    private fun matchParen(s: String, open: Int): Int {
        var depth = 0
        var k = open
        while (k < s.length) {
            k = skipString(s, k)
            if (k >= s.length) break
            when (s[k]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) return k }
            }
            k++
        }
        return -1
    }

    /** 跳过字符串字面量（含 \" 转义与 ' 字符字面量），返回字符串之后的下标。 */
    private fun skipString(s: String, i: Int): Int {
        val c = s[i]
        if (c != '"' && c != '\'') return i
        val q = c
        var j = i + 1
        while (j < s.length) {
            if (s[j] == '\\') { j += 2; continue }
            if (s[j] == q) return j + 1
            j++
        }
        return s.length
    }
}
