package com.foundry.preview.state

import android.content.Context
import android.graphics.Bitmap
import com.foundry.preview.plugin.LocalSdkScanner
import com.foundry.preview.renderer.RendererClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 多进程沙箱 IPC 渲染委托 — 从 [FoundryViewModel] 中拆出，遵循单一职责原则。
 *
 * 职责：
 *  - 绑定 / 解绑 :renderer 进程（AIDL 通道）
 *  - IPC 渲染：源码 → AIDL → RenderCaptureActivity → 截屏 PNG → 主进程解码
 *  - 本地 SDK 扫描
 *  - 渲染状态 / 位图 / 诊断信息管理
 *
 * 不持有 [Context] 引用（避免 Activity 泄漏），每次方法调用显式传入。
 */
class IpcRenderDelegate(
    private val scope: CoroutineScope,
    private val onStatus: (String) -> Unit
) {
    private var rendererClient: RendererClient? = null
    private var rendererRequestCounter = 0L

    /** 渲染进程返回的位图。 */
    private val _rendererBitmap = MutableStateFlow<Bitmap?>(null)
    val rendererBitmap: StateFlow<Bitmap?> = _rendererBitmap

    /** 最近一次 IPC 渲染诊断信息。 */
    private val _rendererDiagnostics = MutableStateFlow("")
    val rendererDiagnostics: StateFlow<String> = _rendererDiagnostics

    /** IPC 渲染是否正在进行中。 */
    private val _isRendererBusy = MutableStateFlow(false)
    val isRendererBusy: StateFlow<Boolean> = _isRendererBusy

    /** 渲染进程内存占用（MB），-1 表示未连接。 */
    private val _rendererMemoryMb = MutableStateFlow(-1)
    val rendererMemoryMb: StateFlow<Int> = _rendererMemoryMb

    val connected: Boolean get() = rendererClient?.connected == true

    /**
     * 绑定渲染进程（:renderer），建立 AIDL 通道。
     * 应在 Activity onStart 或用户首次触发渲染前调用。
     */
    fun bindRenderer(context: Context) {
        if (rendererClient?.connected == true) return
        scope.launch(Dispatchers.IO) {
            try {
                val client = RendererClient(context)
                client.bind()
                rendererClient = client
                _rendererMemoryMb.value = client.getRendererMemoryMb()
                onStatus("已连接渲染进程 (:renderer)")
            } catch (e: Exception) {
                onStatus("渲染进程连接失败: ${e.message}")
            }
        }
    }

    /** 解绑渲染进程，释放 :renderer 进程资源。 */
    fun unbindRenderer() {
        rendererClient?.unbind()
        rendererClient = null
        _rendererMemoryMb.value = -1
    }

    /**
     * IPC 渲染（简化路径）：将当前源码通过 `renderAndCapture` 直接发送到 :renderer 进程，
     * 渲染进程启动透明 RenderCaptureActivity 执行 View 渲染 + 截屏（规避 Service 无 Window 限制），
     * 返回 PNG 文件路径 → 主进程解码 Bitmap。
     *
     * ## 架构
     * Service(无 Window) → startActivity(RenderCaptureActivity) → DexClassLoader 加载 SDK
     * → 反射创建 View → attach Window → drawToBitmap → PNG 文件 → latch.countDown()
     * → Service 拿到 outputPath → Binder 返回 String(文件路径，非 byte[]) → 主进程 decodeFile
     *
     * 如果无 SDK 可用（空壳 IPC），渲染进程会返回诊断占位 Bitmap，证明链路通畅。
     */
    fun renderViaIpc(context: Context, source: String) {
        if (source.isBlank()) {
            onStatus("No source code to render")
            return
        }

        scope.launch(Dispatchers.IO) {
            _isRendererBusy.value = true
            _rendererDiagnostics.value = "正在连接渲染进程..."

            // 确保已绑定
            if (rendererClient?.connected != true) {
                try {
                    bindRenderer(context)
                    delay(500)
                } catch (_: Exception) { }
            }

            val client = rendererClient
            if (client?.connected != true) {
                _isRendererBusy.value = false
                _rendererDiagnostics.value = "渲染进程未连接"
                onStatus("渲染进程未连接，请重试")
                return@launch
            }

            try {
                // 自动选择 SDK：优先扫描本地 foundry-pack.json
                val availableSdks = getAvailableSdkIds(context)
                val sdkId = availableSdks.firstOrNull() ?: "no-sdk"
                val startMs = System.currentTimeMillis()

                _rendererDiagnostics.value = "已发送渲染请求 (sdkId=$sdkId, ${availableSdks.size} SDK available)"
                val pngPath = client.renderAndCapture(context, source, sdkId)
                val elapsed = System.currentTimeMillis() - startMs

                if (pngPath != null) {
                    val bmp = client.loadBitmapFromPath(pngPath)
                    _rendererBitmap.value = bmp
                    _rendererDiagnostics.value = buildString {
                        append("渲染完成 (${elapsed}ms")
                        val fileSize = runCatching { java.io.File(pngPath).length() }.getOrDefault(0L)
                        if (fileSize > 0) append(", ${fileSize / 1024}KB")
                        append(", 内存: ${client.getRendererMemoryMb()}MB)")
                    }
                    onStatus("沙箱渲染完成 (${elapsed}ms)")
                } else {
                    _rendererBitmap.value = null
                    _rendererDiagnostics.value = "渲染失败/超时 (${elapsed}ms)"
                    onStatus("渲染进程返回 null（可能超时或SDK加载失败）")
                }
            } catch (e: Exception) {
                _rendererBitmap.value = null
                _rendererDiagnostics.value = "IPC 渲染异常: ${e.message}"
                onStatus("渲染进程异常: ${e.message}")
            } finally {
                _isRendererBusy.value = false
                _rendererMemoryMb.value = client.getRendererMemoryMb()
            }
        }
    }

    /** 清除 IPC 渲染缓存（切换 tab 时调用）。 */
    fun clearRendererState() {
        _rendererBitmap.value = null
        _rendererDiagnostics.value = ""
    }

    /** 获取本地可用的 SDK ID 列表（从 foundry-pack.json 扫描结果）。 */
    private fun getAvailableSdkIds(context: Context): List<String> {
        return try {
            LocalSdkScanner.scan(LocalSdkScanner.defaultScanDirs(context)).map { it.id }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
