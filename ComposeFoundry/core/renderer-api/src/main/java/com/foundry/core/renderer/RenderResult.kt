package com.foundry.core.renderer

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 渲染结果状态枚举。
 */
enum class RenderStatus {
    /** 渲染成功，bitmap 可用。 */
    SUCCESS,
    /** 部分成功（渲染了部分内容，有 warning）。 */
    PARTIAL,
    /** 渲染失败，errorMessage 包含原因。 */
    FAILED
}

/**
 * 跨进程渲染结果（渲染进程 → 主进程）。
 * 包含渲染后的 Bitmap 文件描述符、状态、诊断信息。
 */
@Parcelize
data class RenderResult(
    val requestId: String,
    val status: RenderStatus,
    /** 渲染位图的共享内存文件描述符路径（ParcelFileDescriptor）。渲染进程写入 Bitmap → PNG 到内存文件。 */
    val bitmapFdPath: String? = null,
    /** 错误信息（FAILED 时非空）。 */
    val errorMessage: String? = null,
    /** 渲染耗时（毫秒）。 */
    val renderTimeMs: Long = 0L,
    /** 诊断信息数量。 */
    val diagnosticCount: Int = 0,
    /** 序列化后的 UiGraph JSON（可选，供主进程进一步处理）。 */
    val uiGraphJson: String? = null
) : Parcelable
