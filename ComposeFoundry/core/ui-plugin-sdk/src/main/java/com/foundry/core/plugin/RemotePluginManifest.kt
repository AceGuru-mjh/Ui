package com.foundry.core.plugin

import kotlinx.serialization.Serializable

/**
 * 云端插件的声明（对应文章第四节的 RemotePluginManifest）。
 *
 * 纯数据、可序列化、无任何 Android 依赖。让主程序与所有下载的插件共享同一份定义，
 * 保证插件在被独立 ClassLoader 加载时，也能正确识别主程序传来的 [UiFormatPlugin] 契约对象。
 */
@Serializable
data class RemotePluginManifest(
    /** 插件唯一 id，必须与插件内 [PluginDescriptor.id] 一致，用于去重与热更新。 */
    val id: String,
    /** 语义化版本号。 */
    val version: String,
    /** 展示名（市场 UI 用）。 */
    val displayName: String,
    /** 云端下载地址（dex / jar / apk）。 */
    val downloadUrl: String,
    /** 文件 SHA-256（小写十六进制），用于下载后完整性校验（防代码注入）。 */
    val sha256: String,
    /** 插件入口类全限定名，例如 "com.foundry.plugin.xml.XmlFormatPlugin"。 */
    val entryClass: String,
    val sizeBytes: Long? = null,
    val homepage: String? = null,
    val description: String? = null,
    /** 要求的主程序 plugin-sdk 最低版本，用于能力协商（Capability Negotiation）。 */
    val minCoreVersion: String? = null,
    /** 要求的最低 Android API Level。 */
    val minSdk: Int? = null,
    /** 可选：插件作者签名（base64），用于后续签名校验阶段。 */
    val signature: String? = null
)

/** 云端仓库索引：一组可用插件 + 仓库自身元数据。 */
@Serializable
data class PluginRepositoryIndex(
    val repositoryUrl: String? = null,
    val updatedAt: String? = null,
    val plugins: List<RemotePluginManifest> = emptyList()
)
