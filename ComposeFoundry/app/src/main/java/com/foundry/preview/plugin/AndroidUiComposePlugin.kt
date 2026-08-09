package com.foundry.preview.plugin

import com.foundry.core.plugin.*
import com.foundry.core.uimodel.*
import com.foundry.preview.dsl.ComposeSourceParser
import com.foundry.preview.dsl.UiElement

/**
 * 平台化后的第三个插件（Stage 4 预览原型）：把 Jetpack Compose Kotlin 源码（.kt）
 * 解析为规范化 UiGraph。
 *
 * 当前为「静态结构预览」原型：识别 @Composable fun 及其内部 Compose 组件调用与
 * Modifier 链，产出近似的 UiNode 树。不执行代码（沙箱执行属于后续阶段），
 * 因此只覆盖声明式 UI 结构，无法反映运行时状态/条件分支/循环结果。
 */
class AndroidUiComposePlugin : UiFormatPlugin {

    override val descriptor: PluginDescriptor = PluginDescriptor(
        id = "com.foundry.plugin.compose.source",
        version = "0.1.0",
        displayName = "Compose Kotlin Source",
        supportedMimeTypes = setOf("text/x-kotlin", "text/plain"),
        supportedExtensions = setOf("kt", "kts"),
        capabilities = setOf(
            UiCapability.PARSE,
            UiCapability.RENDER_STATIC,
            UiCapability.RENDER_INTERACTIVE
        ),
        previewLevels = setOf(PreviewLevel.L1_STRUCTURE, PreviewLevel.L2_WIREFRAME, PreviewLevel.L3_STATIC_VISUAL),
        priority = 90
    )

    override fun canHandle(artifact: UiArtifact): PluginMatch {
        if (artifact.detectedKind == ArtifactKind.KOTLIN_COMPOSE) {
            return PluginMatch(1.0, Confidence.HIGH, "kind detected as KOTLIN_COMPOSE")
        }
        val content = artifact.content.orEmpty().trim()
        val looksLikeCompose =
            content.contains("@Composable") && (content.contains("import androidx.compose") || content.contains("Column(") || content.contains("Row("))
        return when {
            looksLikeCompose -> PluginMatch(0.9, Confidence.HIGH, "content looks like compose kotlin source")
            artifact.extension in setOf("kt", "kts") -> PluginMatch(0.6, Confidence.MEDIUM, "extension is kotlin")
            else -> PluginMatch(0.0, Confidence.NONE, "not compose source")
        }
    }

    override suspend fun parse(artifact: UiArtifact, context: PreviewContext): ParseResult {
        val content = artifact.content ?: return ParseResult.Failed(
            listOf(err("No content available for artifact '${artifact.displayName}'"))
        )

        val (roots, issues) = ComposeSourceParser().parse(content).getOrElse { e ->
            return ParseResult.Failed(listOf(err("Compose parse failed: ${e.message ?: e::class.simpleName}")))
        }

        if (roots.isEmpty()) {
            return ParseResult.Failed(
                listOf(err("No @Composable function found — cannot preview")) + issues.map { warn(it) }
            )
        }

        // 多 @Composable / @Preview 入口：各自为独立预览根，并列渲染（不再折叠丢失）
        val rootNodes = roots.map { (name, element) -> toUiNode(element, "root:$name") }
        val root = if (rootNodes.size == 1) rootNodes.first() else UiNode(
            id = "root",
            type = "Box",
            children = rootNodes,
            confidence = Confidence.HIGH
        )
        val rootNode = root

        val parseDiags = issues.map { warn(it) }
        val multiRootInfo = if (roots.size > 1) {
            roots.keys.mapIndexed { idx, name ->
                Diagnostic(
                    severity = Severity.INFO,
                    code = "COMPOSE_PREVIEW",
                    message = "预览入口[$idx]: $name",
                    sourcePlugin = descriptor.id,
                    confidence = Confidence.MEDIUM
                )
            }
        } else emptyList()

        val resourceTable = context.resourceTable ?: ResourceTable()
        val rawGraph = UiGraph(
            id = "graph:${artifact.id}",
            sourceArtifactId = artifact.id,
            root = rootNode,
            resources = resourceTable,
            diagnostics = parseDiags + multiRootInfo,
            capabilities = descriptor.capabilities,
            meta = UiGraphMeta(
                title = artifact.displayName,
                format = "compose-kotlin",
                parser = descriptor.id,
                parserVersion = descriptor.version,
                previewLevel = PreviewLevel.L3_STATIC_VISUAL,
                confidence = Confidence.MEDIUM
            )
        )

        val resolved = rawGraph.resolveResources()
        val validationDiags = resolved.validate()
        val allDiagnostics = parseDiags + multiRootInfo + validationDiags
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

    private fun err(message: String): Diagnostic = Diagnostic(
        severity = Severity.ERROR, code = "COMPOSE_ERROR",
        message = message, sourcePlugin = descriptor.id, confidence = Confidence.NONE
    )

    private fun warn(message: String): Diagnostic = Diagnostic(
        severity = Severity.WARNING, code = "COMPOSE_DOWNGRADE",
        message = message, sourcePlugin = descriptor.id, confidence = Confidence.MEDIUM
    )
}
