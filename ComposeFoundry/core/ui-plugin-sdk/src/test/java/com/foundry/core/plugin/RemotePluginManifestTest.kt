package com.foundry.core.plugin

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RemotePluginManifestTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun manifest_serializationRoundTrip() {
        val manifest = RemotePluginManifest(
            id = "com.foundry.plugin.apk",
            version = "0.1.0",
            displayName = "APK 解析器",
            sha256 = "abcdef",
            entryClass = "com.foundry.plugin.apk.ApkFormatPlugin",
            mirrors = listOf(
                MirrorNode("https://x.com/a.dex", type = MirrorType.SERVER),
                MirrorNode("https://cdn.com/a.dex", type = MirrorType.CDN, priority = 1)
            ),
            description = "demo"
        )
        val text = json.encodeToString(RemotePluginManifest.serializer(), manifest)
        val back = json.decodeFromString<RemotePluginManifest>(text)
        assertEquals(manifest, back)
    }

    @Test
    fun resolvedMirrors_usesMirrorsFirst() {
        val manifest = RemotePluginManifest(
            id = "test", version = "1.0", displayName = "T",
            sha256 = "00", entryClass = "c.T",
            mirrors = listOf(MirrorNode("https://a.dex", type = MirrorType.SERVER)),
            downloadUrl = "https://old.dex"
        )
        val resolved = manifest.resolvedMirrors()
        assertEquals(1, resolved.size)
        assertEquals("https://a.dex", resolved[0].url)
    }

    @Test
    fun resolvedMirrors_fallsBackToDownloadUrl() {
        val manifest = RemotePluginManifest(
            id = "test", version = "1.0", displayName = "T",
            sha256 = "00", entryClass = "c.T",
            downloadUrl = "https://old.dex"
        )
        val resolved = manifest.resolvedMirrors()
        assertEquals(1, resolved.size)
        assertEquals("https://old.dex", resolved[0].url)
        assertEquals(MirrorType.SERVER, resolved[0].type)
    }

    @Test
    fun repositoryIndex_parsesFromJson_withMirrors() {
        val text = """
        {
          "repositoryUrl": "https://x/index.json",
          "plugins": [
            {
              "id": "p1", "version": "1.0.0", "displayName": "P1",
              "sha256": "00", "entryClass": "com.x.P1",
              "mirrors": [
                { "url": "https://a.dex", "type": "SERVER" },
                { "url": "https://b.dex", "type": "CDN", "priority": 2 }
              ]
            }
          ]
        }
        """.trimIndent()
        val idx = json.decodeFromString<PluginRepositoryIndex>(text)
        assertEquals(1, idx.plugins.size)
        val plugin = idx.plugins.first()
        assertEquals("p1", plugin.id)
        assertEquals(2, plugin.mirrors.size)
        assertEquals(MirrorType.CDN, plugin.mirrors[1].type)
    }

    @Test
    fun repositoryIndex_parsesBackwardCompat_downloadUrlOnly() {
        val text = """
        {
          "plugins": [
            {
              "id": "p1", "version": "1.0", "displayName": "P1",
              "sha256": "00", "entryClass": "c.P1",
              "downloadUrl": "https://old.dex"
            }
          ]
        }
        """.trimIndent()
        val idx = json.decodeFromString<PluginRepositoryIndex>(text)
        val resolved = idx.plugins.first().resolvedMirrors()
        assertEquals(1, resolved.size)
        assertEquals("https://old.dex", resolved[0].url)
    }

    @Test
    fun coreVersion_mustBeSemver() {
        assertTrue(CORE_VERSION.matches(Regex("\\d+\\.\\d+\\.\\d+")))
    }
}
