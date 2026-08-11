package com.foundry.plugin.version

import android.util.Xml
import com.foundry.core.plugin.*
import com.foundry.core.uimodel.*
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

/**
 * A 类动态插件 —— 真实格式解析器（AndroidManifest.xml）。
 *
 * 与内置 [AndroidUiXmlPlugin]（处理 layout）分工：本插件专门识别 <manifest> 根，
 * 提取 package / minSdk / targetSdk / uses-permission / activity / service，
 * 渲染为可读的结构树。通过 DexClassLoader 从网络下载热加载，演示“格式解析补丁”式热更新。
 *
 * 契约对齐（已核对真实代码）：
 *  - capabilities 含 RENDER_INTERACTIVE：FoundryViewModel.render() 的 selectFor 硬性要求；
 *  - canHandle 仅对 manifest 输入 score>0（detectedKind 或内容起始 <manifest），
 *    避免误伤内置 JSON/XML 插件；priority=200 高于内置插件(100)确保被选中；
 *  - 节点 type 用 "column"（ColumnRenderer）+ "text"（TextRenderer），均为主程序已注册渲染器；
 *  - 属性用 UiValue.Text/ColorVal/Number，经 toUiElement.toRawString() 直接交给渲染器。
 */
class ManifestPlugin : UiFormatPlugin {

    override val descriptor = PluginDescriptor(
        id = "com.foundry.plugin.manifest",
        version = "1.0.0",
        displayName = "Android Manifest 解析器 (A类)",
        capabilities = setOf(UiCapability.PARSE, UiCapability.RENDER_INTERACTIVE),
        priority = 200
    )

    override fun canHandle(artifact: UiArtifact): PluginMatch {
        if (artifact.detectedKind == ArtifactKind.ANDROID_MANIFEST) {
            return PluginMatch(1.0, Confidence.HIGH, "detected as AndroidManifest")
        }
        val c = artifact.content.orEmpty().trim()
        return if (c.startsWith("<manifest")) {
            PluginMatch(0.9, Confidence.HIGH, "content begins with <manifest")
        } else {
            PluginMatch(0.0, Confidence.LOW, "not a manifest artifact")
        }
    }

    override suspend fun parse(artifact: UiArtifact, context: PreviewContext): ParseResult {
        val content = artifact.content ?: return ParseResult.Failed(
            listOf(err("No content available for '${artifact.displayName}'"))
        )
        val model = try {
            parseManifest(content)
        } catch (e: Exception) {
            return ParseResult.Failed(
                listOf(err("Manifest parse failed: ${e.message ?: e::class.simpleName}"))
            )
        }
        return ParseResult.Success(buildGraph(model, artifact), emptyList())
    }

    private fun parseManifest(xml: String): ManifestModel {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setInput(StringReader(xml))
        val permissions = mutableListOf<String>()
        val activities = mutableListOf<String>()
        val services = mutableListOf<String>()
        var pkg = ""
        var minSdk = ""
        var targetSdk = ""
        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "manifest" -> pkg = parser.getAttributeValue(null, "package") ?: pkg
                    "uses-sdk" -> {
                        minSdk = parser.getAttributeValue(ANDROID_NS, "minSdkVersion") ?: minSdk
                        targetSdk = parser.getAttributeValue(ANDROID_NS, "targetSdkVersion") ?: targetSdk
                    }
                    "uses-permission" ->
                        parser.getAttributeValue(ANDROID_NS, "name")?.let { permissions += it }
                    "activity" ->
                        parser.getAttributeValue(ANDROID_NS, "name")?.let { activities += it }
                    "service" ->
                        parser.getAttributeValue(ANDROID_NS, "name")?.let { services += it }
                }
            }
            event = parser.next()
        }
        return ManifestModel(pkg, minSdk, targetSdk, permissions, activities, services)
    }

    private fun buildGraph(m: ManifestModel, artifact: UiArtifact): UiGraph {
        val children = mutableListOf<UiNode>()
        var i = 0
        fun line(text: String, color: String, size: Double) {
            children += UiNode(
                id = "n${i++}",
                type = "text",
                attributes = mapOf(
                    "text" to UiValue.Text(text),
                    "color" to UiValue.ColorVal(color),
                    "fontSize" to UiValue.Number(size)
                )
            )
        }
        line("AndroidManifest 预览", "#1565C0", 22.0)
        line("package: ${m.pkg.ifEmpty { "(unknown)" }}", "#212121", 16.0)
        line(
            "minSdk=${m.minSdk.ifEmpty { "?" }}  targetSdk=${m.targetSdk.ifEmpty { "?" }}",
            "#616161", 14.0
        )
        line("Permissions (${m.permissions.size}):", "#E65100", 16.0)
        if (m.permissions.isEmpty()) line("  (none)", "#9E9E9E", 13.0)
        else m.permissions.forEach { line("  • $it", "#424242", 13.0) }
        line("Activities (${m.activities.size}):", "#2E7D32", 16.0)
        if (m.activities.isEmpty()) line("  (none)", "#9E9E9E", 13.0)
        else m.activities.forEach { line("  • $it", "#424242", 13.0) }
        line("Services (${m.services.size}):", "#6A1B9A", 16.0)
        if (m.services.isEmpty()) line("  (none)", "#9E9E9E", 13.0)
        else m.services.forEach { line("  • $it", "#424242", 13.0) }

        val root = UiNode(id = "root", type = "column", children = children)
        return UiGraph(
            id = "graph:${artifact.id}",
            sourceArtifactId = artifact.id,
            root = root,
            meta = UiGraphMeta(
                title = "Manifest: ${m.pkg}",
                format = "android-manifest",
                parser = descriptor.id,
                parserVersion = descriptor.version,
                previewLevel = PreviewLevel.L3_STATIC_VISUAL,
                confidence = Confidence.HIGH
            )
        )
    }

    private fun err(message: String) = Diagnostic(
        severity = Severity.ERROR, code = "MANIFEST_ERROR",
        message = message, sourcePlugin = descriptor.id, confidence = Confidence.NONE
    )

    private data class ManifestModel(
        val pkg: String, val minSdk: String, val targetSdk: String,
        val permissions: List<String>, val activities: List<String>, val services: List<String>
    )

    companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
