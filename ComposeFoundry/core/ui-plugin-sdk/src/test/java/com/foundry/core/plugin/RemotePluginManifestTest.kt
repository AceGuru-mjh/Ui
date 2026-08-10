package com.foundry.core.plugin

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePluginManifestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun manifest_serializationRoundTrip() {
        val manifest = RemotePluginManifest(
            id = "com.foundry.plugin.apk",
            version = "0.1.0",
            displayName = "APK 解析器",
            downloadUrl = "https://example.com/apk.dex",
            sha256 = "abcdef",
            entryClass = "com.foundry.plugin.apk.ApkFormatPlugin",
            description = "demo"
        )
        val text = json.encodeToString(manifest)
        val back = json.decodeFromString<RemotePluginManifest>(text)
        assertEquals(manifest, back)
    }

    @Test
    fun repositoryIndex_parsesFromJson() {
        val text = """
        {
          "repositoryUrl": "https://x/index.json",
          "plugins": [
            {
              "id": "p1", "version": "1.0.0", "displayName": "P1",
              "downloadUrl": "https://x/p1.dex", "sha256": "00", "entryClass": "com.x.P1"
            }
          ]
        }
        """.trimIndent()
        val idx = json.decodeFromString<PluginRepositoryIndex>(text)
        assertEquals(1, idx.plugins.size)
        assertEquals("p1", idx.plugins.first().id)
    }
}
