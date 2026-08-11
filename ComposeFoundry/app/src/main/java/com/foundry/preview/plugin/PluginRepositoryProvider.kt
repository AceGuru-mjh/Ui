package com.foundry.preview.plugin

import android.content.Context
import com.foundry.core.plugin.PluginRepositoryIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * 插件仓库索引提供方：优先从远端 URL 拉取 [PluginRepositoryIndex]，失败时回退到
 * 应用内置 assets（离线可用）。与主程序解耦，便于未来接入真实分发服务。
 */
object PluginRepositoryProvider {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 从内置 assets 读取仓库索引（离线兜底）。 */
    fun loadFromAssets(context: Context): PluginRepositoryIndex? = runCatching {
        context.assets.open("plugin-repository.json").bufferedReader().use {
            json.decodeFromString<PluginRepositoryIndex>(it.readText())
        }
    }.getOrNull()

    fun parse(jsonText: String): PluginRepositoryIndex =
        json.decodeFromString(jsonText)

    /** 从远端 URL 拉取仓库索引（网络请求，须在 IO 调度器调用）。 */
    suspend fun fetchRemote(url: String): PluginRepositoryIndex = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Fetch repository failed: HTTP $code")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            parse(text)
        } finally {
            conn.disconnect()
        }
    }
}
