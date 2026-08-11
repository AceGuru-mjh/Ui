package com.foundry.preview.renderer

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.foundry.core.renderer.IRenderCallback
import com.foundry.core.renderer.IRenderEngine
import com.foundry.core.renderer.IRenderService
import com.foundry.core.renderer.RenderRequest
import com.foundry.core.renderer.RenderResult
import com.foundry.core.renderer.RenderStatus
import com.foundry.preview.plugin.LocalSdkManifest
import com.foundry.preview.plugin.LocalSdkScanner
import dalvik.system.DexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * 独立渲染沙箱进程（android:process=":renderer"）。
 *
 * ## 核心职责
 * 1. 接收主进程的 [IRenderService] 调用
 * 2. 从 [LocalSdkScanner] 获取本地 SDK 清单（foundry-pack.json）
 * 3. 通过缓存的 [DexClassLoader] 隔离加载 10GB 级本地库
 * 4. 反射调用 SDK 入口类执行渲染
 * 5. 将 Bitmap 压缩为 PNG byte[] 返回主进程
 *
 * ## ClassLoader 隔离策略
 * - 每个 sdkId → 独立 DexClassLoader（避免不同 SDK 间的类冲突）
 * - parent = 渲染进程 ClassLoader（共享 Android 基础类）
 * - dexPath = 该 SDK 所有 JAR/DEX 的路径（File.pathSeparator 拼接）
 * - 缓存复用：避免重复加载 10GB 库
 */
class RendererService : Service() {

