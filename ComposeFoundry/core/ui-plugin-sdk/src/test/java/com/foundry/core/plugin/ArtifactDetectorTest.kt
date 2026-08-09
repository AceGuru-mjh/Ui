package com.foundry.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtifactDetectorTest {

    private fun art(content: String? = null, extension: String? = null, mimeType: String? = null) =
        UiArtifact(
            id = "t", uri = "", displayName = "t",
            content = content, extension = extension, mimeType = mimeType
        )

    @Test
    fun jsonDsl_detectedFromContent() {
        val json = """{ "type": "column", "root": { "type": "text" } }"""
        assertEquals(ArtifactKind.JSON_DSL, ArtifactDetector.detectFromContent(json))
        assertEquals(ArtifactKind.JSON_DSL, ArtifactDetector.detect(art(content = json)))
    }

    @Test
    fun plainJson_isUnknownText_notDsl() {
        val json = """{ "name": "ace", "age": 3 }"""
        assertEquals(ArtifactKind.UNKNOWN_TEXT, ArtifactDetector.detectFromContent(json))
    }

    @Test
    fun xmlLayout_detectedFromAndroidNamespace() {
        val xml = """<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent" android:layout_height="wrap_content"/>"""
        assertEquals(ArtifactKind.ANDROID_XML_LAYOUT, ArtifactDetector.detectFromContent(xml))
        assertEquals(ArtifactKind.ANDROID_XML_LAYOUT, ArtifactDetector.detect(art(content = xml)))
    }

    @Test
    fun xmlManifest_detected() {
        val xml = """<manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application android:label="App"/></manifest>"""
        assertEquals(ArtifactKind.ANDROID_MANIFEST, ArtifactDetector.detectFromContent(xml))
    }

    @Test
    fun xmlNavigation_detected() {
        val xml = """<navigation xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            app:startDestination="@id/home"></navigation>"""
        assertEquals(ArtifactKind.ANDROID_NAVIGATION, ArtifactDetector.detectFromContent(xml))
    }

    @Test
    fun xmlDrawable_detected() {
        val xml = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
            android:width="24dp" android:height="24dp"></vector>"""
        assertEquals(ArtifactKind.ANDROID_XML_DRAWABLE, ArtifactDetector.detectFromContent(xml))
    }

    @Test
    fun xmlValues_detected() {
        val xml = """<resources><string name="app_name">Foundry</string></resources>"""
        assertEquals(ArtifactKind.ANDROID_XML_VALUES, ArtifactDetector.detectFromContent(xml))
    }

    @Test
    fun xmlMenu_detected() {
        val xml = """<menu xmlns:android="http://schemas.android.com/apk/res/android">
            <item android:title="Settings"/></menu>"""
        assertEquals(ArtifactKind.ANDROID_MENU, ArtifactDetector.detectFromContent(xml))
    }

    @Test
    fun mime_json_fallback() {
        assertEquals(ArtifactKind.JSON_DSL, ArtifactDetector.detectFromMime("application/json"))
    }

    @Test
    fun extension_xml_fallback() {
        assertEquals(ArtifactKind.ANDROID_XML_LAYOUT, ArtifactDetector.detectFromExtension("xml"))
        assertEquals(ArtifactKind.APK, ArtifactDetector.detectFromExtension("apk"))
    }

    @Test
    fun fullDetect_prefersContentOverExtension() {
        // 后缀是 .xml 但内容是 JSON DSL —— 内容优先
        val json = """{ "type": "column", "root": {} }"""
        assertEquals(
            ArtifactKind.JSON_DSL,
            ArtifactDetector.detect(art(content = json, extension = "xml"))
        )
    }

    @Test
    fun emptyContent_fallsBackToExtension() {
        assertEquals(
            ArtifactKind.ANDROID_XML_LAYOUT,
            ArtifactDetector.detect(art(extension = "xml"))
        )
    }

    @Test
    fun unrecognized_isUnknown() {
        assertEquals(ArtifactKind.UNKNOWN_TEXT, ArtifactDetector.detect(art(content = "just some prose")))
        assertNull(ArtifactDetector.detectFromMime("text/plain"))
    }
}
