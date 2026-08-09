package com.foundry.preview.plugin

import com.foundry.core.uimodel.*
import com.foundry.preview.dsl.PaddingSpec as DslPadding
import com.foundry.preview.dsl.ThemeConfig
import com.foundry.preview.dsl.UiDocument
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.dsl.UiModifierSpec
import com.foundry.preview.engine.Diagnostic as EngineDiagnostic
import com.foundry.preview.engine.DiagnosticLevel as EngineLevel

/**
 * 双向适配器：把现有 dsl.UiElement 树与平台规范化 UiNode 互转。
 *
 * 方向：
 *  - [toUiNode]   DSL 文档  →  UiGraph（供任意后端消费 / 跨插件共享）
 *  - [toUiElement] UiGraph  →  DSL 元素（供现有 ComponentRenderer 渲染，避免重写渲染层）
 */

private fun inferUiValue(key: String, raw: String): UiValue {
    val lower = key.lowercase()
    return when {
        // 资源引用优先：@string/@color/@dimen 等必须识别为 ResourceRef，
        // 否则后续 resolveResources() 无法解析，且诊断无法发现未解析引用。
        raw.startsWith("@") ->
            UiValue.ResourceRef(raw)
        lower == "text" || lower.startsWith("content") || lower.endsWith("label") ->
            UiValue.Text(raw)
        lower.contains("color") || lower == "background" || lower == "tint" ->
            UiValue.ColorVal(raw)
        lower.contains("size") || lower.contains("width") || lower.contains("height")
        || lower.contains("elevation") || lower.contains("radius") || lower.contains("padding") ->
            UiValue.Dimen(raw)
        lower == "src" || lower.endsWith("uri") || lower.endsWith("url") ->
            UiValue.ResourceRef(raw)
        raw == "true" || raw == "false" ->
            UiValue.Bool(raw.toBoolean())
        raw.toDoubleOrNull() != null && lower != "text" ->
            UiValue.Number(raw.toDouble())
        raw.startsWith("{") || raw.contains("if (") || raw.contains("?:") ->
            UiValue.Expression(raw)
        else -> UiValue.Unknown(raw)
    }
}

private fun UiValue.toRawString(): String = raw

fun toUiNode(element: UiElement, path: String = "root"): UiNode {
    val dslMod = element.modifier
    val modifiers = buildList {
        add(
            UiModifier(
                fillMaxWidth = dslMod.fillMaxWidth,
                fillMaxHeight = dslMod.fillMaxHeight,
                fillMaxSize = dslMod.fillMaxSize,
                width = dslMod.width,
                height = dslMod.height,
                padding = dslMod.padding?.let { p ->
                    PaddingSpec(
                        all = p.all, horizontal = p.horizontal, vertical = p.vertical,
                        start = p.start, top = p.top, end = p.end, bottom = p.bottom
                    )
                },
                background = dslMod.background,
                cornerRadius = dslMod.cornerRadius,
                borderWidth = dslMod.borderWidth,
                borderColor = dslMod.borderColor,
                elevation = dslMod.elevation,
                horizontalAlignment = dslMod.horizontalAlignment,
                verticalArrangement = dslMod.verticalArrangement,
                verticalAlignment = dslMod.verticalAlignment,
                horizontalArrangement = dslMod.horizontalArrangement,
                contentAlignment = dslMod.contentAlignment,
                align = dslMod.align,
                scrollable = dslMod.scrollable
            )
        )
    }
    val attributes = element.attributes.mapValues { (k, v) -> inferUiValue(k, v) }
    val children = element.children.mapIndexed { index, child -> toUiNode(child, "$path.children[$index]") }

    return UiNode(
        id = path,
        type = element.type,
        attributes = attributes,
        modifiers = modifiers,
        children = children,
        source = SourceLocation(nodeId = path),
        confidence = Confidence.HIGH
    )
}

fun toUiElement(node: UiNode): UiElement {
    val mod = node.modifiers.firstOrNull()
    val padding = mod?.padding?.let { p ->
        DslPadding(
            all = p.all, horizontal = p.horizontal, vertical = p.vertical,
            start = p.start, top = p.top, end = p.end, bottom = p.bottom
        )
    }
    val modifierSpec = UiModifierSpec(
        fillMaxWidth = mod?.fillMaxWidth ?: false,
        fillMaxHeight = mod?.fillMaxHeight ?: false,
        fillMaxSize = mod?.fillMaxSize ?: false,
        width = mod?.width,
        height = mod?.height,
        padding = padding,
        background = mod?.background,
        cornerRadius = mod?.cornerRadius,
        borderWidth = mod?.borderWidth,
        borderColor = mod?.borderColor,
        elevation = mod?.elevation,
        horizontalAlignment = mod?.horizontalAlignment,
        verticalArrangement = mod?.verticalArrangement,
        verticalAlignment = mod?.verticalAlignment,
        horizontalArrangement = mod?.horizontalArrangement,
        contentAlignment = mod?.contentAlignment,
        align = mod?.align,
        scrollable = mod?.scrollable ?: false
    )
    val attributes = node.attributes.mapValues { (_, v) -> v.toRawString() }
    val children = node.children.map { toUiElement(it) }

    return UiElement(
        type = node.type,
        modifier = modifierSpec,
        attributes = attributes,
        children = children
    )
}

/**
 * 把规范化 UiGraph 还原为现有渲染层使用的 UiDocument。
 * 主题信息来自插件写入 graph.themes[0].attributes（primaryColor / backgroundColor /
 * surfaceColor / textColor / isDark）。
 */
fun toUiDocument(graph: UiGraph): UiDocument {
    val rootElement = graph.root?.let { toUiElement(it) } ?: UiElement(type = "Box")
    val theme = graph.themes.firstOrNull()?.attributes?.let { attrs ->
        ThemeConfig(
            primaryColor = attrs["primaryColor"]?.raw ?: ThemeConfig().primaryColor,
            backgroundColor = attrs["backgroundColor"]?.raw ?: ThemeConfig().backgroundColor,
            surfaceColor = attrs["surfaceColor"]?.raw ?: ThemeConfig().surfaceColor,
            textColor = attrs["textColor"]?.raw ?: ThemeConfig().textColor,
            isDark = (attrs["isDark"] as? UiValue.Bool)?.value ?: false
        )
    } ?: ThemeConfig()
    return UiDocument(theme = theme, root = rootElement)
}

/**
 * 跨插件统一诊断 → 现有 DiagnosticsEngine 诊断（反向映射），
 * 使不同格式插件产出的诊断能在同一面板统一展示。
 */
fun toEngineDiagnostic(d: Diagnostic): EngineDiagnostic {
    val level = when (d.severity) {
        Severity.INFO -> EngineLevel.INFO
        Severity.WARNING -> EngineLevel.WARNING
        Severity.ERROR -> EngineLevel.ERROR
    }
    return EngineDiagnostic(
        level = level,
        message = "[${d.sourcePlugin}] ${d.message}",
        path = d.location?.nodeId ?: "",
        line = d.location?.line
    )
}
