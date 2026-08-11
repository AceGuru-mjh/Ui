package com.foundry.core.plugin

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PluginValidatorTest {

    @Test
    fun sha256_matchesKnownContent() {
        val f = File.createTempFile("plugin", ".dex").apply { writeText("dummy dex content") }
        val sha = PluginValidator.sha256Of(f)
        assertTrue("same sha should match", PluginValidator.matches(f, sha))
        PluginValidator.verifyOrThrow(f, sha) // 不应抛异常
    }

    @Test
    fun sha256_rejectsTamperedContent() {
        val f = File.createTempFile("plugin", ".dex").apply { writeText("original content") }
        val sha = PluginValidator.sha256Of(f)
        f.writeText("tampered content")
        assertFalse("tampered file must not match original sha", PluginValidator.matches(f, sha))
        assertThrows(SecurityException::class.java) { PluginValidator.verifyOrThrow(f, sha) }
    }

    @Test
    fun sha256_isHexAnd64Chars() {
        val f = File.createTempFile("plugin", ".dex").apply { writeText("abc") }
        val sha = PluginValidator.sha256Of(f)
        assertTrue(sha.matches(Regex("[0-9a-f]{64}")))
    }
}
