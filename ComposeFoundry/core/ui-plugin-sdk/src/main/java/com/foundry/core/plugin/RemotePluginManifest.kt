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
    /** 文件 SHA-256（小写十六进制），用于下载后完整性校验（防代码注入）。 */
    val sha256: String,
    /** 插件入口类全限定名，例如 "com.foundry.plugin.xml.XmlFormatPlugin"。 */
    val entryClass: String,
    /** 多源镜像列表（替代原有的单一 downloadUrl）；为空时退化为 downloadUrl。 */
    val mirrors: List<MirrorNode> = emptyList(),
    /**
     * 兼容旧索引的单一下载地址（已废弃，迁移到 [mirrors]）。
     * 若 mirrors 不为空则忽略此字段；否则将其包装为单一 mirror。
     */
    @Deprecated("Use mirrors instead", ReplaceWith("mirrors"))
    val downloadUrl: String = "",
    val sizeBytes: Long? = null,
    val homepage: String? = null,
    val description: String? = null,
    /** 要求的主程序 plugin-sdk 最低版本，用于能力协商（Capability Negotiation）。 */
    val minCoreVersion: String? = null,
    /** 要求的最低 Android API Level。 */
    val minSdk: Int? = null,
    /**
     * 可选：签名证书 SHA-256 指纹（小写十六进制），用于**签名钉扎**（certificate pinning）。
     * 非空时，DynamicPluginManager.install 会在加载前校验 APK 签名证书哈希是否与此一致，
     * 防止中间人替换同内容的恶意 APK（SHA-256 仅防篡改、不防伪造来源，签名钉扎补上这一环）。
     * 为空则跳过，保持仅 SHA-256 完整性校验的向后兼容行为。
     */
    val signature: String? = null
) {
    /** 解析出实际可用的下载源列表（优先 mirrors，回退到 downloadUrl）。 */
    fun resolvedMirrors(): List<MirrorNode> {
        if (mirrors.isNotEmpty()) return mirrors
        if (downloadUrl.isNotBlank()) {
            return listOf(MirrorNode(url = downloadUrl, type = MirrorType.SERVER))
        }
        return emptyList()
    }
}

/** 单个镜像节点（对应审查建议第二节的 "多源镜像列表"）。 */
@Serializable
data class MirrorNode(
    val url: String,
    val region: String = "global",
    val type: MirrorType = MirrorType.SERVER,
    val priority: Int = 0
)

@Serializable
enum class MirrorType {
    /** 自定义服务器 / OSS。 */
    SERVER,
    /** GitHub Releases。 */
    GITHUB_RELEASE,
    /** jsDelivr / CDN 代理。 */
    CDN,
    /** IPFS 去中心化网络（CID 格式为 ipfs:// 开头的 URL）。 */
    IPFS,
    /** 局域网 P2P（局域网内的其他客户端）。 */
    LAN_PEER
}

/** 云端仓库索引：一组可用插件 + 仓库自身元数据。 */
@Serializable
data class PluginRepositoryIndex(
    val repositoryUrl: String? = null,
    val updatedAt: String? = null,
    val plugins: List<RemotePluginManifest> = emptyList()
)

/** 主程序 plugin-sdk 版本号，由 CI 注入或在 core 模块内维护，用于能力协商。 */
const val CORE_VERSION = "0.2.0"
