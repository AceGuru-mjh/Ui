package com.foundry.codegen

/**
 * Compose Kotlin 代码生成器。
 * 将 CodegenNode 树转换为可编译的 Jetpack Compose 代码。
 *
 * 推理：采用递归下降方式，每个节点类型映射到一个生成函数。
 * Modifier 链按 Compose 最佳实践排序：
 * size → padding → background → clip → border → shadow
 */
class ComposeCodeGenerator {

    private val sb = StringBuilder()
    private var indentLevel = 0
    private val indent = "    "

    /**
     * 生成完整的 Kotlin 文件内容。
     */
    fun generate(root: CodegenNode, config: GeneratorConfig = GeneratorConfig()): String {
        sb.clear()
        indentLevel = 0

        if (config.includeImports) {
            generateImports(root, config)
        }

        if (config.includePreview) {
            appendLine("@Preview(showBackground = true)")
        }

        appendLine("@Composable")
        appendLine("fun ${config.functionName}() {")
        indentLevel++
        generateElement(root)
        indentLevel--
        appendLine("}")

        return sb.toString()
    }

    private fun generateImports(root: CodegenNode, config: GeneratorConfig) {
        val imports = mutableSetOf(
            "androidx.compose.runtime.Composable",
            "androidx.compose.ui.Modifier",
            "androidx.compose.foundation.layout.*",
            "androidx.compose.material3.*",
            "androidx.compose.ui.unit.dp",
            "androidx.compose.ui.unit.sp",
            "androidx.compose.ui.tooling.preview.Preview"
        )
        collectImports(root, imports)

        appendLine("package ${config.packageName}")
        appendLine("")
        imports.sorted().forEach { appendLine("import $it") }
        appendLine("")
    }

    private fun collectImports(node: CodegenNode, imports: MutableSet<String>) {
        when (node.type.lowercase()) {
            "text" -> {
                imports.add("androidx.compose.ui.text.font.FontWeight")
                imports.add("androidx.compose.ui.graphics.Color")
            }
            "card" -> imports.add("androidx.compose.foundation.shape.RoundedCornerShape")
            "textfield", "switch", "checkbox", "slider", "tabrow" ->
                imports.add("androidx.compose.runtime.*")
            "scroll" -> imports.add("androidx.compose.foundation.rememberScrollState")
            "lazycolumn" -> imports.add("androidx.compose.foundation.lazy.LazyColumn")
        }
        if (node.modifier.background != null) {
            imports.add("androidx.compose.foundation.background")
            imports.add("androidx.compose.ui.graphics.Color")
        }
        if (node.modifier.cornerRadius != null) {
            imports.add("androidx.compose.foundation.shape.RoundedCornerShape")
            imports.add("androidx.compose.ui.draw.clip")
        }
        if (node.modifier.elevation != null) {
            imports.add("androidx.compose.ui.draw.shadow")
        }
        node.children.forEach { collectImports(it, imports) }
    }

    private fun generateElement(node: CodegenNode) {
        when (node.type.lowercase()) {
            "column" -> generateContainer(node, "Column")
            "row" -> generateContainer(node, "Row")
            "box" -> generateContainer(node, "Box")
            "card" -> generateContainer(node, "Card")
            "surface" -> generateContainer(node, "Surface")
            "scroll" -> generateScroll(node)
            "lazycolumn" -> generateLazyColumn(node)
            "text" -> generateText(node)
            "button" -> generateButton(node)
            "spacer" -> generateSpacer(node)
            "divider" -> generateDivider(node)
            "image" -> generateImage(node)
            "textfield" -> generateTextField(node)
            "switch" -> generateSwitch(node)
            "checkbox" -> generateCheckbox(node)
            "slider" -> generateSlider(node)
            "progressindicator" -> generateProgress(node)
            "tabrow" -> generateTabRow(node)
            else -> generateUnknown(node)
        }
    }

    private fun generateContainer(node: CodegenNode, composableName: String) {
        val modStr = buildModifierString(node.modifier)
        appendLine("$composableName(")
        appendLine("    modifier = $modStr")
        appendLine(") {")
        indentLevel++
        node.children.forEach { generateElement(it) }
        indentLevel--
        appendLine("}")
    }

    private fun generateScroll(node: CodegenNode) {
        val modStr = buildModifierString(node.modifier)
        appendLine("val scrollState = rememberScrollState()")
        appendLine("Box(modifier = $modStr.verticalScroll(scrollState)) {")
        indentLevel++
        node.children.forEach { generateElement(it) }
        indentLevel--
        appendLine("}")
    }

