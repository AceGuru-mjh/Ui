package com.foundry.preview.plugin

import com.foundry.core.plugin.*
import com.foundry.core.uimodel.*
import com.foundry.preview.dsl.XmlLayoutParser

/**
 * 平台化后的第二个插件：把 Android XML Layout 适配进统一插件契约 UiFormatPlugin。
 * 复用现有 XmlLayoutParser 把 XML 转为 dsl.UiElement，再经适配器产出规范化 UiGraph。
 *
 * 与 JSON DSL 插件并列，由 PluginManager 按匹配得分选择；XML 导入不再单独调用
 * XmlLayoutParser，而是与其它格式一样走统一管线。
 */
class AndroidUiXmlPlugin : UiFormatPlugin {

    override val descriptor: PluginDescriptor = PluginDescriptor(
        id = "com.foundry.plugin.android.xml",
        version = "1.0.0",
        displayName = "Android XML Layout",
        supportedMimeTypes = setOf("application/xml", "text/xml"),
        supportedExtensions = setOf("xml"),
        capabilities = setOf(
            UiCapability.PARSE,
            UiCapability.RENDER_STATIC,
            UiCapability.RENDER_INTERACTIVE
        ),
        previewLevels = setOf(PreviewLevel.L3_STATIC_VISUAL, PreviewLevel.L4_INTERACTIVE),
        priority = 100
    )

    override fun canHandle(artifact: UiArtifact): PluginMatch {
        if (artifact.detectedKind == ArtifactKind.ANDROID_XML_LAYOUT) {
            return PluginMatch(1.0, Confidence.HIGH, "kind detected as ANDROID_XML_LAYOUT")
        }
        val content = artifact.content.orEmpty().trim()
        val looksLikeLayout =
            content.startsWith("<") && ("android:layout" in content || "xmlns:android" in content)
        return when {
            looksLikeLayout -> PluginMatch(0.9, Confidence.HIGH, "content looks like android layout xml")
            artifact.extension == "xml" -> PluginMatch(0.7, Confidence.MEDIUM, "extension is xml")
            else -> PluginMatch(0.0, Confidence.NONE, "not android xml")
        }
    }

    override suspend fun parse(artifact: UiArtifact, context: PreviewContext): ParseResult {
        val content = artifact.content ?: return ParseResult.Failed(
            listOf(err("No content available for artifact '${artifact.displayName}'"))
        )

        val (element, issues) = XmlLayoutParser().parse(content).getOrElse { e ->
            return ParseResult.Failed(
                listOf(err("XML parse failed: ${e.message ?: e::class.simpleName}"))
            )
        }

        val root = toUiNode(element, "root")

        // 把解析期降级/忽略问题转化为跨插件统一诊断
        val parseDiags = issues.map { issue ->
            Diagnostic(
                severity = Severity.WARNING,
                code = "XML_DOWNGRADE",
                message = issue,
                sourcePlugin = descriptor.id,
                confidence = Confidence.MEDIUM
            )
        }

        // 资源表优先用调用方提供的；否则空表（@string 等引用保留并给出提示诊断）
        val resourceTable = context.resourceTable ?: ResourceTable()

        val rawGraph = UiGraph(
            id = "graph:${artifact.id}",
            sourceArtifactId = artifact.id,
            root = root,
            resources = resourceTable,
            diagnostics = parseDiags,
            capabilities = descriptor.capabilities,
            meta = UiGraphMeta(
                title = artifact.displayName,
                format = "android-xml",
                parser = descriptor.id,
                parserVersion = descriptor.version,
                previewLevel = PreviewLevel.L3_STATIC_VISUAL,
                confidence = Confidence.HIGH
            )
        )

        // 资源解析：@string/@color/@dimen 引用在 resourceTable 命中时替换为具体值
        val resolved = rawGraph.resolveResources()
        // 解析后仍残留的资源引用，给出 INFO 诊断（预览将保留引用）
        val unresolvedRefs = collectUnresolvedRefs(resolved.root)
        val unresolvedDiags = unresolvedRefs.map { ref ->
            Diagnostic(
                severity = Severity.INFO,
                code = "XML_RESOURCE_UNRESOLVED",
                message = "资源引用 '$ref' 未解析（当前无对应资源表条目），预览将保留引用",
                sourcePlugin = descriptor.id,
                confidence = Confidence.LOW
            )
        }

        val validationDiags = resolved.validate()
        val allDiagnostics = parseDiags + unresolvedDiags + validationDiags
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

    /** 收集图中仍为 ResourceRef（未被解析）的属性与修饰符资源引用。 */
    private fun collectUnresolvedRefs(node: UiNode?): List<String> {
        if (node == null) return emptyList()
        val refs = mutableListOf<String>()
        node.attributes.values.forEach { v ->
            if (v is UiValue.ResourceRef) refs += v.ref
        }
        node.modifiers.forEach { m ->
            m.background?.let { if (it.startsWith("@")) refs += it }
            m.borderColor?.let { if (it.startsWith("@")) refs += it }
        }
        node.children.forEach { refs += collectUnresolvedRefs(it) }
        return refs.distinct()
    }

    private fun deriveConfidence(diags: List<Diagnostic>): Confidence = when {
        diags.any { it.severity == Severity.ERROR } -> Confidence.LOW
        diags.any { it.severity == Severity.WARNING } -> Confidence.MEDIUM
        else -> Confidence.HIGH
    }

    private fun err(message: String): Diagnostic = Diagnostic(
        severity = Severity.ERROR,
        code = "XML_ERROR",
        message = message,
        sourcePlugin = descriptor.id,
        confidence = Confidence.NONE
    )
}
