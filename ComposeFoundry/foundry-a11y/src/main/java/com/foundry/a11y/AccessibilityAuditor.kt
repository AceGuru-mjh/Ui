package com.foundry.a11y

import kotlin.math.pow

/**
 * 无障碍审计器。
 *
 * 检查规则（基于 WCAG 2.1 AA 标准）：
 * 1. 图片必须有 contentDescription
 * 2. 文本对比度 >= 4.5:1（正常文本）或 >= 3:1（大文本 >= 18sp）
 * 3. 可点击目标最小 48x48dp
 * 4. Button 必须有文本内容
 * 5. TextField 必须有 label 或 placeholder
 * 6. 嵌套交互元素检测
 */
class AccessibilityAuditor {

    companion object {
        private const val MIN_TOUCH_TARGET_DP = 48f
        private const val MIN_CONTRAST_NORMAL = 4.5
        private const val MIN_CONTRAST_LARGE = 3.0
        private const val LARGE_TEXT_SP = 18f
    }

    /**
     * 执行完整审计。
     */
    fun audit(root: A11yNode, themeBackground: String = "#FFFFFFFF"): List<AccessibilityIssue> {
        val issues = mutableListOf<AccessibilityIssue>()
        auditElement(root, "root", themeBackground, issues, isInsideClickable = false)
        return issues
    }

    /**
     * 生成审计报告。
     */
    fun formatReport(issues: List<AccessibilityIssue>): String {
        if (issues.isEmpty()) return "No accessibility issues found."

        val sb = StringBuilder()
        val critical = issues.filter { it.severity == AccessibilitySeverity.CRITICAL }
        val major = issues.filter { it.severity == AccessibilitySeverity.MAJOR }
        val minor = issues.filter { it.severity == AccessibilitySeverity.MINOR }

        sb.appendLine("Accessibility Audit Report")
        sb.appendLine("Critical: ${critical.size} | Major: ${major.size} | Minor: ${minor.size}")
        sb.appendLine()

        critical.forEach {
            sb.appendLine("[CRITICAL] [${it.rule}] ${it.message}")
            sb.appendLine("  Path: ${it.path}")
            sb.appendLine("  Fix: ${it.suggestion}")
            sb.appendLine()
        }
        major.forEach {
            sb.appendLine("[MAJOR] [${it.rule}] ${it.message}")
            sb.appendLine("  Path: ${it.path}")
            sb.appendLine("  Fix: ${it.suggestion}")
            sb.appendLine()
        }
        minor.forEach {
            sb.appendLine("[MINOR] [${it.rule}] ${it.message}")
            sb.appendLine("  Path: ${it.path}")
            sb.appendLine("  Fix: ${it.suggestion}")
            sb.appendLine()
        }
        return sb.toString()
    }

    private fun auditElement(
        node: A11yNode,
        path: String,
        themeBackground: String,
        issues: MutableList<AccessibilityIssue>,
        isInsideClickable: Boolean
    ) {
        val typeLower = node.type.lowercase()

        when (typeLower) {
            "image" -> checkImage(node, path, issues)
            "text" -> checkText(node, path, themeBackground, issues)
            "button" -> checkButton(node, path, issues, isInsideClickable)
            "textfield" -> checkTextField(node, path, issues)
            "switch", "checkbox" -> checkInteractiveLabel(node, path, issues)
        }

        checkTouchTarget(node, path, issues)

        val isClickable = typeLower == "button"
        node.children.forEachIndexed { index, child ->
            auditElement(child, "$path.children[$index]", themeBackground, issues, isClickable || isInsideClickable)
        }
    }

    private fun checkImage(node: A11yNode, path: String, issues: MutableList<AccessibilityIssue>) {
        val desc = node.attributes["contentDescription"]
        if (desc.isNullOrBlank()) {
            issues.add(AccessibilityIssue(
                severity = AccessibilitySeverity.CRITICAL,
                rule = "WCAG 1.1.1",
                message = "Image missing contentDescription",
                path = path,
                suggestion = "Add 'contentDescription' attribute"
            ))
        }
    }

    private fun checkText(node: A11yNode, path: String, themeBackground: String, issues: MutableList<AccessibilityIssue>) {
        val text = node.attributes["text"]
        if (text.isNullOrBlank()) {
            issues.add(AccessibilityIssue(
                severity = AccessibilitySeverity.MAJOR,
                rule = "WCAG 1.1.1",
                message = "Text element has empty content",
                path = path,
                suggestion = "Add meaningful text content"
            ))
            return
        }

        val textColorStr = node.attributes["color"]
        val bgColorStr = node.modifier.background ?: themeBackground

        if (textColorStr != null) {
            val ratio = calculateContrastRatio(textColorStr, bgColorStr)
            val fontSize = node.attributes["fontSize"]?.toFloatOrNull() ?: 14f
            val required = if (fontSize >= LARGE_TEXT_SP) MIN_CONTRAST_LARGE else MIN_CONTRAST_NORMAL

            if (ratio < required) {
                issues.add(AccessibilityIssue(
                    severity = if (ratio < 2.0) AccessibilitySeverity.CRITICAL else AccessibilitySeverity.MAJOR,
                    rule = "WCAG 1.4.3",
                    message = "Text contrast ratio ${"%.2f".format(ratio)}:1 below required ${required}:1",
                    path = path,
                    suggestion = "Increase color contrast between text and background"
                ))
            }
        }
    }

