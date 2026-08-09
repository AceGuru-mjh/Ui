package com.foundry.preview.plugin

import com.foundry.core.plugin.*
import com.foundry.core.uimodel.*
import com.foundry.preview.dsl.UiParser
import com.foundry.preview.dsl.UiValidator
import com.foundry.preview.engine.Diagnostic as EngineDiagnostic
import com.foundry.preview.engine.DiagnosticLevel as EngineLevel

/**
 * 平台化后的“第一个插件”：把现有的 JSON DSL（dsl.UiParser / UiValidator）
 * 适配进统一插件契约 UiFormatPlugin。
 *
 * 这是 Strangler 模式的第一步——现有渲染层（ComponentRenderer）暂不改动，
 * 仅把输入侧与诊断侧统一到 UiGraph / 跨插件 Diagnostic 模型。
 */
class AndroidUiJsonPlugin : UiFormatPlugin {

    override val descriptor: PluginDescriptor = PluginDescriptor(
        id = "com.foundry.plugin.json.dsl",
        version = "1.0.0",
        displayName = "ComposeFoundry JSON DSL",
        supportedMimeTypes = setOf("application/json", "text/json"),
        supportedExtensions = setOf("json", "androidui.json"),
        capabilities = setOf(
            UiCapability.PARSE,
            UiCapability.RENDER_STATIC,
            UiCapability.RENDER_INTERACTIVE
        ),
        previewLevels = setOf(PreviewLevel.L3_STATIC_VISUAL, PreviewLevel.L4_INTERACTIVE),
        priority = 100
    )

    override fun canHandle(artifact: UiArtifact): PluginMatch {
        if (artifact.detectedKind == ArtifactKind.JSON_DSL) {
            return PluginMatch(1.0, Confidence.HIGH, "kind already detected as JSON_DSL")
        }
        val content = artifact.content.orEmpty().trim()
        val looksLikeDsl =
            content.startsWith("{") && "type" in content && "root" in content
        val extOk = artifact.extension in descriptor.supportedExtensions
        return when {
            looksLikeDsl -> PluginMatch(0.9, Confidence.HIGH, "JSON contains dsl 'type'+'root'")
            extOk -> PluginMatch(0.7, Confidence.MEDIUM, "extension matches json dsl")
            else -> PluginMatch(0.0, Confidence.NONE, "not a JSON DSL artifact")
        }
    }

    override suspend fun parse(artifact: UiArtifact, context: PreviewContext): ParseResult {
        val content = artifact.content ?: return ParseResult.Failed(
            listOf(
                mapError("No content available for artifact '${artifact.displayName}'")
            )
        )

        val doc = UiParser().parse(content).getOrElse { e ->
            return ParseResult.Failed(
                listOf(
                    mapError("Parse failed: ${e.message ?: e::class.simpleName}")
                )
            )
        }

        val validator = UiValidator()
        val engineDiags = validator.validate(doc)
        val diagnostics = engineDiags.map { mapDiagnostic(it) }

        val root = toUiNode(doc.root, "root")

        val confidence = when {
            engineDiags.any { it.level == EngineLevel.ERROR } -> Confidence.LOW
            engineDiags.any { it.level == EngineLevel.WARNING } -> Confidence.MEDIUM
            else -> Confidence.HIGH
        }

        val rawGraph = UiGraph(
            id = "graph:${artifact.id}",
            sourceArtifactId = artifact.id,
            root = root,
            themes = listOf(
                ThemeSpec(
                    name = "default",
                    attributes = mapOf(
                        "primaryColor" to UiValue.ColorVal(doc.theme.primaryColor),
                        "backgroundColor" to UiValue.ColorVal(doc.theme.backgroundColor),
                        "surfaceColor" to UiValue.ColorVal(doc.theme.surfaceColor),
                        "textColor" to UiValue.ColorVal(doc.theme.textColor),
                        "isDark" to UiValue.Bool(doc.theme.isDark)
                    )
                )
            ),
            diagnostics = diagnostics,
            capabilities = descriptor.capabilities,
            meta = UiGraphMeta(
                title = artifact.displayName,
                format = "json-dsl",
                parser = descriptor.id,
                parserVersion = descriptor.version,
                previewLevel = PreviewLevel.L4_INTERACTIVE,
                confidence = confidence
            )
        )

        // 资源解析 + 结构校验：把核心库基础工具接入实际管线
        val resolved = rawGraph.resolveResources()
        val validationDiags = resolved.validate()
        val allDiagnostics = diagnostics + validationDiags
        val finalConfidence = deriveConfidence(allDiagnostics)
        val finalGraph = resolved.copy(
            diagnostics = allDiagnostics,
            meta = resolved.meta.copy(confidence = finalConfidence)
        )

        return if (finalConfidence == Confidence.LOW) {
            ParseResult.Partial(finalGraph, allDiagnostics)
        } else {
            ParseResult.Success(finalGraph, allDiagnostics)
        }
    }

    private fun deriveConfidence(diags: List<Diagnostic>): Confidence = when {
        diags.any { it.severity == Severity.ERROR } -> Confidence.LOW
        diags.any { it.severity == Severity.WARNING } -> Confidence.MEDIUM
        else -> Confidence.HIGH
    }

    private fun mapDiagnostic(d: EngineDiagnostic): Diagnostic {
        val severity = when (d.level) {
            EngineLevel.INFO -> Severity.INFO
            EngineLevel.WARNING -> Severity.WARNING
            EngineLevel.ERROR -> Severity.ERROR
        }
        return Diagnostic(
            severity = severity,
            code = "DSL_${d.level.name}",
            message = d.message,
            sourcePlugin = descriptor.id,
            location = SourceLocation(nodeId = d.path.ifEmpty { null }, line = d.line),
            confidence = when (severity) {
                Severity.ERROR -> Confidence.LOW
                Severity.WARNING -> Confidence.MEDIUM
                Severity.INFO -> Confidence.HIGH
            }
        )
    }

    private fun mapError(message: String): Diagnostic = Diagnostic(
        severity = Severity.ERROR,
        code = "DSL_ERROR",
        message = message,
        sourcePlugin = descriptor.id,
        confidence = Confidence.NONE
    )
}
