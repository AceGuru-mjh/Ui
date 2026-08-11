package com.foundry.preview.state

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.foundry.core.plugin.ArtifactDetector
import com.foundry.core.plugin.ParseResult
import com.foundry.core.plugin.PluginManager
import com.foundry.core.plugin.PreviewContext
import com.foundry.core.plugin.UiArtifact
import com.foundry.core.uimodel.UiCapability
import com.foundry.core.uimodel.UiGraph
import com.foundry.preview.plugin.toUiDocument
import com.foundry.preview.project.buildResourceTable
import com.foundry.preview.project.findAndroidProjectRoot
import com.foundry.preview.dsl.UiDocument
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 文件导入/导出委托 — 从 [FoundryViewModel] 中拆出，遵循单一职责原则。
 *
 * 职责：
 *  - JSON 文件导入 / 导出
 *  - XML 文件导入（走插件管线解析为 UiGraph → 转 DSL）
 *  - PNG 渲染位图导出
 *
 * 回调：[onCodeReplaced] 在导入成功后通知 ViewModel 更新源码，[onGraphImported]
 * 在 XML 解析为 UiGraph 并序列化为 JSON 后通知 ViewModel 替换源码。
 */
class FileIoDelegate(
    private val scope: CoroutineScope,
    private val _resourceTable: () -> com.foundry.core.uimodel.ResourceTable,
    private val _updateResourceTable: (com.foundry.core.uimodel.ResourceTable) -> Unit,
    private val onStatus: (String) -> Unit,
    private val onCodeReplaced: (code: String) -> Unit,
    /** XML 导入完成后回调：(serializedJsonCode, document, graph) */
    private val onGraphImported: (serializedCode: String, document: UiDocument, graph: UiGraph) -> Unit,
    private val onXmlFallback: (xmlContent: String, reason: String) -> Unit
) {
    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }
    fun importJsonFromUri(context: Context, uri: Uri) {
        try {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
            if (content != null) {
                onCodeReplaced(content)
                onStatus("JSON imported successfully")
            }
        } catch (e: Exception) {
            onStatus("JSON import failed: ${e.message}")
        }
    }

    fun importXmlFromUri(context: Context, uri: Uri) {
        val xmlContent = try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
        if (xmlContent == null) {
            onStatus("Failed to read XML file")
            return
        }

        scope.launch {
            // 若能通过 uri 路径解析出工程根，则刷新资源表（供 @string/@color/@dimen 解析）
            uri.path?.let { p -> findAndroidProjectRoot(File(p)) }
                ?.let {
                    val rt = buildResourceTable(it)
                    _updateResourceTable(rt)
                }
            val baseArtifact = UiArtifact(
                id = "imported-xml",
                uri = uri.toString(),
                displayName = "imported xml",
                content = xmlContent,
                extension = "xml"
            )
            val artifact = baseArtifact.copy(detectedKind = ArtifactDetector.detect(baseArtifact))
            val plugin = PluginManager.selectFor(
                artifact,
                requires = setOf(UiCapability.RENDER_INTERACTIVE)
            )
            if (plugin == null) {
                onXmlFallback(xmlContent, "无匹配插件")
                return@launch
            }
            when (val result = plugin.parse(artifact, PreviewContext(resourceTable = _resourceTable()))) {
                is ParseResult.Success -> applyImportedGraph(result.graph, xmlContent)
                is ParseResult.Partial -> applyImportedGraph(result.graph, xmlContent)
                is ParseResult.Failed -> onXmlFallback(xmlContent, "插件解析失败")
            }
        }
    }

    private fun applyImportedGraph(graph: UiGraph?, xmlContent: String) {
        val g = graph ?: run {
            onXmlFallback(xmlContent, "无图")
            return
        }
        val doc = toUiDocument(g)
        val serializedCode = prettyJson.encodeToString(doc)
        onGraphImported(serializedCode, doc, g)
        onStatus("XML imported via ${g.meta.parser}")
    }

    fun exportJsonToUri(context: Context, uri: Uri, code: String) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(code.toByteArray(Charsets.UTF_8))
            }
            onStatus("JSON exported successfully")
        } catch (e: Exception) {
            onStatus("JSON export failed: ${e.message}")
        }
    }

    fun exportPngToUri(context: Context, uri: Uri, bitmap: Bitmap) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            onStatus("PNG exported successfully")
        } catch (e: Exception) {
            onStatus("PNG export failed: ${e.message}")
        }
    }
}
