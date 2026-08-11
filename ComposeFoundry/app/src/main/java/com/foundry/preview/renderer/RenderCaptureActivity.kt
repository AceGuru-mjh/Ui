package com.foundry.preview.renderer

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.platform.ComposeView
import com.foundry.core.renderer.IRenderEngine
import com.foundry.preview.plugin.LocalSdkManifest
import com.foundry.preview.plugin.LocalSdkScanner
import dalvik.system.DexClassLoader
import java.io.File
import java.io.FileOutputStream

/**
 * 隐形截屏 Activity（运行在 :renderer 进程）。
 *
 * ## 生命周期
 * 1. [onCreate] 从 Intent 中拿到 taskId
 * 2. 通过 [RenderTaskManager.getTask] 获取 payload + sdkId
 * 3. 加载 SDK → 反射创建 View/Compose → attach 到 Window → measure/layout → drawToBitmap
 * 4. PNG 写入 filesDir → [RenderTaskManager.completeTask]
 * 5. [finish] 自毁
 *
 * ## 设计决策
 * - **完全透明**：用户看不到 Activity 的任何 UI
 * - **自毁式**：截屏完成立即 finish，不占用后台栈
 * - **无动画**：Theme.Transparent.NoAnimation 禁用了所有转场动画
 * - **降级链**：引擎 View → 降级占位 View → 保底 Bitmap
 */
class RenderCaptureActivity : Activity() {

