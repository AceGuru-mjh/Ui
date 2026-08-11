package com.foundry.preview.plugin

import android.content.Context
import android.util.Log
import com.foundry.core.plugin.MirrorNode
import com.foundry.core.plugin.MirrorType
import com.foundry.core.plugin.PluginValidator
import com.foundry.core.plugin.RemotePluginManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 智能多源下载器：并发测速 (Racing) + 故障转移 (Failover)。
 *
 * 从 [RemotePluginManifest.resolvedMirrors] 中选取延迟最低的镜像下载，中途失败
 * 自动切换备用节点；支持进度回调 [onProgress] 供 UI 展示进度条。
 * 带本地缓存：已存在且 SHA-256 命中则跳过下载。
 */
object PluginDownloader {

    private const val TAG = "PluginDownloader"
    private const val PROBE_TIMEOUT_MS = 10_000
    private const val DOWNLOAD_CONNECT_MS = 15_000
    private const val DOWNLOAD_READ_MS = 60_000

    /**
     * 安全校验：拒绝非 HTTPS 的远端 URL，防止中间人攻击替换插件文件。
     * localhost/10.x.x.x/192.168.x.x 等本地地址允许 HTTP（仅调试/内网场景）。
     */
    private fun requireSecure(url: String) {
        val lowered = url.lowercase()
        if (!lowered.startsWith("https://") && !lowered.startsWith("http://localhost") &&
            !lowered.startsWith("http://10.") && !lowered.startsWith("http://192.168.") &&
            !lowered.startsWith("http://172.1") && !lowered.startsWith("http://127.")
        ) {
            throw SecurityException(
                "Plugin download URL must use HTTPS for security: $url. " +
                "Only localhost and private-network (RFC 1918) HTTP URLs are exempted."
            )
        }
    }

    /**
     * 智能下载入口。
     *
     * @param onProgress 进度回调 (bytesRead, totalBytes)；totalBytes 未知时为 -1。
     */
    suspend fun downloadSmart(
        context: Context,
        manifest: RemotePluginManifest,
        onProgress: ((bytesRead: Long, totalBytes: Long) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val target = DynamicPluginManager.pluginFile(context, manifest)
        val dir = target.parentFile
        if (dir != null && !dir.exists()) dir.mkdirs()

        // 缓存命中直接复用。
        if (target.exists() && PluginValidator.matches(target, manifest.sha256)) {
            return@withContext target
        }

        val mirrorNodes = manifest.resolvedMirrors()
        if (mirrorNodes.isEmpty()) {
            throw IllegalStateException("Plugin '${manifest.id}' has no mirrors or downloadUrl")
        }

        // 1. 并发测速（Racing）：同时发送 HEAD 请求到所有镜像，首个返回即为最优。
        val fastest = selectFastestMirror(mirrorNodes)
            ?: throw IllegalStateException("All mirrors unreachable for plugin '${manifest.id}'")

        // 2. 从最快节点下载；失败则故障转移到剩余节点。
        val remaining = mirrorNodes.toMutableList().also { it.remove(fastest) }
        downloadWithFailover(fastest, remaining, target, onProgress)
        PluginValidator.verifyOrThrow(target, manifest.sha256)
        return@withContext target
    }

    // ─────────── 内部实现 ───────────

    /** 并发探测所有镜像，返回首个可达的节点（最快响应）。 */
    private suspend fun selectFastestMirror(nodes: List<MirrorNode>): MirrorNode? =
        withContext(Dispatchers.IO) {
            nodes.map { node ->
                async {
                    probe(node)?.let { node to it }
                }
            }.awaitAll()
                .filterNotNull()
                .minByOrNull { it.second }
                ?.first
        }

    /** 下载：从 primary 开始，失败则逐个尝试 fallback。 */
    private suspend fun downloadWithFailover(
        primary: MirrorNode,
        fallbacks: List<MirrorNode>,
        target: File,
        onProgress: ((Long, Long) -> Unit)?
    ) = withContext(Dispatchers.IO) {
        var lastError: Exception? = null
        val queue = mutableListOf(primary) + fallbacks
        for (node in queue) {
            try {
                downloadFromUrl(node.url, target, onProgress)
                return@withContext
            } catch (e: Exception) {
                Log.w(TAG, "Mirror ${node.url} failed: ${e.message}; falling back...")
                lastError = e
            }
        }
        throw lastError ?: IllegalStateException("All mirrors failed")
    }

    /** HEAD 探测：检查可达性并返回耗时（ms）；不可达返回 null。 */
    private fun probe(node: MirrorNode): Long? = runCatching {
        requireSecure(node.url)
        val start = System.currentTimeMillis()
        val conn = (URL(node.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"
            connectTimeout = PROBE_TIMEOUT_MS
            readTimeout = PROBE_TIMEOUT_MS
        }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code in 200..299 || code == HttpURLConnection.HTTP_MOVED_TEMP || code == HttpURLConnection.HTTP_MOVED_PERM) {
                System.currentTimeMillis() - start
            } else null
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** 从单个 URL 下载到 target，支持进度回调。 */
    private fun downloadFromUrl(url: String, target: File, onProgress: ((Long, Long) -> Unit)?) {
        requireSecure(url)
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = DOWNLOAD_CONNECT_MS
            readTimeout = DOWNLOAD_READ_MS
        }
        try {
            conn.connect()
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from $url")
            }
            val totalBytes = conn.contentLengthLong
            val input = conn.inputStream
            target.outputStream().use { output ->
                copyWithProgress(input, output, totalBytes, onProgress)
            }
        } finally {
            conn.disconnect()
        }
    }

    /** 带进度回调的流拷贝。 */
    private fun copyWithProgress(
        input: InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        onProgress: ((Long, Long) -> Unit)?
    ) {
        val buffer = ByteArray(8192)
        var read: Int
        var totalRead = 0L
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
            totalRead += read
            onProgress?.invoke(totalRead, totalBytes)
        }
    }
}
