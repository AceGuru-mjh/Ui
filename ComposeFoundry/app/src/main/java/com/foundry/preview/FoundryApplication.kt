package com.foundry.preview

import android.app.Application
import com.foundry.preview.engine.RendererManager

/**
 * 应用入口：在进程启动时完成一次性初始化（组件渲染器 + 格式插件注册），
 * 避免在 Activity 重建时重复注册。
 */
class FoundryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RendererManager.initialize()
    }
}
