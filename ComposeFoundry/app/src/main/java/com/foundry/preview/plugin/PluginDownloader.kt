package com.foundry.preview.plugin

import android.content.Context
import com.foundry.core.plugin.PluginValidator
import com.foundry.core.plugin.RemotePluginManifest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 插件下载器：将云端 dex/jar/apk 下载到应用私有目录（[DynamicPluginManager.pluginsDir]）。
 * 使用 HttpURLConnection（无额外依赖）。带本地缓存：已存在且 SHA-256 命中则跳过下载。
 */
object PluginDownloader {

    /** 下载插件到内部存储；返回本地文件。缓存命中时直接复用，不重复下载。 */
    fun download(context: Context, manifest: RemotePluginManifest): File {
        val target = DynamicPluginManager.pluginFile(context, manifest)
        val dir = target.parentFile
        if (dir != null && !dir.exists()) dir.mkdirs()

        // 缓存命中：已下载且完整性校验通过则直接复用（节省流量、支持离线）。
        if (target.exists() && PluginValidator.matches(target, manifest.sha256)) {
            return target
        }

        if (manifest.downloadUrl.isBlank()) {
            throw IllegalStateException("Plugin '${manifest.id}' has no downloadUrl")
        }

        val conn = (URL(manifest.downloadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Download failed for ${manifest.id}: HTTP $code")
            }
            conn.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            conn.disconnect()
        }
        return target
    }
}
