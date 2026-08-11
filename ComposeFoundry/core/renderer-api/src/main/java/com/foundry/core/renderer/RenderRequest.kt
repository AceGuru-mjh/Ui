package com.foundry.core.renderer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 跨进程渲染请求（主进程 → 渲染进程）。
 * 包含源码、库路径列表、目标设备配置。
 */
@Parcelize
data class RenderRequest(
    /** 唯一请求 ID，用于回调匹配。 */
    val requestId: String,
    /** 需预览的源码内容（JSON DSL / XML / Compose Kotlin）。 */
    val sourceCode: String,
    /** 源码格式标识（json / xml / compose-kotlin），供渲染进程选择解析器。 */
    val format: String,
    /** 渲染所需库的本地路径列表（JAR / DEX / 目录）。 */
    val libraryPaths: List<String>,
    /** 目标设备宽度 (dp)。 */
    val targetWidthDp: Int = 412,
    /** 目标设备高度 (dp)。 */
    val targetHeightDp: Int = 915,
    /** 是否暗色主题。 */
    val darkTheme: Boolean = false,
    /** 预览级别（结构 / 线框 / 静态视觉 / 交互）。 */
    val previewLevel: String = "L3_STATIC_VISUAL"
) : Parcelable