    companion object {
        private const val TAG = "RenderCaptureActivity"
        const val EXTRA_TASK_ID = "foundry.task_id"
        private const val CAPTURE_WIDTH = 600
        private const val CAPTURE_HEIGHT = 400
        /** ComposeView composition 等待延迟 (ms)。 */
        private const val COMPOSE_WAIT_MS = 150L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val taskId = intent?.getStringExtra(EXTRA_TASK_ID)
        if (taskId == null) {
            Log.e(TAG, "No taskId in intent, finishing immediately")
            finish()
            return
        }

        val task = RenderTaskManager.getTask(taskId)
        if (task == null) {
            Log.e(TAG, "Task not found: $taskId")
            RenderTaskManager.failTask(taskId, "Task not found in manager")
            finish()
            return
        }

        Log.i(TAG, "Capture started: taskId=$taskId, sdkId=${task.sdkId}, payloadLen=${task.payload.length}")

        try {
            val view = renderView(task.payload, task.sdkId)
            if (view == null) {
                RenderTaskManager.failTask(taskId, "renderView returned null")
                finish()
                return
            }

            val outputFile = File(filesDir, "capture_$taskId.png")
            val container = buildCaptureContainer(view)

            // 将容器添加到 Activity 的 content root
            val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(container)

            // 检测是否包含 ComposeView：Compose 需要异步 composition
            val hasCompose = findComposeView(view)

            if (hasCompose) {
                // ComposeView 路径：延迟等待 composition 完成后再截图
                Log.i(TAG, "ComposeView detected, deferring capture for composition")
                mainHandler.postDelayed({
                    captureAndFinish(container, outputFile, taskId)
                }, COMPOSE_WAIT_MS)
            } else {
                // 普通 View 路径：立即 measure/layout/draw
                captureAndFinish(container, outputFile, taskId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed: ${e.message}", e)
            RenderTaskManager.failTask(taskId, "Exception: ${e.message}")
            finish()
        }
    }

    /**
     * 构建截图容器 FrameLayout，将渲染 View 放入其中。
     */
    private fun buildCaptureContainer(view: View): FrameLayout {
        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(CAPTURE_WIDTH, CAPTURE_HEIGHT)
            addView(view, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
    }

    /**
     * 递归检测 View 树中是否包含 ComposeView。
     */
    private fun findComposeView(view: View): Boolean {
        if (view is ComposeView) return true
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                if (findComposeView(view.getChildAt(i))) return true
            }
        }
        return false
    }

    /**
     * 截图并完成：measure → layout → draw → PNG → completeTask → finish。
     */
    private fun captureAndFinish(container: FrameLayout, outputFile: File, taskId: String) {
        try {
            val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
            val bitmap = containerToBitmap(container)
            rootView.removeView(container)

            if (bitmap != null) {
                FileOutputStream(outputFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
                bitmap.recycle()
                RenderTaskManager.completeTask(taskId, outputFile.absolutePath)
                Log.i(TAG, "Capture success: $outputFile (${outputFile.length()} bytes)")
            } else {
                RenderTaskManager.failTask(taskId, "containerToBitmap returned null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Capture failed in post: ${e.message}", e)
            RenderTaskManager.failTask(taskId, "Exception: ${e.message}")
        } finally {
            finish()
        }
    }

    /**
     * 对已添加到 Window 的容器执行 measure/layout/draw → Bitmap。
     */
    private fun containerToBitmap(container: ViewGroup): Bitmap? {
        return try {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(CAPTURE_WIDTH, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(CAPTURE_HEIGHT, View.MeasureSpec.EXACTLY)
            container.measure(widthSpec, heightSpec)
            container.layout(0, 0, CAPTURE_WIDTH, CAPTURE_HEIGHT)
            val bitmap = Bitmap.createBitmap(CAPTURE_WIDTH, CAPTURE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            container.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "containerToBitmap failed: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  核心渲染（仅返回 View，截图由 onCreate 异步完成）
    // ═══════════════════════════════════════════════════════════

    /**
     * 加载 SDK → 创建 View（不包含截图步骤）。
     *
     * 1. 扫描 /FoundrySDK/ 按 sdkId 找 foundry-pack.json
     * 2. DexClassLoader 加载 → IRenderEngine / 反射链 → View
     * 3. 降级：返回诊断占位 View
     *
     * [onCreate] 负责将 View attach 到 Window 后执行异步截图。
     */
    private fun renderView(payload: String, sdkId: String): View? {
        val manifests = LocalSdkScanner.scan(LocalSdkScanner.defaultScanDirs())
        val manifest = manifests.firstOrNull { it.id == sdkId }

        if (manifest != null) {
            val view = tryCreateViewFromSdk(manifest, payload)
            if (view != null) return view
        }

        return createDiagnosticView(
            label = if (manifest != null) "SDK Render Fallback: ${manifest.id}" else "No SDK: $sdkId",
            details = manifests.map { "${it.id} v${it.version}" }.ifEmpty {
                listOf("No SDKs found in /FoundrySDK/")
            }
        )
    }

    // ═══════════════════════════════════════════════════════════
    //  SDK 加载 → View 反射创建
    // ═══════════════════════════════════════════════════════════

    /**
     * 通过 DexClassLoader 加载 SDK，创建 View。
     *
     * ## 加载优先级（Golden Path 优先）
     * **0) IRenderEngine 接口转换** ── 编译时共享契约，类型安全，零反射开销
     *    `engine as IRenderEngine` → `engine.render(payload, context)` → View
     *
     * 降级链（旧 SDK 兼容）：
     * 1) engine.createView(payload, context) → View
     * 2) engine.render(payload, context) → Bitmap → ImageView
     * 3) engine.renderToBytes(payload) → ByteArray → BitmapFactory → ImageView
     *
     * ## ClassLoader 委托模型
     * ```
     * DexClassLoader (插件 JAR)
     *   └─ parent = Activity.classLoader (渲染进程 CL)
     *        ├─ app 模块的所有类（含 core:renderer-api → IRenderEngine）
     *        └─ Android SDK 基础类
     * ```
     * 因此 `engine as IRenderEngine` 可以成功，因为父 CL 提供了接口定义。
     */
    private fun tryCreateViewFromSdk(manifest: LocalSdkManifest, payload: String): View? {
        return try {
            val packRoot = findPackRoot(manifest.id)
            val absPaths = manifest.absoluteLibraryPaths(packRoot)
            val existingPaths = absPaths.filter { File(it).exists() }
            if (existingPaths.isEmpty()) {
                Log.w(TAG, "No library files exist for ${manifest.id}")
                return null
            }

            val dexPath = existingPaths.joinToString(File.pathSeparator)
            val loader = DexClassLoader(dexPath, codeCacheDir.absolutePath, null, this.classLoader)
            val engineClass = loader.loadClass(manifest.entryClass)
            val engine = engineClass.getDeclaredConstructor().newInstance()

            // ═══ Golden Path: IRenderEngine 接口转换 ═══
            // 如果插件实现了 IRenderEngine，直接用共享接口调用，无反射开销
            if (engine is IRenderEngine) {
                Log.i(TAG, "Golden Path: ${manifest.entryClass} implements IRenderEngine")
                return engine.render(payload, this)
            }

            // ═══ 降级链 1: createView(payload, context) → View ═══
            try {
                val method = engineClass.getMethod("createView", String::class.java, android.content.Context::class.java)
                val view = method.invoke(engine, payload, this) as? View
                if (view != null) return view
            } catch (_: NoSuchMethodException) { }

            // ═══ 降级链 2: render(payload, context) → Bitmap ═══
            try {
                val method = engineClass.getMethod("render", String::class.java, android.content.Context::class.java)
                val bitmap = method.invoke(engine, payload, this) as? Bitmap
                if (bitmap != null) {
                    val iv = android.widget.ImageView(this)
                    iv.setImageBitmap(bitmap)
                    return iv
                }
            } catch (_: NoSuchMethodException) { }

            // ═══ 降级链 3: renderToBytes(payload) → ByteArray ═══
            try {
                val method = engineClass.getMethod("renderToBytes", String::class.java)
                val bytes = method.invoke(engine, payload) as? ByteArray
                if (bytes != null) {
                    val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        val iv = android.widget.ImageView(this)
                        iv.setImageBitmap(bmp)
                        return iv
                    }
                }
            } catch (_: NoSuchMethodException) { }

            Log.w(TAG, "Entry class loaded but no known render method: ${manifest.entryClass}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create view from SDK ${manifest.id}: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  诊断占位 View（降级输出）
    // ═══════════════════════════════════════════════════════════

    private fun createDiagnosticView(label: String, details: List<String>): View {
        val context = this
        val rootLayout = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#1A1A2E"))
            layoutParams = FrameLayout.LayoutParams(CAPTURE_WIDTH, CAPTURE_HEIGHT)
        }

        val textLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24, 48, 24, 24)
        }

        // 标题
        textLayout.addView(TextView(context).apply {
            text = "Renderer Sandbox"
            setTextColor(Color.parseColor("#00D2FF"))
            textSize = 28f
            gravity = Gravity.CENTER_HORIZONTAL
        })

        // 标签
        textLayout.addView(TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(0, 16, 0, 0)
        })

        // PID / 进程信息
        val pid = Process.myPid()
        textLayout.addView(TextView(context).apply {
            text = "PID: $pid  |  Process: :renderer"
            setTextColor(Color.GRAY)
            textSize = 14f
            setPadding(0, 16, 0, 0)
        })

        // SDK 列表
        if (details.isNotEmpty()) {
            textLayout.addView(TextView(context).apply {
                text = "Available SDKs:"
                setTextColor(Color.parseColor("#E94560"))
                textSize = 16f
                setPadding(0, 24, 0, 0)
            })
            for (item in details.take(8)) {
                textLayout.addView(TextView(context).apply {
                    text = "  $item"
                    setTextColor(Color.LTGRAY)
                    textSize = 13f
                    setPadding(0, 4, 0, 0)
                })
            }
        }

        // IPC 状态
        textLayout.addView(TextView(context).apply {
            text = "IPC Pipeline: CONNECTED  |  Capture Mode: Activity"
            setTextColor(Color.parseColor("#00FF88"))
            textSize = 14f
            setPadding(0, 24, 0, 0)
            gravity = Gravity.CENTER_HORIZONTAL
        })

        rootLayout.addView(textLayout, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        return rootLayout
    }

    // ═══════════════════════════════════════════════════════════
    //  辅助方法
    // ═══════════════════════════════════════════════════════════

    private fun findPackRoot(sdkId: String): File {
        for (dir in LocalSdkScanner.defaultScanDirs()) {
            val packDir = File(dir, sdkId)
            if (packDir.exists() && packDir.isDirectory) return packDir
        }
        val fallback = File(LocalSdkScanner.defaultScanDirs().first(), sdkId)
        fallback.mkdirs()
        return fallback
    }
}
