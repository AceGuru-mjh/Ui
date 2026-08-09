package com.foundry.core.uimodel

import kotlinx.serialization.Serializable

/**
 * 规范化 UI 图（Normalized UI Graph）核心模型。
 *
 * 所有格式（JSON DSL / Android XML / Compose 代码 / APK 资源 ...）
 * 最终都转换为 UiGraph，再交给任意渲染后端呈现。
 * 本模块不依赖 Android Framework，可被 Android / Desktop / CLI 共享。
 *
 * 所有公共类型均标注 [Serializable]，使 UiGraph 可作为跨进程 / 跨插件 /
 * 落盘的统一交换格式（序列化由 kotlinx.serialization 提供）。
 */

/** 预览保真度等级：L0 元数据 ~ L5 运行时仿真。 */
@Serializable
enum class PreviewLevel {
    L0_METADATA, L1_STRUCTURE, L2_WIREFRAME, L3_STATIC_VISUAL, L4_INTERACTIVE, L5_RUNTIME_EMULATED
}

/** 系统对每个预览结果有多确定。 */
@Serializable
enum class Confidence { NONE, LOW, MEDIUM, HIGH }

@Serializable
enum class Severity { INFO, WARNING, ERROR }

@Serializable
enum class UiCapability {
    PARSE, RENDER_STATIC, RENDER_INTERACTIVE, RESOURCE_RESOLVE, CODE_EDIT
}

/** 属性值：统一为带类型的值，而非裸 String。 */
@Serializable
sealed interface UiValue {
    val raw: String
    @Serializable
    data class Text(val value: String) : UiValue { override val raw = value }
    @Serializable
    data class Number(val value: Double) : UiValue { override val raw = value.toString() }
    @Serializable
    data class Bool(val value: Boolean) : UiValue { override val raw = value.toString() }
    @Serializable
    data class ColorVal(val value: String) : UiValue { override val raw = value }
    @Serializable
    data class Dimen(val value: String) : UiValue { override val raw = value }
    @Serializable
    data class ResourceRef(val ref: String) : UiValue { override val raw = ref }   // @string/title_home
    @Serializable
    data class Expression(val source: String) : UiValue { override val raw = source } // if(x) a else b
    @Serializable
    data class Unknown(val value: String) : UiValue { override val raw = value }
}

@Serializable
data class PaddingSpec(
    val all: Float? = null,
    val horizontal: Float? = null,
    val vertical: Float? = null,
    val start: Float? = null,
    val top: Float? = null,
    val end: Float? = null,
    val bottom: Float? = null
)

/** 对应现有 dsl.UiModifierSpec，但独立于 dsl 包，便于核心层解耦。 */
@Serializable
data class UiModifier(
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val fillMaxSize: Boolean = false,
    val width: Float? = null,
    val height: Float? = null,
    val padding: PaddingSpec? = null,
    val background: String? = null,
    val cornerRadius: Float? = null,
    val borderWidth: Float? = null,
    val borderColor: String? = null,
    val elevation: Float? = null,
    val horizontalAlignment: String? = null,
    val verticalArrangement: String? = null,
    val verticalAlignment: String? = null,
    val horizontalArrangement: String? = null,
    val contentAlignment: String? = null,
    val align: String? = null,
    val scrollable: Boolean = false
)

@Serializable
data class UiNode(
    val id: String,
    val type: String,
    val attributes: Map<String, UiValue> = emptyMap(),
    val modifiers: List<UiModifier> = emptyList(),
    val children: List<UiNode> = emptyList(),
    val source: SourceLocation? = null,
    val confidence: Confidence = Confidence.HIGH
)

@Serializable
data class ResourceTable(
    val strings: Map<String, String> = emptyMap(),
    val colors: Map<String, String> = emptyMap(),
    val dimens: Map<String, String> = emptyMap()
)

@Serializable
data class ThemeSpec(
    val name: String,
    val parent: String? = null,
    val attributes: Map<String, UiValue> = emptyMap()
)

@Serializable
data class UiGraphMeta(
    val title: String?,
    val format: String,
    val parser: String,
    val parserVersion: String,
    val previewLevel: PreviewLevel,
    val confidence: Confidence
)

@Serializable
data class UiGraph(
    val id: String,
    val sourceArtifactId: String,
    val root: UiNode?,
    val resources: ResourceTable = ResourceTable(),
    val themes: List<ThemeSpec> = emptyList(),
    val diagnostics: List<Diagnostic> = emptyList(),
    val capabilities: Set<UiCapability> = emptySet(),
    val meta: UiGraphMeta
)

@Serializable
data class SourceLocation(
    val artifactId: String? = null,
    val filePath: String? = null,
    val line: Int? = null,
    val column: Int? = null,
    val nodeId: String? = null
)

/** 跨插件统一诊断模型；带来源插件与置信度，使任何成功/降级/失败都可解释。 */
@Serializable
data class Diagnostic(
    val severity: Severity,
    val code: String,
    val message: String,
    val sourcePlugin: String,
    val location: SourceLocation? = null,
    val suggestion: String? = null,
    val confidence: Confidence? = null
)

/**
 * 规范化 UI 图的纯函数工具集（不可变转换，便于管线组合）。
 */
object UiGraphs {

    /** 构造一个空的规范化图（无根节点、最低保真度），用于占位或初始化。 */
    fun empty(
        id: String,
        format: String = "empty",
        parser: String = "none"
    ): UiGraph = UiGraph(
        id = id,
        sourceArtifactId = id,
        root = null,
        meta = UiGraphMeta(
            title = id,
            format = format,
            parser = parser,
            parserVersion = "0.0.0",
            previewLevel = PreviewLevel.L0_METADATA,
            confidence = Confidence.NONE
        )
    )
}

/** 返回追加诊断后的新图（不可变）。 */
fun UiGraph.withDiagnostics(extra: List<Diagnostic>): UiGraph =
    copy(diagnostics = diagnostics + extra)

/** 返回追加单条诊断后的新图（不可变）。 */
fun UiGraph.withDiagnostic(d: Diagnostic): UiGraph = withDiagnostics(listOf(d))

/** 深度优先查找第一个满足谓词的节点。 */
fun UiNode.findFirst(predicate: (UiNode) -> Boolean): UiNode? {
    if (predicate(this)) return this
    return children.firstNotNullOfOrNull { it.findFirst(predicate) }
}

/** 在整张图里深度优先查找第一个满足谓词的节点。 */
fun UiGraph.findNode(predicate: (UiNode) -> Boolean): UiNode? = root?.findFirst(predicate)

/** 图中节点总数（空图返回 0）。 */
val UiGraph.nodeCount: Int
    get() = root?.count() ?: 0

private fun UiNode.count(): Int = 1 + children.sumOf { it.count() }
