package com.foundry.core.plugin

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * 插件完整性 / 真实性校验（文章第四节的 Validator）。
 *
 * 纯 Kotlin/JVM，可在 CLI / 服务端 / 未来 KMP 复用。下载插件后必须先用 [verifyOrThrow]
 * 校验 SHA-256，确保文件未被篡改，再交给 DynamicPluginManager 加载，避免代码注入。
 */
object PluginValidator {

    /** 计算文件的 SHA-256 十六进制串（小写）。 */
    fun sha256Of(file: File): String = file.inputStream().use { sha256Of(it) }

    fun sha256Of(stream: InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var read: Int
        while (stream.read(buffer).also { read = it } != -1) {
            md.update(buffer, 0, read)
        }
        return md.digest().toHex()
    }

    /** 比对下载文件与 manifest 声明的 SHA-256 是否一致。 */
    fun matches(file: File, expectedSha256: String): Boolean =
        sha256Of(file).equals(expectedSha256, ignoreCase = true)

    /** 校验失败抛 [SecurityException]，调用方应中止安装。 */
    fun verifyOrThrow(file: File, expectedSha256: String) {
        val actual = sha256Of(file)
        if (!actual.equals(expectedSha256, ignoreCase = true)) {
            throw SecurityException(
                "Plugin integrity check failed: expected $expectedSha256 but got $actual"
            )
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