    companion object {
        private const val TAG = "RendererService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── ClassLoader 缓存：sdkId → DexClassLoader ──
    private val classLoaderCache = mutableMapOf<String, ClassLoader>()
    // ── SDK 清单缓存：sdkId → LocalSdkManifest ──
    private var cachedManifests: List<LocalSdkManifest> = emptyList()
    private var manifestsLoaded = false

    private val binder = object : IRenderService() {

        // ── 异步渲染（原有接口，保持兼容） ──
        override fun render(request: RenderRequest, callback: IRenderCallback) {
            scope.launch {
                val startMs = System.currentTimeMillis()
                val result: RenderResult = try {
                    renderInternal(request, startMs)
                } catch (e: Exception) {
                    Log.e(TAG, "Render failed for ${request.requestId}", e)
                    RenderResult(
                        requestId = request.requestId,
                        status = RenderStatus.FAILED,
                        errorMessage = e.message ?: "Unknown",
                        renderTimeMs = System.currentTimeMillis() - startMs
                    )
                }
                try { callback.onRenderComplete(result) }
                catch (re: RemoteException) { Log.w(TAG, "Callback failed", re) }
            }
        }

        override fun renderSync(request: RenderRequest): RenderResult {
            val startMs = System.currentTimeMillis()
            return try { renderInternal(request, startMs) }
            catch (e: Exception) {
                RenderResult(request.requestId, status = RenderStatus.FAILED,
                    errorMessage = e.message, renderTimeMs = System.currentTimeMillis() - startMs)
            }
        }

        override fun getRendererMemoryMb(): Int {
            val rt = Runtime.getRuntime()
            return ((rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)).toInt()
        }

        // ── 简化同步渲染（用户需求核心 API） ──
        override fun renderToBitmap(payload: String, sdkId: String): ByteArray? {
            val startMs = System.currentTimeMillis()
            return try {
                val bytes = renderToBitmapInternal(payload, sdkId)
                Log.i(TAG, "renderToBitmap sdkId=$sdkId done in ${System.currentTimeMillis() - startMs}ms " +
                    "(size=${bytes?.size ?: 0})")
                bytes
            } catch (e: Exception) {
                Log.e(TAG, "renderToBitmap sdkId=$sdkId failed: ${e.message}", e)
                null
            }
        }

        // ── 隐形 Activity 截屏渲染（新核心 API） ──
        override fun renderAndCapture(payload: String, sdkId: String): String? {
            val startMs = System.currentTimeMillis()
            return try {
                val taskId = RenderTaskManager.submitTask(payload, sdkId)
                Log.i(TAG, "renderAndCapture: launching RenderCaptureActivity for $taskId")

                val intent = Intent(this@RendererService, RenderCaptureActivity::class.java).apply {
                    putExtra(RenderCaptureActivity.EXTRA_TASK_ID, taskId)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)

                val outputPath = RenderTaskManager.awaitResult(taskId)
                val elapsed = System.currentTimeMillis() - startMs
                if (outputPath != null) {
                    Log.i(TAG, "renderAndCapture done: $outputPath (${elapsed}ms)")
                } else {
                    Log.w(TAG, "renderAndCapture timed out or failed after ${elapsed}ms")
                }
                outputPath
            } catch (e: Exception) {
                Log.e(TAG, "renderAndCapture failed: ${e.message}", e)
                null
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        scope.coroutineContext[Job]?.cancel()
        classLoaderCache.clear()
        cachedManifests = emptyList()
        manifestsLoaded = false
        RenderTaskManager.clearAll()
        super.onDestroy()
    }

    // ═══════════════════════════════════════════════════════════
    //  核心渲染 API（Task 3）
    // ═══════════════════════════════════════════════════════════

    /**
     * [renderToBitmap] 的内部实现：
     * 1. 加载 SDK 清单
     * 2. 获取或创建隔离 ClassLoader
     * 3. 反射实例化渲染引擎
     * 4. 调用渲染 → 压缩为 PNG byte[]
     */
    private fun renderToBitmapInternal(payload: String, sdkId: String): ByteArray? {
        // Step 1: 加载 SDK 清单（带缓存）
        ensureManifestsLoaded()

        // Step 2: 按 sdkId 查找 SDK 清单
        val manifest = cachedManifests.firstOrNull { it.id == sdkId }
        if (manifest == null) {
            Log.w(TAG, "SDK '$sdkId' not found. Available: ${cachedManifests.map { it.id }}")
            // 空壳 IPC：无 SDK 时返回诊断占位位图，证明 IPC 链路通畅
            return generateDiagnosticBitmap(
                label = "SDK Not Found: $sdkId",
                available = cachedManifests.map { "${it.id} v${it.version}" }
            )
        }

        // Step 3: 获取或创建隔离的 DexClassLoader
        val loader = getOrCreateClassLoader(manifest)

        // Step 4: 反射实例化渲染引擎
        return try {
            val engineClass = loader.loadClass(manifest.entryClass)
            val engine = engineClass.getDeclaredConstructor().newInstance()

            // ═══ Golden Path: IRenderEngine 接口转换 ═══
            // 类型安全，零反射开销，返回 View 后手动 measure/layout/draw
            if (engine is IRenderEngine) {
                Log.i(TAG, "Golden Path: ${manifest.entryClass} implements IRenderEngine")
                val view = engine.render(payload, this@RendererService)
                val bmp = viewToBitmapInService(view)
                if (bmp != null) return bitmapToPngBytes(bmp)
            }

            // ═══ 降级链 1: renderToBytes(payload) → ByteArray ═══
            val bytes = invokeRenderer(engine, engineClass, payload)
            if (bytes != null) return bytes

            // ═══ 降级链 2: render(payload, context) → Bitmap ═══
            val bitmap = invokeRendererWithContext(engine, engineClass, payload)
            if (bitmap != null) return bitmapToPngBytes(bitmap)

            // 完全失败但 SDK 加载成功 → 返回诊断位图
            generateDiagnosticBitmap(
                label = "SDK Loaded: ${manifest.id} v${manifest.version}",
                available = listOf("Entry class loaded but no known render method: ${manifest.entryClass}")
            )
        } catch (e: ClassNotFoundException) {
            Log.e(TAG, "Entry class not found: ${manifest.entryClass}", e)
            generateDiagnosticBitmap(
                label = "ClassNotFound: ${manifest.entryClass}",
                available = listOf("SDK: ${manifest.id} v${manifest.version}", "Error: ${e.message}")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Reflective render failed: ${manifest.id}", e)
            generateDiagnosticBitmap(
                label = "Render Error: ${manifest.id}",
                available = listOf("Error: ${e.message}")
            )
        }
    }

    /** 确保已加载 SDK 清单（首次调用时扫描 FoundrySDK/）。 */
    private fun ensureManifestsLoaded() {
        if (manifestsLoaded) return
        cachedManifests = LocalSdkScanner.scan(LocalSdkScanner.defaultScanDirs())
        manifestsLoaded = true
        Log.i(TAG, "Loaded ${cachedManifests.size} local SDK manifests")
    }

    /**
     * 获取或创建指定 SDK 的隔离 DexClassLoader。
     * 缓存 key = sdkId，确保同一 SDK 不重复加载。
     */
    private fun getOrCreateClassLoader(manifest: LocalSdkManifest): ClassLoader {
        return classLoaderCache.getOrPut(manifest.id) {
            // 找出与 manifest 关联的 pack 根目录
            val packRoot = findPackRoot(manifest.id)
            val absPaths = manifest.absoluteLibraryPaths(packRoot)
            val existingPaths = absPaths.filter { File(it).exists() }

            if (existingPaths.isEmpty()) {
                Log.w(TAG, "No library files exist for ${manifest.id}, falling back to parent classLoader")
                return@getOrPut this.classLoader
            }

            val dexPath = existingPaths.joinToString(File.pathSeparator)
            Log.i(TAG, "Creating DexClassLoader for ${manifest.id}: ${existingPaths.size} files, dexPath=$dexPath")

            DexClassLoader(
                dexPath,
                codeCacheDir.absolutePath,
                null,
                this.classLoader  // parent: 共享基础 Android 类，隔离第三方 UI 库
            )
        }
    }

    /** 在扫描目录中查找指定 SDK pack 的根目录。 */
    private fun findPackRoot(sdkId: String): File {
        for (dir in LocalSdkScanner.defaultScanDirs()) {
            val packDir = File(dir, sdkId)
            if (packDir.exists() && packDir.isDirectory) return packDir
        }
        // 降级：返回 FoundrySDK/sdkId
        val fallback = File(LocalSdkScanner.defaultScanDirs().first(), sdkId)
        fallback.mkdirs()
        return fallback
    }

    // ═══════════════════════════════════════════════════════════
    //  反射调用渲染引擎
    // ═══════════════════════════════════════════════════════════

    /** 尝试调用 engine.renderToBytes(payload: String): ByteArray? */
    private fun invokeRenderer(engine: Any, engineClass: Class<*>, payload: String): ByteArray? {
        return try {
            val method = engineClass.getMethod("renderToBytes", String::class.java)
            method.invoke(engine, payload) as? ByteArray
        } catch (_: NoSuchMethodException) { null }
    }

    /** 尝试调用 engine.render(payload: String, context: Context): Bitmap */
    private fun invokeRendererWithContext(engine: Any, engineClass: Class<*>, payload: String): Bitmap? {
        return try {
            val method = engineClass.getMethod("render", String::class.java, android.content.Context::class.java)
            method.invoke(engine, payload, this@RendererService) as? Bitmap
        } catch (_: NoSuchMethodException) { null }
    }

    /**
     * 在 Service 环境下将 View 渲染为 Bitmap（无 Window 场景的手动 measure/layout/draw）。
     *
     * Service 没有 Window，无法像 Activity 那样 `addView` 后自动布局。
     * 需要手动调用 View.measure() / layout() / draw() 来完成离屏渲染。
     */
    private fun viewToBitmapInService(view: View, width: Int = 600, height: Int = 400): Bitmap? {
        return try {
            val widthSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            view.measure(widthSpec, heightSpec)
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            val bitmap = Bitmap.createBitmap(
                view.measuredWidth.coerceAtLeast(1),
                view.measuredHeight.coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            view.draw(canvas)
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "viewToBitmapInService failed: ${e.message}", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════
    //  诊断占位位图（空壳 IPC 的视觉证明）
    // ═══════════════════════════════════════════════════════════

    /**
     * 生成诊断占位位图，返回 PNG 字节数组。
     * 包含渲染进程 PID、SDK 信息、诊断提示，证明 IPC 链路已全部打通。
     */
    private fun generateDiagnosticBitmap(label: String, available: List<String>): ByteArray {
        val bmp = Bitmap.createBitmap(600, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)

        // 背景
        canvas.drawColor(Color.parseColor("#1A1A2E"))

        val pid = android.os.Process.myPid()
        var y = 50f

        // 标题
        val titlePaint = Paint().apply {
            color = Color.parseColor("#00D2FF")
            textSize = 36f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("Renderer Sandbox", bmp.width / 2f, y, titlePaint)
        y += 50

        // 标签
        val labelPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            isAntiAlias = true
        }
        canvas.drawText(label, 24f, y, labelPaint)
        y += 40

        // PID / 进程信息
        val infoPaint = Paint().apply {
            color = Color.GRAY
            textSize = 20f
            isAntiAlias = true
        }
        canvas.drawText("PID: $pid  |  Process: :renderer", 24f, y, infoPaint)
        y += 50

        // 可用 SDK 列表
        if (available.isNotEmpty()) {
            val headerPaint = Paint().apply {
                color = Color.parseColor("#E94560")
                textSize = 22f
                isAntiAlias = true
            }
            canvas.drawText("Available SDKs:", 24f, y, headerPaint)
            y += 32

            val itemPaint = Paint().apply {
                color = Color.LTGRAY
                textSize = 18f
                isAntiAlias = true
            }
            for (item in available.take(8)) {
                if (y < bmp.height - 20) {
                    canvas.drawText("  $item", 24f, y, itemPaint)
                    y += 28
                }
            }
        }

        // IPC 状态
        val statusPaint = Paint().apply {
            color = Color.parseColor("#00FF88")
            textSize = 22f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("IPC Pipeline: CONNECTED", bmp.width / 2f, bmp.height - 20f, statusPaint)

        return bitmapToPngBytes(bmp)
    }

    private fun bitmapToPngBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)
        bitmap.recycle()
        return stream.toByteArray()
    }

    // ═══════════════════════════════════════════════════════════
    //  原有 renderInternal（保持兼容）
    // ═══════════════════════════════════════════════════════════

    private fun renderInternal(request: RenderRequest, startMs: Long): RenderResult {
        // 优先尝试 renderToBitmap 路径
        val bytes = try {
            renderToBitmapInternal(request.sourceCode, request.format)
        } catch (_: Exception) { null }

        if (bytes != null) {
            val pngFile = File(cacheDir, "${request.requestId}.png")
            FileOutputStream(pngFile).use { it.write(bytes) }
            return RenderResult(
                requestId = request.requestId,
                status = RenderStatus.SUCCESS,
                bitmapFdPath = pngFile.absolutePath,
                renderTimeMs = System.currentTimeMillis() - startMs
            )
        }

        // 降级为占位位图
        val widthPx = (request.targetWidthDp.coerceIn(240, 1920) * 2f).toInt()
        val heightPx = (request.targetHeightDp.coerceIn(240, 1920) * 2f).toInt()
        val bytes2 = generateDiagnosticBitmap(
            label = "Format: ${request.format}",
            available = cachedManifests.map { "${it.id} v${it.version} (${it.type})" }
        )
        val pngFile = File(cacheDir, "${request.requestId}.png")
        FileOutputStream(pngFile).use { it.write(bytes2) }
        return RenderResult(
            requestId = request.requestId,
            status = RenderStatus.PARTIAL,
            bitmapFdPath = pngFile.absolutePath,
            renderTimeMs = System.currentTimeMillis() - startMs,
            diagnosticCount = cachedManifests.size
        )
    }
}