    private fun generateLazyColumn(node: CodegenNode) {
        val modStr = buildModifierString(node.modifier)
        appendLine("LazyColumn(modifier = $modStr) {")
        indentLevel++
        node.children.forEach {
            appendLine("item {")
            indentLevel++
            generateElement(it)
            indentLevel--
            appendLine("}")
        }
        indentLevel--
        appendLine("}")
    }

    private fun generateText(node: CodegenNode) {
        val text = node.attributes["text"] ?: ""
        val fontSize = node.attributes["fontSize"]?.toFloatOrNull() ?: 14f
        val modStr = buildModifierString(node.modifier)

        appendLine("Text(")
        appendLine("    text = \"$text\",")
        appendLine("    fontSize = ${fontSize.toInt()}.sp,")
        if (modStr != "Modifier") appendLine("    modifier = $modStr")
        appendLine(")")
    }

    private fun generateButton(node: CodegenNode) {
        val text = node.attributes["text"] ?: "Button"
        val modStr = buildModifierString(node.modifier)
        appendLine("Button(")
        appendLine("    onClick = { /* TODO */ },")
        if (modStr != "Modifier") appendLine("    modifier = $modStr")
        appendLine(") {")
        indentLevel++
        appendLine("Text(\"$text\")")
        indentLevel--
        appendLine("}")
    }

    private fun generateSpacer(node: CodegenNode) {
        val modStr = buildModifierString(node.modifier)
        appendLine("Spacer(modifier = $modStr)")
    }

    private fun generateDivider(node: CodegenNode) {
        val modStr = buildModifierString(node.modifier)
        appendLine("HorizontalDivider(modifier = $modStr)")
    }

    private fun generateImage(node: CodegenNode) {
        val desc = node.attributes["contentDescription"] ?: "Image"
        val modStr = buildModifierString(node.modifier)
        appendLine("// Image placeholder: $desc")
        appendLine("Box(")
        appendLine("    modifier = $modStr.background(Color(0xFFE0E0E0)),")
        appendLine("    contentAlignment = Alignment.Center")
        appendLine(") {")
        indentLevel++
        appendLine("Text(\"$desc\", color = Color.Gray)")
        indentLevel--
        appendLine("}")
    }

    private fun generateTextField(node: CodegenNode) {
        val label = node.attributes["label"] ?: ""
        val modStr = buildModifierString(node.modifier)
        appendLine("var textFieldValue by remember { mutableStateOf(\"\") }")
        appendLine("OutlinedTextField(")
        appendLine("    value = textFieldValue,")
        appendLine("    onValueChange = { textFieldValue = it },")
        if (label.isNotEmpty()) appendLine("    label = { Text(\"$label\") },")
        appendLine("    modifier = $modStr")
        appendLine(")")
    }

    private fun generateSwitch(node: CodegenNode) {
        val label = node.attributes["label"] ?: ""
        val checked = node.attributes["checked"]?.toBoolean() ?: false
        val modStr = buildModifierString(node.modifier)
        appendLine("var switchChecked by remember { mutableStateOf($checked) }")
        appendLine("Row(modifier = $modStr, verticalAlignment = Alignment.CenterVertically) {")
        indentLevel++
        if (label.isNotEmpty()) appendLine("Text(\"$label\", modifier = Modifier.weight(1f))")
        appendLine("Switch(checked = switchChecked, onCheckedChange = { switchChecked = it })")
        indentLevel--
        appendLine("}")
    }

    private fun generateCheckbox(node: CodegenNode) {
        val label = node.attributes["label"] ?: ""
        val checked = node.attributes["checked"]?.toBoolean() ?: false
        val modStr = buildModifierString(node.modifier)
        appendLine("var checkboxChecked by remember { mutableStateOf($checked) }")
        appendLine("Row(modifier = $modStr, verticalAlignment = Alignment.CenterVertically) {")
        indentLevel++
        appendLine("Checkbox(checked = checkboxChecked, onCheckedChange = { checkboxChecked = it })")
        if (label.isNotEmpty()) appendLine("Text(\"$label\")")
        indentLevel--
        appendLine("}")
    }

    private fun generateSlider(node: CodegenNode) {
        val min = node.attributes["min"]?.toFloatOrNull() ?: 0f
        val max = node.attributes["max"]?.toFloatOrNull() ?: 100f
        val value = node.attributes["value"]?.toFloatOrNull() ?: min
        val modStr = buildModifierString(node.modifier)
        appendLine("var sliderValue by remember { mutableStateOf(${value}f) }")
        appendLine("Column(modifier = $modStr) {")
        indentLevel++
        appendLine("Slider(")
        appendLine("    value = sliderValue,")
        appendLine("    onValueChange = { sliderValue = it },")
        appendLine("    valueRange = ${min}f..${max}f")
        appendLine(")")
        indentLevel--
        appendLine("}")
    }

