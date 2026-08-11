package com.foundry.core.renderer

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException

/**
 * 渲染回调 Binder 接口（替代 AIDL，纯 Kotlin 实现）。
 *
 * 渲染进程通过此回调异步推回 [RenderResult] 到主进程。
 */
abstract class IRenderCallback : Binder() {

    companion object {
        const val DESCRIPTOR = "com.foundry.core.renderer.IRenderCallback"
        private const val TRANSACTION_onRenderComplete = IBinder.FIRST_CALL_TRANSACTION + 0
    }

    /** 渲染完成回传结果 */
    @Throws(RemoteException::class)
    abstract fun onRenderComplete(result: RenderResult)

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_onRenderComplete -> {
                data.enforceInterface(DESCRIPTOR)
                val result: RenderResult? = data.readParcelable(RenderResult::class.java.classLoader)
                if (result != null) onRenderComplete(result)
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    /**
     * 客户端 Stub：渲染进程中创建实例传给 [IRenderService.render]，实现被远端调用的逻辑。
     * 子类只需实现 [onRenderComplete]。
     */
    abstract class Stub : IRenderCallback() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            return super.onTransact(code, data, reply, flags)
        }

        companion object {
            fun asInterface(obj: IBinder?): IRenderCallback? {
                if (obj == null) return null
                val existing = obj.queryLocalInterface(DESCRIPTOR)
                if (existing is IRenderCallback) return existing
                return Proxy(obj)
            }
        }
    }

    /** 客户端 Proxy（运行在主进程侧，用于接收渲染进程推送）。 */
    private class Proxy(private val remote: IBinder) : IRenderCallback() {
        override fun onRenderComplete(result: RenderResult) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeParcelable(result, 0)
                remote.transact(TRANSACTION_onRenderComplete, data, null, IBinder.FLAG_ONEWAY)
            } finally {
                data.recycle()
            }
        }
    }
}
