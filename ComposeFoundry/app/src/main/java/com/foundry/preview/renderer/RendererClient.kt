package com.foundry.preview.renderer

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.DeadObjectException
import android.os.IBinder
import android.util.Log
import com.foundry.core.renderer.IRenderCallback
import com.foundry.core.renderer.IRenderService
import com.foundry.core.renderer.RenderRequest
import com.foundry.core.renderer.RenderResult
import com.foundry.core.renderer.RenderStatus
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 渲染进程 IPC 客户端（主进程侧）。
 *
 * ## 用法
 * ```kotlin
 * val client = RendererClient(context)
 * client.bind()
 * val result = client.render(RenderRequest(...))
 * val bitmap = client.loadBitmap(result)
 * client.unbind()
 * ```
 *
 * ## 线程安全
 * [render] 是 suspend 函数，内部通过 [suspendCancellableCoroutine] 等待
 * 渲染进程的异步回调。
 */
class RendererClient(private val context: Context) {

    companion object {
        private const val TAG = "RendererClient"
        /** 单次渲染超时（10 秒），超时后清理连接。 */
        private const val RENDER_TIMEOUT_MS = 10_000L
        /** 绑定 Service 超时。 */
        private const val BIND_TIMEOUT_MS = 3_000L
    }

    private var service: IRenderService? = null
    private var isBound = false

