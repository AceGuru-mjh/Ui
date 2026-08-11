package com.foundry.core.renderer

import android.content.Context
import android.view.View

/**
 * 所有离线渲染引擎必须实现的通用接口。
 *
 * ## 核心原则：接口共享与依赖隔离 (The Golden Rule)
 *
 * **主程序和插件共享契约接口，但插件绝不能自带接口的实现类。**
 *
 * - 主程序（app + :renderer 进程）通过 `core:renderer-api` 模块持有此接口
 * - 插件在编译时通过 `compileOnly` 依赖此模块，运行时由父 ClassLoader 提供
 * - 这样可以实现 `engine as IRenderEngine` 的类型安全转换，避免反射调用
 *
 * ## 设计约束
 * - 此接口必须保持极其轻量，且不能频繁修改
 * - 只暴露一个 `render` 方法，所有复杂逻辑由插件内部封装
 * - Context 参数实际上是 RenderCaptureActivity（或 RendererService），插件可据此获取资源
 *
 * ## 为什么放在 core:renderer-api 而非 core:ui-plugin-sdk
 * core:ui-plugin-sdk 是纯 JVM 模块（java-library），不依赖 Android SDK，
 * 无法引用 android.view.View / android.content.Context。
 * core:renderer-api 是 Android Library，已内置 Android 依赖，是放置此接口的正确位置。
 */
interface IRenderEngine {
    /**
     * 在沙箱进程中渲染 payload 为 Android View。
     *
     * 调用方（RenderCaptureActivity / RendererService）会负责：
     * 1. 将 View attach 到 Window（如果是 Activity）
     * 2. 执行 measure/layout/draw
     * 3. drawToBitmap 截图
     * 4. 保存 PNG 文件并返回路径
     *
     * @param payload 要渲染的数据（JSON / XML / Kotlin 源码等），由插件自行解析
     * @param context 渲染进程的 Context（RenderCaptureActivity 或 RendererService）
     * @return 渲染完成的 Android View，沙箱会将其截图
     */
    fun render(payload: String, context: Context): View
}
