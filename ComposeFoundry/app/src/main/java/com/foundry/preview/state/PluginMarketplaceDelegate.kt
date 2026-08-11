package com.foundry.preview.state

import android.content.Context
import android.net.Uri
import android.util.Log
import com.foundry.core.plugin.PluginDescriptor
import com.foundry.core.plugin.PluginManager
import com.foundry.core.plugin.PluginRepositoryIndex
import com.foundry.core.plugin.RemotePluginManifest
import com.foundry.preview.plugin.DynamicPluginManager
import com.foundry.preview.plugin.PluginDownloader
import com.foundry.preview.plugin.PluginRepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 动态插件市场委托 — 从 [FoundryViewModel] 中拆出，遵循单一职责原则。
 *
 * 职责：
 *  - 加载插件仓库索引（本地 assets / 远端 URL）
 *  - 检查远端更新
 *  - 安装 / 卸载 / 本地热加载插件
 *  - 刷新已注册插件清单
 *
 * 不持有 [Context] 引用（避免 Activity 泄漏），每次方法调用显式传入。
 */
class PluginMarketplaceDelegate(
    private val scope: CoroutineScope,
    private val _pluginRepository: MutableStateFlow<PluginRepositoryIndex?>,
    private val _installedPlugins: MutableStateFlow<List<PluginDescriptor>>,
    private val _installingPluginIds: MutableStateFlow<Set<String>>,
    private val onStatus: (String) -> Unit
) {
    val pluginRepository: StateFlow<PluginRepositoryIndex?> = _pluginRepository
    val installedPlugins: StateFlow<List<PluginDescriptor>> = _installedPlugins
    val installingPluginIds: StateFlow<Set<String>> = _installingPluginIds

    /** 加载插件仓库索引（优先内置 assets，离线可用）并刷新已注册插件清单。 */
    fun loadPluginRepository(context: Context) {
        scope.launch(Dispatchers.IO) {
            _pluginRepository.value = PluginRepositoryProvider.loadFromAssets(context)
            refreshInstalledPlugins()
        }
    }

    /** 从远端 URL 拉取仓库索引（网络）。 */
    fun loadPluginRepositoryRemote(url: String) {
        scope.launch(Dispatchers.IO) {
            runCatching { PluginRepositoryProvider.fetchRemote(url) }
                .onSuccess { _pluginRepository.value = it; refreshInstalledPlugins() }
                .onFailure { onStatus("仓库索引拉取失败: ${it.message}") }
        }
    }

    /**
     * 检查更新：拉取远端仓库索引并与已安装版本比对。
     */
    fun checkForUpdates(context: Context) {
        scope.launch(Dispatchers.IO) {
            val url = _pluginRepository.value?.repositoryUrl
                ?: "https://plugins.composefoundry.dev/index.json"
            runCatching { PluginRepositoryProvider.fetchRemote(url) }
                .onSuccess {
                    _pluginRepository.value = it
                    refreshInstalledPlugins()
                    onStatus("已检查更新：远端 ${it.plugins.size} 个插件可用")
                }
                .onFailure { onStatus("检查更新失败（沿用本地索引）: ${it.message}") }
        }
    }

    /** 重新同步已注册插件（内置 + 动态加载）的清单，供 UI 展示。 */
    fun refreshInstalledPlugins() {
        _installedPlugins.value = PluginManager.all().map { it.descriptor }
    }

    /** 完整流程：多源测速下载（带缓存 + 进度）→ SHA-256 校验 → DexClassLoader 加载 → 热注册。 */
    fun installPlugin(context: Context, manifest: RemotePluginManifest) {
        scope.launch(Dispatchers.IO) {
            _installingPluginIds.value = _installingPluginIds.value + manifest.id
            try {
                val file = PluginDownloader.downloadSmart(context, manifest)
                DynamicPluginManager.install(context, manifest, file)
                refreshInstalledPlugins()
                onStatus("已安装插件 ${manifest.displayName} v${manifest.version}")
            } catch (e: SecurityException) {
                onStatus("插件安装被安全策略阻止: ${e.message}")
            } catch (e: Exception) {
                onStatus("插件安装失败: ${e.message}")
            } finally {
                _installingPluginIds.value = _installingPluginIds.value - manifest.id
            }
        }
    }

    /** 卸载插件：触发 onDestroy 生命周期清理 + 删除本地 .dex + 从注册中心移除。 */
    fun uninstallPlugin(context: Context, id: String) {
        scope.launch(Dispatchers.IO) {
            if (DynamicPluginManager.uninstall(context, id)) {
                refreshInstalledPlugins()
                onStatus("已卸载插件 $id（重启应用可彻底释放内存）")
            }
        }
    }

    /**
     * 本地插件热加载（开发者调试用）：从文档选择器读取 dex/jar/apk 并加载。
     * 约定入口类为 com.foundry.plugin.LocalPlugin，并跳过 SHA-256 校验。
     */
    fun installPluginFromUri(context: Context, uri: Uri) {
        scope.launch(Dispatchers.IO) {
            try {
                val name = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.replace(Regex("\\.(dex|jar|apk)$"), "Plugin")
                    ?: "LocalPlugin"
                val target = File(DynamicPluginManager.pluginsDir(context), "$name/$name.dex")
                target.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                }
                val manifest = RemotePluginManifest(
                    id = "local.$name",
                    version = "local",
                    displayName = name,
                    downloadUrl = "",
                    sha256 = "",
                    entryClass = "com.foundry.plugin.LocalPlugin"
                )
                DynamicPluginManager.install(context, manifest, target, skipShaCheck = true)
                refreshInstalledPlugins()
                onStatus("已加载本地插件 $name")
            } catch (e: Exception) {
                onStatus("本地插件加载失败: ${e.message}")
            }
        }
    }
}
