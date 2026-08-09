package com.foundry.core.plugin

import com.foundry.core.uimodel.*
import kotlinx.serialization.Serializable

/**
 * 插件化格式系统的契约层。
 * 每个输入格式（JSON DSL / Android XML / Compose 代码 / APK ...）都实现 UiFormatPlugin。
 */

@Serializable
enum class ArtifactSource { FILE, FOLDER, APK, AAB, PROJECT, SNIPPET }

@Serializable
enum class ArtifactKind {
    JSON_DSL, ANDROID_XML_LAYOUT, ANDROID_XML_DRAWABLE, ANDROID_XML_VALUES,
    ANDROID_MANIFEST, ANDROID_NAVIGATION, ANDROID_MENU,
    KOTLIN_COMPOSE, KOTLIN_VIEW, JAVA_VIEW,
    APK, AAB, IMAGE, FONT, UNKNOWN_BINARY, UNKNOWN_TEXT
}

/** 输入工件：文件 / 文件夹 / APK / AAB / 项目 统一抽象。 */
@Serializable
data class UiArtifact(
    val id: String,
    val uri: String,
    val displayName: String,
    val content: String? = null,
    val mimeType: String? = null,
    val extension: String? = null,
    val sizeBytes: Long? = null,
    val source: ArtifactSource = ArtifactSource.FILE,
    val detectedKind: ArtifactKind? = null
)

@Serializable
data class PluginDescriptor(
    val id: String,
    val version: String,
    val displayName: String,
    val supportedMimeTypes: Set<String> = emptySet(),
    val supportedExtensions: Set<String> = emptySet(),
    val capabilities: Set<UiCapability> = emptySet(),
    val previewLevels: Set<PreviewLevel> = emptySet(),
    val priority: Int = 0
) {
    /** 边界校验（失败快速）：id/version 非空且 id 符合插件命名规范。 */
    fun isValid(): Boolean =
        id.isNotBlank() && version.isNotBlank() &&
            id.matches(Regex("[A-Za-z0-9._-]+"))
}

/** 格式判定得分：多个插件竞争同一文件时按 score 取最高。 */
@Serializable
data class PluginMatch(
    val score: Double,
    val confidence: Confidence,
    val reason: String
)

@Serializable
sealed interface ParseResult {
    val diagnostics: List<Diagnostic>
    @Serializable
    data class Success(val graph: UiGraph, override val diagnostics: List<Diagnostic>) : ParseResult
    @Serializable
    data class Partial(val graph: UiGraph?, override val diagnostics: List<Diagnostic>) : ParseResult
    @Serializable
    data class Failed(override val diagnostics: List<Diagnostic>) : ParseResult
}

@Serializable
enum class ThemeMode { LIGHT, DARK, SYSTEM }

@Serializable
data class DeviceProfile(val name: String, val widthDp: Int, val heightDp: Int)

@Serializable
data class PreviewContext(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val locale: String? = null,
    val deviceProfile: DeviceProfile? = null,
    /** 外部资源表（strings/colors/dimens），供插件解析 @string/@color/@dimen 引用。 */
    val resourceTable: ResourceTable? = null
)

interface UiFormatPlugin {
    val descriptor: PluginDescriptor
    fun canHandle(artifact: UiArtifact): PluginMatch
    suspend fun parse(artifact: UiArtifact, context: PreviewContext): ParseResult
}
