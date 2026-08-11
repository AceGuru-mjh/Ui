package com.foundry.preview.plugin

import android.content.Context
import android.os.Build
import android.util.Log
import com.foundry.core.plugin.CORE_VERSION
import com.foundry.core.plugin.PluginManager
import com.foundry.core.plugin.PluginValidator
import com.foundry.core.plugin.RemotePluginManifest
import com.foundry.core.plugin.UiFormatPlugin
import dalvik.system.DexClassLoader
import java.io.File

/**
 * 动态插件管理器：在现有 [PluginManager] 本地注册之上，增加从应用私有存储动态加载并
 * 热注册插件的能力（文章第四节的 DynamicPluginManager）。
 *
 * 安全要点（来自 Android 官方 DexClassLoader 文档与文章第五节）：
 *  - 仅从应用私有内部存储（filesDir / codeCacheDir）加载，绝不从外部存储加载，避免代码注入。
 *  - 加载前用 [PluginValidator] 校验 SHA-256，确保插件未被篡改。
 *  - 通过 parent ClassLoader 传入应用 ClassLoader，使插件能解析主程序与
 *    core:ui-plugin-sdk 中的 [UiFormatPlugin] 契约；插件自带依赖则在其独立 ClassLoader
 *    沙箱中隔离，解决"依赖地狱"。
 *  - 同 id 旧版本存在时先卸载再注册，实现热更新（Instant Fix，无需发版审核）。
 *
 * P0 修复（审查）：在加载 ClassLoader 之前强制执行 minSdk / minCoreVersion 环境拦截，
 * 避免在不满足要求的设备上加载插件导致 VerifyError 或崩溃。
 */
object DynamicPluginManager {

    private const val TAG = "DynamicPluginManager"

    /** 已加载插件的 ClassLoader 记录，便于诊断与卸载/GC。 */
    private val loaders = mutableMapOf<String, ClassLoader>()

    fun pluginsDir(context: Context): File =
        File(context.filesDir, "plugins").also { it.mkdirs() }

    fun pluginFile(context: Context, manifest: RemotePluginManifest): File =
        File(pluginsDir(context), "${manifest.id}/${manifest.id}.dex")

    /**
     * 校验（可选）+ 环境拦截（minSdk / minCoreVersion）+
     * DexClassLoader 动态加载 + 反射实例化 + 注册到现有 [PluginManager]。
     *
     * @param skipShaCheck 仅本地开发调试用，跳过 SHA-256 校验；生产环境必须保持 false。
     */
    @Synchronized
    fun install(
        context: Context,
        manifest: RemotePluginManifest,
        dexFile: File,
        skipShaCheck: Boolean = false
    ): UiFormatPlugin {
        require(dexFile.exists()) { "Plugin file not found: ${dexFile.absolutePath}" }

        if (skipShaCheck) {
            Log.w(TAG, "SHA-256 check SKIPPED for ${manifest.id} (local dev only)")
        } else {
            PluginValidator.verifyOrThrow(dexFile, manifest.sha256)
        }

        // ── P0 修复：环境能力拦截（审查建议：必须修复的漏洞）──
        manifest.minSdk?.let { required ->
            if (Build.VERSION.SDK_INT < required) {
                throw IllegalStateException(
                    "Plugin '${manifest.id}' requires API $required, " +
                        "but current device is API ${Build.VERSION.SDK_INT}"
                )
            }
        }
        manifest.minCoreVersion?.let { required ->
            if (!coreVersionAtLeast(required)) {
                throw IllegalStateException(
                    "Plugin '${manifest.id}' requires core >= $required, " +
                        "but current core is $CORE_VERSION"
                )
            }
        }

        // ── 签名钉扎（可选加固）：manifest.signature 声明了证书指纹时，校验 APK 签名
        //    证书 SHA-256 是否与钉扎值一致，防止中间人替换同内容的恶意 APK。
        //    未声明（null）则跳过，保持向后兼容（仅 SHA-256 完整性校验）。
        manifest.signature?.let { pinned ->
            val actual = extractApkCertSha256(context, dexFile)
            if (!PluginValidator.certMatches(actual, pinned)) {
                throw SecurityException(
                    "Plugin '${manifest.id}' signature pinning failed: " +
                        "expected $pinned but got ${actual ?: "null"}"
                )
            }
        }

        // 热更新：已注册同 id 且版本不同的插件，先卸载再注册。
        val existing = PluginManager.all().firstOrNull { it.descriptor.id == manifest.id }
        if (existing != null && existing.descriptor.version != manifest.version) {
            existing.onDestroy()           // 让旧插件清理静态缓存 / Hook
            PluginManager.unregister(manifest.id)
            loaders.remove(manifest.id)
        }

        // 关键魔法：DexClassLoader 从内部存储加载 dex，parent 共享主程序 ClassLoader。
        // optimizedDirectory 在 API 26+ 已被忽略，但保留传入 codeCacheDir 以保证兼容。
        val loader = DexClassLoader(
            dexFile.absolutePath,
            context.codeCacheDir.absolutePath,
            null,                 // 不携带额外 native 库搜索路径
            context.classLoader  // 共享主程序 + core:ui-plugin-sdk 的 UiFormatPlugin 定义
        )
        val pluginClass = loader.loadClass(manifest.entryClass)
        val instance = pluginClass.getDeclaredConstructor().newInstance() as UiFormatPlugin
        loaders[manifest.id] = loader
        PluginManager.register(instance)
        Log.i(TAG, "Installed plugin ${manifest.id} v${manifest.version} from ${dexFile.absolutePath}")
        return instance
    }

