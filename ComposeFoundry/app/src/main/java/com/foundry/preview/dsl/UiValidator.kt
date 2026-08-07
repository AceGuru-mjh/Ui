package com.foundry.preview.dsl

import com.foundry.preview.engine.Diagnostic
import com.foundry.preview.engine.DiagnosticLevel

/**
 * DSL 校验器。
 * 检查元素类型是否受支持、属性是否合法、嵌套深度是否超限。
 */
class UiValidator {

    companion object {
        val SUPPORTED_TYPES = setOf(
            "column", "row", "box", "text", "button",
            "spacer", "card", "divider", "image",
            "textfield", "scroll", "surface",
            "lazycolumn", "switch", "checkbox",
            "slider", "progressindicator", "tabrow"
        )
        const val MAX_DEPTH = 32
    }

    /**
     * 校验整个文档，返回诊断列表。
     */
    fun validate(document: UiDocument): List<Diagnostic> {
        val diagnostics = mutableListOf<Diagnostic>()
        validateElement(document.root, "root", 0, diagnostics)
        return diagnostics
    }

    private fun validateElement(
        element: UiElement,
        path: String,
        depth: Int,
        diagnostics: MutableList<Diagnostic>
    ) {
        if (depth > MAX_DEPTH) {
            diagnostics.add(
                Diagnostic(
                    level = DiagnosticLevel.ERROR,
                    message = "Nesting depth exceeds maximum ($MAX_DEPTH)",
                    path = path
                )
            )
            return
        }

        val typeLower = element.type.lowercase()
        if (typeLower !in SUPPORTED_TYPES) {
            diagnostics.add(
                Diagnostic(
                    level = DiagnosticLevel.ERROR,
                    message = "Unsupported element type: '${element.type}'. Supported: $SUPPORTED_TYPES",
                    path = path
                )
            )
        }

        if (typeLower == "text" && !element.attributes.containsKey("text")) {
            diagnostics.add(
                Diagnostic(
                    level = DiagnosticLevel.WARNING,
                    message = "Text element missing 'text' attribute",
                    path = path
                )
            )
        }

        if (typeLower == "button" && !element.attributes.containsKey("text")) {
            diagnostics.add(
                Diagnostic(
                    level = DiagnosticLevel.WARNING,
                    message = "Button element missing 'text' attribute",
                    path = path
                )
            )
        }

        element.children.forEachIndexed { index, child ->
            validateElement(child, "$path.children[$index]", depth + 1, diagnostics)
        }
    }
}