    /** 活跃的渲染请求回调映射：requestId → Continuation */
    private val pendingRequests = ConcurrentHashMap<String, kotlinx.coroutines.CancellableContinuation<RenderResult>>()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IRenderService.asInterface(binder)
            isBound = true
            Log.i(TAG, "Connected to renderer service")
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            isBound = false
            Log.w(TAG, "Renderer service disconnected")
            // 清理所有等待中的请求
            pendingRequests.values.forEach { cont ->
                if (cont.isActive) cont.resumeWithException(DeadObjectException())
            }
            pendingRequests.clear()
        }

        override fun onNullBinding(name: ComponentName) {
            service = null
            isBound = false
            Log.e(TAG, "Null binding from renderer service")
        }
    }

    /**
     * 绑定到独立渲染进程（:renderer）。
     * 必须在非 UI 线程调用；会阻塞等待直到绑定完成或超时。
     *
     * @throws RendererBindException 绑定超时或失败
     */
    suspend fun bind() {
        if (isBound) return
        val intent = Intent(context, RendererService::class.java)
        withTimeout(BIND_TIMEOUT_MS) {
            val bindResult = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bindResult) {
                throw RendererBindException("bindService returned false")
            }
            // 等待 onServiceConnected 回调设置 isBound
            var waited = 0L
            while (!isBound && waited < BIND_TIMEOUT_MS) {
                kotlinx.coroutines.delay(100)
                waited += 100
            }
            if (!isBound) {
                throw RendererBindException("Timed out waiting for service connection after ${waited}ms")
            }
        }
    }

    /** 解绑渲染进程。 */
    fun unbind() {
        if (!isBound) return
        context.unbindService(serviceConnection)
        service = null
        isBound = false
        Log.d(TAG, "Unbound from renderer service")
    }

    /**
     * 异步渲染：发送请求到渲染进程，挂起等待结果。
     *
     * @throws IllegalStateException 未绑定 Service
     */
    @Throws(Exception::class)
    suspend fun render(request: RenderRequest): RenderResult {
        val svc = service ?: throw IllegalStateException("Not bound to renderer service")
        val requestId = request.requestId.ifEmpty { UUID.randomUUID().toString() }
        val req = if (request.requestId != requestId) request.copy(requestId = requestId) else request

        return withTimeout(RENDER_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                pendingRequests[requestId] = cont
                cont.invokeOnCancellation {
                    pendingRequests.remove(requestId)
                }

                val callback = object : IRenderCallback.Stub() {
                    override fun onRenderComplete(result: RenderResult) {
                        val c = pendingRequests.remove(requestId)
                        if (c != null && c.isActive) {
                            c.resume(result)
                        }
                    }
                }

                try {
                    svc.render(req, callback)
                } catch (e: Exception) {
                    pendingRequests.remove(requestId)
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        }
    }

    /**
     * 从 RenderResult 加载 Bitmap。
     * 渲染进程将 Bitmap 写入 PNG 文件，主进程通过文件路径读取。
     *
     * @return 成功加载的 Bitmap，或 null（如果结果失败或文件不存在）
     */
    fun loadBitmap(result: RenderResult): Bitmap? {
        if (result.status == RenderStatus.FAILED) return null
        val path = result.bitmapFdPath ?: return null
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode bitmap from $path: ${e.message}")
            null
        }
    }

    /**
     * 获取渲染进程内存占用（MB），用于诊断。
     * @return 内存 MB 数，或 -1 表示未连接
     */
    fun getRendererMemoryMb(): Int {
        return try {
            service?.getRendererMemoryMb() ?: -1
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * 简化同步渲染（核心 API）：
     * 将源码 payload 写入主进程 filesDir 临时文件，再通过 Binder 把**文件路径**发送到 :renderer 进程。
     *
     * ## Binder 1MB 限制规避（P0）
     * 大 payload（几千行 Compose/XML）直接过 Binder 会抛 TransactionTooLargeException。
     * 这里先落盘，AIDL 只传文件路径；渲染进程读文件后删除临时文件。
     *
     * @param context 用于获取 filesDir（同 App 多进程共享）
     * @param payload 渲染源码（JSON DSL / XML / Compose Kotlin）
     * @param sdkId   本地 SDK ID（对应 foundry-pack.json 的 id 字段）
     * @return PNG 文件绝对路径，失败返回 null
     */
    fun renderToBitmap(context: Context, payload: String, sdkId: String): String? {
        val svc = service ?: return null
        val payloadFilePath = writePayloadFile(context, payload) ?: return null
        return try {
            svc.renderToBitmap(payloadFilePath, sdkId)
        } catch (e: Exception) {
            Log.w(TAG, "renderToBitmap failed: ${e.message}")
            null
        }
    }

    /**
     * 隐形 Activity 截屏渲染（核心 API v2）。
     *
     * 将源码 payload 写入主进程 filesDir 临时文件，通过 Binder 只传**文件路径**到 :renderer 进程，
     * 渲染进程启动透明 RenderCaptureActivity 进行 View 渲染 + 截屏，
     * 返回 PNG 文件绝对路径（全程规避 TransactionTooLargeException）。
     *
     * @param context 用于获取 filesDir（同 App 多进程共享）
     * @param payload 渲染源码
     * @param sdkId   本地 SDK ID
     * @return PNG 文件绝对路径，失败返回 null
     */
    fun renderAndCapture(context: Context, payload: String, sdkId: String): String? {
        val svc = service ?: return null
        val payloadFilePath = writePayloadFile(context, payload) ?: return null
        return try {
            svc.renderAndCapture(payloadFilePath, sdkId)
        } catch (e: Exception) {
            Log.w(TAG, "renderAndCapture failed: ${e.message}")
            null
        }
    }

    /**
     * 将渲染源码写入主进程 filesDir/render_tasks/ 临时文件。
     * 渲染进程读取后自行删除；主进程不负责清理（跨进程共享文件，写方无感知读方）。
     */
    private fun writePayloadFile(context: Context, payload: String): String? {
        return try {
            val dir = File(context.filesDir, "render_tasks").apply { mkdirs() }
            val file = File(dir, "payload_${UUID.randomUUID()}.txt")
            file.writeText(payload, Charsets.UTF_8)
            Log.i(TAG, "Payload written: ${file.absolutePath} (${file.length()} bytes)")
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write payload file: ${e.message}", e)
            null
        }
    }

    /**
     * 从 PNG 文件路径加载 Bitmap。
     * 用于 [renderAndCapture] 返回路径后解码。
     */
    fun loadBitmapFromPath(path: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decode bitmap from $path: ${e.message}")
            null
        }
    }

    /** 是否已绑定到渲染进程。 */
    val connected: Boolean get() = isBound
}

/** 渲染进程绑定失败异常。 */
class RendererBindException(message: String) : Exception(message)
