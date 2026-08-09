package com.foundry.core.plugin

/**
 * 工件类型探测层（ArtifactDetector）。
 *
 * 在插件匹配之前，先用扩展名 / MIME / magic bytes / 结构信号把输入归一为
 * [ArtifactKind]，使 [UiFormatPlugin.canHandle] 能基于可靠的 [UiArtifact.detectedKind]
 * 决策，而非各自用脆弱的子串匹配互相抢同一个文件。
 *
 * 纯 Kotlin 实现，不依赖 Android Framework，可在 CLI / 服务端 / KMP 复用。
 *
 * 探测优先级（越具体越优先）：
 *  1. 结构特征（解析内容判定 JSON DSL / Android XML Layout / 清单 / 导航 …）
 *  2. MIME 类型
 *  3. 扩展名
 *  4. magic bytes（二进制头）
 * 全部无法判定时回退到 [ArtifactKind.UNKNOWN_TEXT] / [ArtifactKind.UNKNOWN_BINARY]。
 */
object ArtifactDetector {

    /** 根据工件推断类型；content 为空时退化为仅按扩展名 / MIME 判断。 */
    fun detect(artifact: UiArtifact): ArtifactKind {
        val content = artifact.content
        if (!content.isNullOrBlank()) {
            detectFromContent(content)?.let { return it }
        }
        detectFromMime(artifact.mimeType)?.let { return it }
        detectFromExtension(artifact.extension)?.let { return it }
        return if (looksBinary(content)) ArtifactKind.UNKNOWN_BINARY else ArtifactKind.UNKNOWN_TEXT
    }

    /** 仅基于内容字符串推断（留出 mime/ext 不可用的场景）。 */
    fun detectFromContent(content: String): ArtifactKind? {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            // JSON：进一步判断是否为本平台 DSL（含 type + root）
            val isDsl = "type" in trimmed && "root" in trimmed
            return if (isDsl) ArtifactKind.JSON_DSL else ArtifactKind.UNKNOWN_TEXT
        }

        if (trimmed.startsWith("<")) {
            return detectXmlKind(trimmed)
        }

        // Kotlin / Java 源码特征
        if (trimmed.contains("@Composable") ||
            trimmed.contains("fun ") && trimmed.contains("androidx.compose")
        ) {
            return ArtifactKind.KOTLIN_COMPOSE
        }
        if (trimmed.contains("import android.") || trimmed.contains("public class")) {
            return if (trimmed.contains("View") || trimmed.contains("android.view")) {
                ArtifactKind.KOTLIN_VIEW
            } else {
                ArtifactKind.JAVA_VIEW
            }
        }
        return null
    }

    /** 解析 XML 内容，区分 layout / manifest / navigation / drawable / values / menu。 */
    fun detectXmlKind(content: String): ArtifactKind {
        val lower = content.lowercase()
        // 优先按根标签判定具体类型，避免被 xmlns:android 误判为 layout
        when {
            "<manifest" in lower -> return ArtifactKind.ANDROID_MANIFEST
            "<navigation" in lower -> return ArtifactKind.ANDROID_NAVIGATION
            "<menu" in lower -> return ArtifactKind.ANDROID_MENU
            "<vector" in lower || "<shape" in lower || "<selector" in lower ||
                "<ripple" in lower || "<layer-list" in lower ||
                "<level-list" in lower || "<animated-vector" in lower -> return ArtifactKind.ANDROID_XML_DRAWABLE
            "<resources" in lower -> return ArtifactKind.ANDROID_XML_VALUES
            // layout：含 android 命名空间或 layout 属性
            "android:layout" in lower || "xmlns:android" in lower -> return ArtifactKind.ANDROID_XML_LAYOUT
            else -> return ArtifactKind.ANDROID_XML_LAYOUT
        }
    }

    /** 基于 MIME 推断。 */
    fun detectFromMime(mimeType: String?): ArtifactKind? {
        val mime = mimeType?.lowercase() ?: return null
        return when {
            mime.endsWith("/json") -> ArtifactKind.JSON_DSL
            mime.endsWith("/xml") || mime.endsWith("+xml") -> ArtifactKind.ANDROID_XML_LAYOUT
            mime.startsWith("image/") -> ArtifactKind.IMAGE
            mime.startsWith("font/") -> ArtifactKind.FONT
            mime == "application/vnd.android.package-archive" -> ArtifactKind.APK
            mime == "application/octet-stream" -> null
            else -> null
        }
    }

    /** 基于文件扩展名推断。 */
    fun detectFromExtension(extension: String?): ArtifactKind? {
        val ext = extension?.lowercase() ?: return null
        return when (ext) {
            "json", "androidui.json" -> ArtifactKind.JSON_DSL
            "xml" -> ArtifactKind.ANDROID_XML_LAYOUT
            "kt", "kts" -> ArtifactKind.KOTLIN_COMPOSE
            "java" -> ArtifactKind.JAVA_VIEW
            "apk" -> ArtifactKind.APK
            "aab" -> ArtifactKind.AAB
            "png", "jpg", "jpeg", "webp", "gif", "svg", "avif" -> ArtifactKind.IMAGE
            "ttf", "otf", "woff", "woff2" -> ArtifactKind.FONT
            else -> null
        }
    }

    /** 粗略判断内容是否像二进制（无法文本判定时使用）。 */
    fun looksBinary(content: String?): Boolean {
        if (content == null) return false
        // 存在大量控制字符（非空白、非可打印）即视为二进制
        return content.any { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
    }
}
