package com.foundry.core.uimodel

/**
 * 规范化 UI 图的基础工具集（纯 Kotlin）：资源解析、结构校验、归一化、能力协商。
 * 这些是把“声明但未落地”的 ResourceTable / RESOURCE_RESOLVE / 校验能力真正激活的基础件。
 */

// ---------------- 资源解析 ----------------

/**
 * 把资源引用（如 "@string/title"、"@color/bg"、"@dimen/pad"）解析为具体值。
 * 无法解析时返回 null（调用方应保留原引用并视情况告警）。
 */
fun ResourceTable.resolve(ref: String): UiValue? {
    if (!ref.startsWith("@")) return null
    val body = ref.removePrefix("@")
    val slash = body.indexOf('/')
    if (slash < 0) return null
    val (type, name) = body.substring(0, slash) to body.substring(slash + 1)
    return when (type) {
        "string" -> strings[name]?.let { UiValue.Text(it) }
        "color" -> colors[name]?.let { UiValue.ColorVal(it) }
        "dimen" -> dimens[name]?.let { UiValue.Dimen(it) }
        else -> null
    }
}

/** 解析单个属性值：ResourceRef → 具体值；其它原样返回。 */
fun ResourceTable.resolveValue(value: UiValue): UiValue =
    if (value is UiValue.ResourceRef) resolve(value.ref) ?: value else value

/** 解析整张图中所有资源引用（属性 + modifier 中的颜色），返回新图（不可变）。 */
fun UiGraph.resolveResources(): UiGraph = copy(root = root?.resolveResources(resources))

private fun UiNode.resolveResources(table: ResourceTable): UiNode {
    val attributes = attributes.mapValues { (_, v) -> table.resolveValue(v) }
    val modifiers = modifiers.map { it.resolveResources(table) }
    val children = children.map { it.resolveResources(table) }
    return copy(attributes = attributes, modifiers = modifiers, children = children)
}

private fun UiModifier.resolveResources(table: ResourceTable): UiModifier {
    fun String?.resolveColor(): String? {
        if (this == null) return null
        if (startsWith("@color/")) return table.colors[removePrefix("@color/")] ?: this
        return this
    }
    return copy(background = background.resolveColor(), borderColor = borderColor.resolveColor())
}

// ---------------- 结构校验 ----------------

/**
 * 规范化节点类型的「单一事实来源」。
 * 所有插件（XML / Compose / JSON DSL）产出的 UiNode 都应落在这份集合内，
 * 渲染后端据此判断是否可渲染。放在 core 层避免各模块各自硬编码类型清单、
 * 也能让 [UiGraph.validate] 在 core 内就完成类型合法性校验，无需反向依赖 app 渲染层。
 */
val CANONICAL_NODE_TYPES: Set<String> = setOf(
    "column", "row", "box", "text", "button", "spacer", "card", "divider", "image",
    "textfield", "scroll", "surface", "lazycolumn", "switch", "checkbox", "slider",
    "progressindicator", "tabrow", "radiobutton", "spinner", "chip"
)

data class ValidationOptions(
    val maxDepth: Int = 64,
    val allowedTypes: Set<String>? = CANONICAL_NODE_TYPES
)

/**
 * 结构校验：空根、超深嵌套、未声明类型。
 * allowedTypes 默认采用 [CANONICAL_NODE_TYPES]（规范化类型集），使类型合法性
 * 在 core 层即可判定；传 null 可关闭类型限制（交由具体渲染后端决定）。
 */
fun UiGraph.validate(options: ValidationOptions = ValidationOptions()): List<Diagnostic> {
    val diags = mutableListOf<Diagnostic>()
    val parser = meta.parser
    val root = root
    if (root == null) {
        diags.add(Diagnostic(Severity.ERROR, "GRAPH_EMPTY", "UiGraph has no root node", parser))
        return diags
    }
    validateNode(root, "root", 0, options, parser, diags)
    return diags
}

private fun validateNode(
    node: UiNode,
    path: String,
    depth: Int,
    options: ValidationOptions,
    parser: String,
    diags: MutableList<Diagnostic>
) {
    if (depth > options.maxDepth) {
        diags.add(
            Diagnostic(
                severity = Severity.ERROR,
                code = "GRAPH_DEPTH",
                message = "Nesting depth exceeds maximum (${options.maxDepth}) at $path",
                sourcePlugin = parser,
                location = SourceLocation(nodeId = node.id)
            )
        )
    }
    val type = node.type.lowercase()
    options.allowedTypes?.let { allowed ->
        if (type !in allowed) {
            diags.add(
                Diagnostic(
                    severity = Severity.WARNING,
                    code = "GRAPH_TYPE",
                    message = "Node type '$type' is not in the allowed set",
                    sourcePlugin = parser,
                    location = SourceLocation(nodeId = node.id)
                )
            )
        }
    }
    node.children.forEachIndexed { i, child ->
        validateNode(child, "$path.children[$i]", depth + 1, options, parser, diags)
    }
}

// ---------------- 归一化 ----------------

/** 归一化：为缺少 id 的节点补齐稳定 id（基于路径），返回新图（不可变）。
 * 根节点若缺失 id 则固定为 "root"，其子节点依次为 "root_0"、"root_1" …。 */
fun UiGraph.normalize(): UiGraph = copy(root = root?.normalizeRoot())

private fun UiNode.normalizeRoot(): UiNode {
    val newId = if (id.isBlank()) "root" else id
    return copy(id = newId, children = children.mapIndexed { i, c -> c.normalize(newId, i) })
}

private fun UiNode.normalize(idPrefix: String, index: Int): UiNode {
    val newId = if (id.isBlank()) "${idPrefix}_$index" else id
    return copy(
        id = newId,
        children = children.mapIndexed { i, c -> c.normalize(newId, i) }
    )
}

// ---------------- 能力协商 ----------------

/** 该能力集合是否满足所需能力（全部包含）。 */
fun Set<UiCapability>.satisfies(required: Set<UiCapability>): Boolean = required.all { it in this }

/** 取可用能力与所需能力的交集（协商后真正可用的能力）。 */
fun negotiateCapabilities(available: Set<UiCapability>, required: Set<UiCapability>): Set<UiCapability> =
    available.intersect(required)