    private fun generateProgress(node: CodegenNode) {
        val progress = node.attributes["progress"]?.toFloatOrNull()
        val modStr = buildModifierString(node.modifier)
        if (progress != null) {
            appendLine("LinearProgressIndicator(")
            appendLine("    progress = { ${progress}f },")
            appendLine("    modifier = $modStr")
            appendLine(")")
        } else {
            appendLine("LinearProgressIndicator(modifier = $modStr)")
        }
    }

    private fun generateTabRow(node: CodegenNode) {
        val modStr = buildModifierString(node.modifier)
        appendLine("var selectedTab by remember { mutableIntStateOf(0) }")
        appendLine("TabRow(selectedTabIndex = selectedTab, modifier = $modStr) {")
        indentLevel++
        node.children.forEachIndexed { index, child ->
            val tabText = child.attributes["text"] ?: "Tab ${index + 1}"
            appendLine("Tab(")
            appendLine("    selected = selectedTab == $index,")
            appendLine("    onClick = { selectedTab = $index },")
            appendLine("    text = { Text(\"$tabText\") }")
            appendLine(")")
        }
        indentLevel--
        appendLine("}")
    }

    private fun generateUnknown(node: CodegenNode) {
        appendLine("// Unknown component: ${node.type}")
        appendLine("Text(\"Unknown: ${node.type}\", color = Color.Red)")
    }

    private fun buildModifierString(spec: CodegenModifier): String {
        val parts = mutableListOf("Modifier")

        if (spec.fillMaxSize) {
            parts.add(".fillMaxSize()")
        } else {
            if (spec.fillMaxWidth) parts.add(".fillMaxWidth()")
            if (spec.fillMaxHeight) parts.add(".fillMaxHeight()")
        }

        if (spec.width != null && spec.height != null) {
            parts.add(".size(${spec.width.toInt()}.dp, ${spec.height.toInt()}.dp)")
        } else if (spec.width != null) {
            parts.add(".width(${spec.width.toInt()}.dp)")
        } else if (spec.height != null) {
            parts.add(".height(${spec.height.toInt()}.dp)")
        }

        spec.padding?.let { p ->
            val paddingStr = buildPaddingString(p)
            if (paddingStr.isNotEmpty()) parts.add(".$paddingStr")
        }

        spec.background?.let { bg ->
            val radius = spec.cornerRadius
            if (radius != null && radius > 0f) {
                parts.add(".clip(RoundedCornerShape(${radius.toInt()}.dp))")
                parts.add(".background(Color(0x${bg.removePrefix("#")}), RoundedCornerShape(${radius.toInt()}.dp))")
            } else {
                parts.add(".background(Color(0x${bg.removePrefix("#")}))")
            }
        }

        if (spec.background == null && spec.cornerRadius != null && spec.cornerRadius > 0f) {
            parts.add(".clip(RoundedCornerShape(${spec.cornerRadius.toInt()}.dp))")
        }

        if (spec.borderWidth != null && spec.borderWidth > 0f) {
            val borderColor = spec.borderColor ?: "#FF888888"
            val radius = spec.cornerRadius ?: 0f
            parts.add(".border(${spec.borderWidth.toInt()}.dp, Color(0x${borderColor.removePrefix("#")}), RoundedCornerShape(${radius.toInt()}.dp))")
        }

        spec.elevation?.let { e ->
            parts.add(".shadow(${e.toInt()}.dp)")
        }

        return parts.joinToString("")
    }

    private fun buildPaddingString(p: CodegenPadding): String {
        if (p.all != null) return "padding(${p.all.toInt()}.dp)"
        val parts = mutableListOf<String>()
        p.start?.let { parts.add("start = ${it.toInt()}.dp") }
        p.top?.let { parts.add("top = ${it.toInt()}.dp") }
        p.end?.let { parts.add("end = ${it.toInt()}.dp") }
        p.bottom?.let { parts.add("bottom = ${it.toInt()}.dp") }
        p.horizontal?.let { parts.add("horizontal = ${it.toInt()}.dp") }
        p.vertical?.let { parts.add("vertical = ${it.toInt()}.dp") }
        return if (parts.isEmpty()) "" else "padding(${parts.joinToString(", ")})"
    }

    private fun getIndent(): String = indent.repeat(indentLevel)

    private fun appendLine(text: String) {
        sb.append(getIndent()).appendLine(text)
    }
}