    private fun checkButton(node: A11yNode, path: String, issues: MutableList<AccessibilityIssue>, isInsideClickable: Boolean) {
        val text = node.attributes["text"]
        if (text.isNullOrBlank()) {
            issues.add(AccessibilityIssue(
                severity = AccessibilitySeverity.CRITICAL,
                rule = "WCAG 4.1.2",
                message = "Button has no accessible label",
                path = path,
                suggestion = "Add 'text' attribute to Button"
            ))
        }
        if (isInsideClickable) {
            issues.add(AccessibilityIssue(
                severity = AccessibilitySeverity.MAJOR,
                rule = "WCAG 4.1.2",
                message = "Nested interactive elements",
                path = path,
                suggestion = "Avoid nesting clickable elements"
            ))
        }
    }

    private fun checkTextField(node: A11yNode, path: String, issues: MutableList<AccessibilityIssue>) {
        val label = node.attributes["label"]
        val placeholder = node.attributes["placeholder"]
        if (label.isNullOrBlank() && placeholder.isNullOrBlank()) {
            issues.add(AccessibilityIssue(
                severity = AccessibilitySeverity.CRITICAL,
                rule = "WCAG 3.3.2",
                message = "TextField has no label or placeholder",
                path = path,
                suggestion = "Add 'label' attribute"
            ))
        }
    }

    private fun checkInteractiveLabel(node: A11yNode, path: String, issues: MutableList<AccessibilityIssue>) {
        val label = node.attributes["label"]
        if (label.isNullOrBlank()) {
            issues.add(AccessibilityIssue(
                severity = AccessibilitySeverity.MAJOR,
                rule = "WCAG 4.1.2",
                message = "${node.type} has no accessible label",
                path = path,
                suggestion = "Add 'label' attribute"
            ))
        }
    }

    private fun checkTouchTarget(node: A11yNode, path: String, issues: MutableList<AccessibilityIssue>) {
        val typeLower = node.type.lowercase()
        val isInteractive = typeLower in setOf("button", "switch", "checkbox", "textfield", "slider")
        if (!isInteractive) return

        node.modifier.width?.let { w ->
            if (w < MIN_TOUCH_TARGET_DP) {
                issues.add(AccessibilityIssue(
                    severity = AccessibilitySeverity.MAJOR,
                    rule = "WCAG 2.5.8",
                    message = "Touch target width ${w}dp < minimum ${MIN_TOUCH_TARGET_DP}dp",
                    path = path,
                    suggestion = "Increase width to at least ${MIN_TOUCH_TARGET_DP}dp"
                ))
            }
        }
        node.modifier.height?.let { h ->
            if (h < MIN_TOUCH_TARGET_DP) {
                issues.add(AccessibilityIssue(
                    severity = AccessibilitySeverity.MAJOR,
                    rule = "WCAG 2.5.8",
                    message = "Touch target height ${h}dp < minimum ${MIN_TOUCH_TARGET_DP}dp",
                    path = path,
                    suggestion = "Increase height to at least ${MIN_TOUCH_TARGET_DP}dp"
                ))
            }
        }
    }

    /**
     * WCAG 2.1 对比度计算。
     * 对比度 = (L1 + 0.05) / (L2 + 0.05)
     */
    private fun calculateContrastRatio(fgColorStr: String, bgColorStr: String): Double {
        val fg = parseColorComponents(fgColorStr)
        val bg = parseColorComponents(bgColorStr)
        val lum1 = relativeLuminance(fg)
        val lum2 = relativeLuminance(bg)
        val lighter = maxOf(lum1, lum2)
        val darker = minOf(lum1, lum2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun parseColorComponents(colorStr: String): Triple<Float, Float, Float> {
        return try {
            val hex = colorStr.removePrefix("#")
            val (r, g, b) = when (hex.length) {
                6 -> Triple(
                    hex.substring(0, 2).toInt(16) / 255f,
                    hex.substring(2, 4).toInt(16) / 255f,
                    hex.substring(4, 6).toInt(16) / 255f
                )
                8 -> Triple(
                    hex.substring(2, 4).toInt(16) / 255f,
                    hex.substring(4, 6).toInt(16) / 255f,
                    hex.substring(6, 8).toInt(16) / 255f
                )
                else -> Triple(0.5f, 0.5f, 0.5f)
            }
            Triple(r, g, b)
        } catch (e: Exception) {
            Triple(0.5f, 0.5f, 0.5f)
        }
    }

    private fun relativeLuminance(rgb: Triple<Float, Float, Float>): Double {
        val r = linearize(rgb.first.toDouble())
        val g = linearize(rgb.second.toDouble())
        val b = linearize(rgb.third.toDouble())
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun linearize(channel: Double): Double {
        return if (channel <= 0.03928) channel / 12.92
        else ((channel + 0.055) / 1.055).pow(2.4)
    }
}
