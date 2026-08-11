package com.foundry.preview.plugin

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * 本地离线 SDK 清单——对应 FoundrySDK/ 目录下每个子文件夹中的 `foundry-pack.json`。
 *
 * 约定：
 * - 用户将离线包解压到 /storage/emulated/0/FoundrySDK/<pack-id>/
 * - 每个包目录下必须包含 foundry-pack.json
 * - libraries 中的路径相对于 pack 根目录
 */
@Serializable
data class LocalSdkManifest(
    /** 唯一 ID，如 com.foundry.sdk.compose_v1_6 */
    val id: String,
    /** 语义化版本 */
    val version: String,
    /** SDK 类型：COMPOSE_RUNTIME / LAYOUTLIB / XML_PARSER / CUSTOM */
    val type: String,
    /** 入口类全限定名 */
    val entryClass: String,
    /** 库文件路径列表（相对于 pack 根目录的 .jar/.dex） */
    val libraries: List<String> = emptyList(),
    /** 最小核心版本要求 */
    val minCoreVersion: String? = null,
    /** 最小 SDK 级别 */
    val minSdk: Int? = null
) {
    /** 根据 SDK 根目录将相对路径转换为绝对路径。 */
    fun absoluteLibraryPaths(packRoot: File): List<String> =
        libraries.map { File(packRoot, it).absolutePath }
}

/**
 * 本地离线 SDK 扫描器。
 *
 * 扫描 /FoundrySDK/ 目录，寻找包含 foundry-pack.json 的子目录，
 * 解析为 [LocalSdkManifest] 列表，供 [RendererService] 的 DexClassLoader 使用。
 *
 * ## 目录结构示例
 * ```
 * /FoundrySDK/
 *   com.foundry.sdk.compose_v1_6/
 *     foundry-pack.json        ← 清单文件
 *     libs/
 *       compose-runtime.jar
 *       compose-ui.jar
 *       material3.jar
 *   com.foundry.sdk.layoutlib_v34/
 *     foundry-pack.json
 *     libs/
 *       layoutlib.jar
 * ```
 */
object LocalSdkScanner {

    private const val TAG = "LocalSdkScanner"
    private const val MANIFEST_FILE = "foundry-pack.json"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ── 扫描结果缓存（P1: 避免连续预览时反复磁盘 IO） ──
    // 仅缓存非空结果：空结果不缓存，否则用户放入 SDK 后需手动 refresh 才生效。
    // 有 SDK 时重复调用走缓存；refresh() 可强制重新扫描（用户放入新 SDK 后调用）。
    @Volatile
    private var cachedManifests: List<LocalSdkManifest>? = null

    /**
     * 默认扫描目录。
     *
     * ## Android 11+ Scoped Storage 兼容（P0）
     * 主扫描目录为 **App 私有外部目录**：
     * `/storage/emulated/0/Android/data/<package>/files/FoundrySDK`
     * - 无需任何权限，Android 11+ 分区存储下始终可读写
     * - 同 App 的所有进程（主进程 / :renderer）共享该目录
     * - 用户通过 `adb push` 或文件管理器放入离线包
     *
     * 同时保留旧公共目录 `/storage/emulated/0/FoundrySDK` 的兼容扫描，
     * 仅当设备允许读取时加入（Android 11+ 默认不可读，会被自动忽略）。
     */
    fun defaultScanDirs(context: Context): List<File> = listOfNotNull(
        File(context.getExternalFilesDir(null), "FoundrySDK").apply { mkdirs() },
        File(Environment.getExternalStorageDirectory(), "FoundrySDK").takeIf { it.canRead() }
    )

    /**
     * 同步扫描指定目录列表，返回所有符合条件的 [LocalSdkManifest]。
     * 遍历每个一级子目录，查找其中的 foundry-pack.json。
     *
     * 命中缓存时直接返回，避免每次预览都触发磁盘 IO。
     */
    fun scan(dirs: List<File>): List<LocalSdkManifest> {
        cachedManifests?.let { return it }
        val results = doScan(dirs)
        if (results.isNotEmpty()) cachedManifests = results
        return results
    }

    /** 实际扫描逻辑（无缓存）。 */
    private fun doScan(dirs: List<File>): List<LocalSdkManifest> {
        val results = mutableListOf<LocalSdkManifest>()
        for (dir in dirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            dir.listFiles()?.forEach { subDir ->
                if (!subDir.isDirectory) return@forEach
                val manifestFile = File(subDir, MANIFEST_FILE)
                if (!manifestFile.exists() || !manifestFile.isFile) return@forEach
                parseManifest(manifestFile, subDir)?.let { manifest ->
                    results.add(manifest)
                    Log.i(TAG, "Found SDK: ${manifest.id} v${manifest.version} (${manifest.libraries.size} libs)")
                }
            }
        }
        return results
    }

    /** 使扫描缓存失效（用户放入/更新 SDK 后调用，强制下次重新扫描）。 */
    fun refresh() {
        cachedManifests = null
    }

    /** 根据 ID 查找特定 SDK。 */
    fun getSdkById(context: Context, sdkId: String): LocalSdkManifest? =
        scan(defaultScanDirs(context)).firstOrNull { it.id == sdkId }

    /** 解析单个 foundry-pack.json → LocalSdkManifest。 */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private fun parseManifest(jsonFile: File, packRoot: File): LocalSdkManifest? {
        return try {
            val manifest: LocalSdkManifest = jsonFile.inputStream().use { input ->
                json.decodeFromStream(input)
            }
            // 验证库文件存在
            val missingLibs = manifest.libraries.filter {
                !File(packRoot, it).exists()
            }
            if (missingLibs.isNotEmpty()) {
                Log.w(TAG, "SDK ${manifest.id}: missing libs: $missingLibs")
            }
            manifest
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse ${jsonFile.absolutePath}: ${e.message}")
            null
        }
    }

    // ─── 旧的兼容层（供 DynamicPluginManager 使用） ───

    /** 将扫描到的 SDK 汇总为旧格式 [LocalIndex]，供 [DynamicPluginManager.installAll] 使用。 */
    fun toIndex(manifests: List<LocalSdkManifest>, packRoots: List<File>): LocalIndex {
        val entries = mutableListOf<LocalSdkEntry>()
        for (manifest in manifests) {
            // 找到该 manifest 对应的 pack 根目录
            val packRoot = packRoots.firstOrNull { root ->
                File(root, manifest.id).exists()
            } ?: continue

            for (libRel in manifest.libraries) {
                val libFile = File(packRoot, "$manifest.id/$libRel")
                if (!libFile.exists()) continue
                entries.add(LocalSdkEntry(
                    id = manifest.id,
                    version = manifest.version,
                    displayName = manifest.id.substringAfterLast('.'),
                    filePath = libFile.absolutePath,
                    sizeBytes = libFile.length(),
                    sha256 = sha256Of(libFile),
                    entryClass = manifest.entryClass,
                    minSdk = manifest.minSdk ?: 26
                ))
            }
        }
        return LocalIndex(
            scanDirs = packRoots.map { it.absolutePath },
            entries = entries
        )
    }

    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

// ─── 适配 DynamicPluginManager 的旧数据模型 ───

/** 单个本地 SDK 条目（兼容 DynamicPluginManager）。 */
data class LocalSdkEntry(
    val id: String,
    val version: String,
    val displayName: String,
    val filePath: String,
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val entryClass: String,
    val minSdk: Int = 26
)

/** 本地扫描结果索引。 */
data class LocalIndex(
    val scanDirs: List<String>,
    val entries: List<LocalSdkEntry>
)
