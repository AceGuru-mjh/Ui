package com.foundry.core.renderer

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException

/**
 * 渲染服务 Binder 接口（替代 AIDL，纯 Kotlin 实现）。
 *
 * 主进程通过 bindService 获取 [IRenderService] proxy，
 * 跨进程发送 [RenderRequest] 到 :renderer 进程执行沙箱渲染。
 */
abstract class IRenderService : Binder() {

    companion object {
        const val DESCRIPTOR = "com.foundry.core.renderer.IRenderService"
        private const val TRANSACTION_render = IBinder.FIRST_CALL_TRANSACTION + 0
        private const val TRANSACTION_renderSync = IBinder.FIRST_CALL_TRANSACTION + 1
        private const val TRANSACTION_getRendererMemoryMb = IBinder.FIRST_CALL_TRANSACTION + 2
        private const val TRANSACTION_renderToBitmap = IBinder.FIRST_CALL_TRANSACTION + 3
        private const val TRANSACTION_renderAndCapture = IBinder.FIRST_CALL_TRANSACTION + 4

        /** 从 Binder 代理创建客户端 Proxy。 */
        fun asInterface(obj: IBinder?): IRenderService? {
            if (obj == null) return null
            val existing = obj.queryLocalInterface(DESCRIPTOR)
            if (existing is IRenderService) return existing
            return Proxy(obj)
        }
    }

    /** 异步渲染 */
    @Throws(RemoteException::class)
    abstract fun render(request: RenderRequest, callback: IRenderCallback)

    /** 同步渲染 */
    @Throws(RemoteException::class)
    abstract fun renderSync(request: RenderRequest): RenderResult

    /** 获取渲染进程内存 */
    @Throws(RemoteException::class)
    abstract fun getRendererMemoryMb(): Int

    /**
     * 同步渲染为字节数组（简化 IPC 路径，用于"空壳 IPC"验证）。
     * @param payload 预览代码（JSON DSL / XML / Compose 源码）
     * @param sdkId  本地离线 SDK ID（对应 foundry-pack.json 中的 id 字段）
     * @return PNG 字节数组，失败返回 null
     */
    @Throws(RemoteException::class)
    abstract fun renderToBitmap(payload: String, sdkId: String): ByteArray?

    /**
     * 隐形 Activity 截屏渲染（规避 Service 无 Window 的限制）。
     *
     * Service 收到请求后启动 [RenderCaptureActivity]，透明 Activity 加载 SDK
     * 渲染 View → drawToBitmap → 写入 PNG 文件 → CountDownLatch 同步返回文件路径。
     *
     * ## 设计决策
     * - 返回文件路径 String 而非 byte[]/Bitmap，避免 TransactionTooLargeException（Binder 缓冲区仅 1MB）
     * - 主进程通过文件路径解码 Bitmap，渲染进程的文件目录对主进程不可见，但 Bitmap 已通过 Binder 路径共享
     * - 超时 30 秒后返回 null
     *
     * @param payload 渲染源码
     * @param sdkId   本地 SDK ID
     * @return PNG 文件绝对路径，失败或超时返回 null
     */
    @Throws(RemoteException::class)
    abstract fun renderAndCapture(payload: String, sdkId: String): String?

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        val descriptor = DESCRIPTOR
        return when (code) {
            TRANSACTION_render -> {
                data.enforceInterface(descriptor)
                val request: RenderRequest? = data.readParcelable(RenderRequest::class.java.classLoader)
                val callback = IRenderCallback.Stub.asInterface(data.readStrongBinder())!!
                if (request != null) render(request, callback)
                true
            }
            TRANSACTION_renderSync -> {
                data.enforceInterface(descriptor)
                val request: RenderRequest? = data.readParcelable(RenderRequest::class.java.classLoader)
                if (request == null) return false
                val result = renderSync(request)
                reply?.writeNoException()
                reply?.writeParcelable(result, 0)
                true
            }
            TRANSACTION_getRendererMemoryMb -> {
                data.enforceInterface(descriptor)
                val mem = getRendererMemoryMb()
                reply?.writeNoException()
                reply?.writeInt(mem)
                true
            }
            TRANSACTION_renderToBitmap -> {
                data.enforceInterface(descriptor)
                val payload = data.readString() ?: ""
                val sdkId = data.readString() ?: ""
                val bytes = renderToBitmap(payload, sdkId)
                reply?.writeNoException()
                if (bytes != null) {
                    reply?.writeInt(bytes.size)
                    reply?.writeByteArray(bytes)
                } else {
                    reply?.writeInt(-1)
                }
                true
            }
            TRANSACTION_renderAndCapture -> {
                data.enforceInterface(descriptor)
                val payload = data.readString() ?: ""
                val sdkId = data.readString() ?: ""
                val path = renderAndCapture(payload, sdkId)
                reply?.writeNoException()
                reply?.writeString(path)
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    /** 客户端 Proxy（运行在主进程）。 */
    private class Proxy(private val remote: IBinder) : IRenderService() {
        override fun render(request: RenderRequest, callback: IRenderCallback) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeParcelable(request, 0)
                data.writeStrongBinder(callback)
                remote.transact(TRANSACTION_render, data, null, IBinder.FLAG_ONEWAY)
            } finally {
                data.recycle()
            }
        }

        override fun renderSync(request: RenderRequest): RenderResult {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeParcelable(request, 0)
                remote.transact(TRANSACTION_renderSync, data, reply, 0)
                reply.readException()
                return reply.readParcelable(RenderResult::class.java.classLoader)!!
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun getRendererMemoryMb(): Int {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TRANSACTION_getRendererMemoryMb, data, reply, 0)
                reply.readException()
                return reply.readInt()
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun renderToBitmap(payload: String, sdkId: String): ByteArray? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(payload)
                data.writeString(sdkId)
                remote.transact(TRANSACTION_renderToBitmap, data, reply, 0)
                reply.readException()
                val size = reply.readInt()
                if (size < 0) return null
                val bytes = ByteArray(size)
                reply.readByteArray(bytes)
                return bytes
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun renderAndCapture(payload: String, sdkId: String): String? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeString(payload)
                data.writeString(sdkId)
                remote.transact(TRANSACTION_renderAndCapture, data, reply, 0)
                reply.readException()
                return reply.readString()
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}