    /**
     * 卸载插件：触发 [UiFormatPlugin.onDestroy] 生命周期清理 → 从注册中心移除 →
     * 移除 ClassLoader 记录（允许 GC 回收） → 删除本地 .dex 文件。
     */
    @Synchronized
    fun uninstall(context: Context, id: String): Boolean {
        val plugin = PluginManager.all().firstOrNull { it.descriptor.id == id }
        if (plugin == null) {
            Log.w(TAG, "Uninstall skipped: plugin '$id' not registered")
            return false
        }
        plugin.onDestroy()
        PluginManager.unregister(id)
        loaders.remove(id)

        // 删除本地 .dex 文件，释放磁盘空间。
        val dexFile = File(pluginsDir(context), "$id/$id.dex")
        val deleted = if (dexFile.exists()) dexFile.delete() else false
        loaders.remove(id)
        Log.i(TAG, "Uninstalled plugin $id; dex deleted=$deleted")
        return true
    }

    /**
     * 语义化版本简单比对：当前 core 版本是否 >= required。
     * 格式：major.minor.patch，按逐段数字比较。
     */
    private fun coreVersionAtLeast(required: String): Boolean {
        return try {
            val curr = CORE_VERSION.split(".").map { it.toInt() }
            val req = required.split(".").map { it.toInt() }
            for (i in 0 until maxOf(curr.size, req.size)) {
                val c = curr.getOrElse(i) { 0 }
                val r = req.getOrElse(i) { 0 }
                if (c > r) return true
                if (c < r) return false
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun isInstalled(id: String): Boolean =
        PluginManager.all().any { it.descriptor.id == id }

    fun loaderFor(id: String): ClassLoader? = loaders[id]

    /**
     * 提取未安装 APK 的签名证书 SHA-256（小写十六进制）。
     * API 28+ 用 PackageManager.GET_SIGNING_CERTIFICATES（支持 v1/v2/v3）；
     * 低版本回退到 PluginValidator 的 v1 证书解析。
     */
    private fun extractApkCertSha256(context: Context, file: File): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            )
            val sig = info?.signingInfo
            val cert = if (sig?.hasMultipleSigners() == true) {
                sig.apkContentsSigners.firstOrNull()
            } else {
                sig?.signingCertificate
            }
            cert?.let { PluginValidator.sha256Of(it.encoded.inputStream()) }
        } else {
            PluginValidator.apkCertSha256V1(file)
        }
    }
}
