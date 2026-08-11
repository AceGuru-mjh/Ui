package com.foundry.core.renderer

import android.graphics.Bitmap

/**
 * SDK 渲染插件契约：本地离线 SDK 库实现此接口，由渲染进程通过
 * [PathClassLoader] 加载并调用，产出 [Bitmap]。
 *
 * 每个 SDK JAR（如 layoutlib、compose-runtime）提供一个入口类，
 * 实现此接口后将 JAR 放入 FoundrySDK/ 目录即可被 [LocalSdkScanner]
 * 发现并注册。
 *
 * 生命周期由渲染进程管理；每次 [render] 调用是独立、无状态的。
 */
interface BitmapRenderer {

    /**
     * 将源码渲染为位图。
     *
     * @param sourceCode 源码内容（JSON DSL / XML / Compose Kotlin）
     * @param format     源码格式标识（json / xml / compose-kotlin）
     * @param widthPx    目标宽度（像素，已根据设备密度换算）
     * @param heightPx   目标高度（像素）
     * @param darkTheme  是否暗色主题
     * @return 渲染后的位图，失败返回 null
     */
    fun render(
        sourceCode: String,
        format: String,
        widthPx: Int,
        heightPx: Int,
        darkTheme: Boolean = false
    ): Bitmap?

    /** 返回渲染器名称（用于诊断元数据）。 */
    val rendererName: String

    /** 返回支持的格式列表。 */
    val supportedFormats: Set<String>
}
