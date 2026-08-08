package com.foundry.preview.engine

/**
 * 诊断级别。
 */
enum class DiagnosticLevel {
    INFO, WARNING, ERROR
}

/**
 * 单条诊断信息。
 */
data class Diagnostic(
    val level: DiagnosticLevel,
    val message: String,
    val path: String = "",
    val line: Int? = null
)

/**
 * 诊断引擎：收集、查询、格式化所有解析/渲染诊断。
 */
class DiagnosticsEngine {

    private val _diagnostics = mutableListOf<Diagnostic>()
    val diagnostics: List<Diagnostic> get() = _diagnostics.toList()

    val hasErrors: Boolean get() = _diagnostics.any { it.level == DiagnosticLevel.ERROR }
    val hasWarnings: Boolean get() = _diagnostics.any { it.level == DiagnosticLevel.WARNING }
    val errorCount: Int get() = _diagnostics.count { it.level == DiagnosticLevel.ERROR }
    val warningCount: Int get() = _diagnostics.count { it.level == DiagnosticLevel.WARNING }

    fun addError(message: String, path: String = "") {
        _diagnostics.add(Diagnostic(DiagnosticLevel.ERROR, message, path))
    }

    fun addWarning(message: String, path: String = "") {
        _diagnostics.add(Diagnostic(DiagnosticLevel.WARNING, message, path))
    }

    fun addInfo(message: String, path: String = "") {
        _diagnostics.add(Diagnostic(DiagnosticLevel.INFO, message, path))
    }

    fun add(diagnostic: Diagnostic) {
        _diagnostics.add(diagnostic)
    }

    fun addAll(items: List<Diagnostic>) {
        _diagnostics.addAll(items)
    }

    fun clear() {
        _diagnostics.clear()
    }

    /**
     * 生成人类可读的诊断报告。
     */
    fun formatReport(): String {
        if (_diagnostics.isEmpty()) return "✅ No issues found."

        val sb = StringBuilder()
        val errors = _diagnostics.filter { it.level == DiagnosticLevel.ERROR }
        val warnings = _diagnostics.filter { it.level == DiagnosticLevel.WARNING }
        val infos = _diagnostics.filter { it.level == DiagnosticLevel.INFO }

        if (errors.isNotEmpty()) {
            sb.appendLine("❌ Errors (${errors.size}):")
            errors.forEach { sb.appendLine("  [${it.path}] ${it.message}") }
        }
        if (warnings.isNotEmpty()) {
            sb.appendLine("⚠️ Warnings (${warnings.size}):")
            warnings.forEach { sb.appendLine("  [${it.path}] ${it.message}") }
        }
        if (infos.isNotEmpty()) {
            sb.appendLine("ℹ️ Info (${infos.size}):")
            infos.forEach { sb.appendLine("  [${it.path}] ${it.message}") }
        }
        return sb.toString()
    }
}
