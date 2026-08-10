package com.foundry.preview.state

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.dsl.UiDocument
import com.foundry.preview.project.ProjectIndex
import com.foundry.preview.project.ProjectIndexer
import com.foundry.core.plugin.ArtifactDetector
import com.foundry.core.plugin.ArtifactKind
import com.foundry.core.plugin.ParseResult
import com.foundry.core.plugin.PluginDescriptor
import com.foundry.core.plugin.PluginManager
import com.foundry.core.plugin.PluginRepositoryIndex
import com.foundry.core.plugin.PreviewContext
import com.foundry.core.plugin.RemotePluginManifest
import com.foundry.core.plugin.UiArtifact
import com.foundry.preview.plugin.DynamicPluginManager
import com.foundry.preview.plugin.PluginDownloader
import com.foundry.preview.plugin.PluginRepositoryProvider
import com.foundry.core.uimodel.ResourceTable
import com.foundry.core.uimodel.UiCapability
import com.foundry.core.uimodel.UiGraph
import com.foundry.preview.project.buildResourceTable
import com.foundry.preview.project.findAndroidProjectRoot
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.plugin.toEngineDiagnostic
import com.foundry.preview.plugin.toUiDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.foundry.codegen.ComposeCodeGenerator
import com.foundry.codegen.CodegenNode
import com.foundry.codegen.CodegenModifier
import com.foundry.codegen.CodegenPadding
import com.foundry.a11y.AccessibilityAuditor
import com.foundry.a11y.A11yNode
import com.foundry.a11y.A11yModifier

enum class RenderMode {
    JSON_DSL,
    XML_DIRECT
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "foundry_settings")

class FoundryViewModel : ViewModel() {

    private val _code = MutableStateFlow("")
    val code: StateFlow<String> = _code.asStateFlow()

    private val _document = MutableStateFlow<UiDocument?>(null)
    val document: StateFlow<UiDocument?> = _document.asStateFlow()

    private val _diagnostics = MutableStateFlow(DiagnosticsEngine())
    val diagnostics: StateFlow<DiagnosticsEngine> = _diagnostics.asStateFlow()

    private val _isDarkTheme = MutableStateFlow(false)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _devicePreset = MutableStateFlow("Pixel 7")
    val devicePreset: StateFlow<String> = _devicePreset.asStateFlow()

    private val _statusMessage = MutableStateFlow("")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _zoomLevel = MutableStateFlow(1.0f)
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    private val _selectedElementPath = MutableStateFlow<String?>(null)
    val selectedElementPath: StateFlow<String?> = _selectedElementPath.asStateFlow()

    private val _renderMode = MutableStateFlow(RenderMode.JSON_DSL)
    val renderMode: StateFlow<RenderMode> = _renderMode.asStateFlow()

    private val _xmlContent = MutableStateFlow("")
    val xmlContent: StateFlow<String> = _xmlContent.asStateFlow()

    // 平台化核心：render() 经由 PluginManager 调用格式插件，产出的规范化 UiGraph
    // 作为“单一可信中间表示”，供 UI 展示元数据、未来替换现有渲染路径。
    private val _uiGraph = MutableStateFlow<UiGraph?>(null)
    val uiGraph: StateFlow<UiGraph?> = _uiGraph.asStateFlow()

    // 任务 D：项目级索引器状态（Stage 4 预览原型）
    private val _projectRootPath = MutableStateFlow("")
    val projectRootPath: StateFlow<String> = _projectRootPath.asStateFlow()

    private val _projectIndex = MutableStateFlow<ProjectIndex?>(null)
    val projectIndex: StateFlow<ProjectIndex?> = _projectIndex.asStateFlow()

    // 动态插件市场（文章第四节：Dynamic Plugin Marketplace）
    private val _pluginRepository = MutableStateFlow<PluginRepositoryIndex?>(null)
    val pluginRepository: StateFlow<PluginRepositoryIndex?> = _pluginRepository.asStateFlow()

    private val _installedPlugins = MutableStateFlow<List<PluginDescriptor>>(emptyList())
    val installedPlugins: StateFlow<List<PluginDescriptor>> = _installedPlugins.asStateFlow()

    private val _installingPluginIds = MutableStateFlow<Set<String>>(emptySet())
    val installingPluginIds: StateFlow<Set<String>> = _installingPluginIds.asStateFlow()

    // 任务：Android 资源引用解析——基于当前文件所在工程根扫描得到的资源表，供插件解析 @string/@color/@dimen。
    private var _resourceTable: ResourceTable = ResourceTable()

    private val undoStack = mutableListOf<String>()
    private val redoStack = mutableListOf<String>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    private val prettyJson = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun initializeDataStore(context: Context) {
        dataStore = context.dataStore
        saveScope.launch {
            try {
                val prefs = dataStore!!.data.first()
                val savedDocs = prefs[KEY_DOCUMENTS]
                val savedActiveIndex = prefs[KEY_ACTIVE_INDEX] ?: 0
                val savedIsDark = prefs[KEY_IS_DARK] ?: false
                val savedDevicePreset = prefs[KEY_DEVICE_PRESET] ?: "Pixel 7"
                val savedNextDocId = prefs[KEY_NEXT_DOC_ID] ?: 1

                _isDarkTheme.value = savedIsDark
                _devicePreset.value = savedDevicePreset
                nextDocId = savedNextDocId

                if (savedDocs != null && savedDocs.isNotEmpty()) {
                    try {
                        val docList = kotlinx.serialization.json.Json.decodeFromString<List<DocumentTab>>(savedDocs)
                        if (docList.isNotEmpty()) {
                            _openDocuments.value = docList
                            val validIndex = savedActiveIndex.coerceIn(0, docList.size - 1)
                            _activeDocIndex.value = validIndex
                            _code.value = docList[validIndex].code
                            render()
                        }
                    } catch (e: Exception) {
                        // 解析失败则使用默认
                    }
                }
            } catch (e: Exception) {
                // DataStore 读取失败，使用默认值
            }
        }
    }

    fun initializeWithSample(dsl: String) {
        if (_code.value.isEmpty()) {
            _code.value = dsl
        }
    }

    fun updateCode(newCode: String) {
        undoStack.add(_code.value)
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
        _code.value = newCode
        scheduleAutoSave()
    }

    fun render() {
        _renderMode.value = RenderMode.JSON_DSL
        // 统一走插件管线：把当前代码作为工件交给 PluginManager，
        // 由匹配的格式插件（JSON DSL）解析为规范化 UiGraph，再转回 UiDocument 渲染。
        // 旧的直接 UiParser 路径已移除。
        viewModelScope.launch {
            val engine = DiagnosticsEngine()
            val baseArtifact = UiArtifact(
                id = "current",
                uri = "",
                displayName = "current document",
                content = _code.value,
                extension = "androidui.json"
            )
            val artifact = baseArtifact.copy(detectedKind = ArtifactDetector.detect(baseArtifact))
            val plugin = PluginManager.selectFor(
                artifact,
                requires = setOf(UiCapability.RENDER_INTERACTIVE)
            )
            if (plugin == null) {
                engine.addError("No format plugin matched the current document")
                _document.value = null
                _diagnostics.value = engine
                _statusMessage.value = "No matching plugin"
                return@launch
            }
            when (val result = plugin.parse(artifact, PreviewContext(resourceTable = _resourceTable))) {
                is ParseResult.Success -> {
                    _uiGraph.value = result.graph
                    result.diagnostics.forEach { engine.add(toEngineDiagnostic(it)) }
                    if (engine.hasErrors) {
                        _document.value = null
                        _statusMessage.value = "Render blocked: ${engine.errorCount} error(s)"
                    } else {
                        _document.value = toUiDocument(result.graph)
                        _statusMessage.value = "Rendered successfully"
                    }
                }
                is ParseResult.Partial -> {
                    _uiGraph.value = result.graph
                    result.diagnostics.forEach { engine.add(toEngineDiagnostic(it)) }
                    _document.value = result.graph?.let { toUiDocument(it) }
                    _statusMessage.value = "Partial render: ${engine.errorCount} error(s)"
                }
                is ParseResult.Failed -> {
                    result.diagnostics.forEach { engine.add(toEngineDiagnostic(it)) }
                    _document.value = null
                    _statusMessage.value = "Plugin parse failed"
                }
            }
            _diagnostics.value = engine
        }
    }

    fun undo() {
        if (undoStack.isNotEmpty()) {
            redoStack.add(_code.value)
            _code.value = undoStack.removeAt(undoStack.lastIndex)
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack.add(_code.value)
            _code.value = redoStack.removeAt(redoStack.lastIndex)
        }
    }

    fun toggleTheme() {
        _isDarkTheme.value = !_isDarkTheme.value
        scheduleAutoSave()
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun setDevicePreset(preset: String) {
        _devicePreset.value = preset
        scheduleAutoSave()
    }

    fun getDeviceDimensions(): Pair<Int, Int> {
        return when (_devicePreset.value) {
            "Small Phone" -> 360 to 640
            "Pixel 7" -> 412 to 915
            "Pixel Tablet" -> 800 to 1280
            else -> 412 to 915
        }
    }

    fun zoomIn() {
        _zoomLevel.value = minOf(_zoomLevel.value + 0.25f, 3.0f)
    }

    fun zoomOut() {
        _zoomLevel.value = maxOf(_zoomLevel.value - 0.25f, 0.5f)
    }

    fun resetZoom() {
        _zoomLevel.value = 1.0f
    }

    fun selectElement(path: String?) {
        _selectedElementPath.value = path
    }

    fun insertComponent(dslSnippet: String) {
        undoStack.add(_code.value)
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()

        val currentCode = _code.value
        val lastBracket = currentCode.lastIndexOf("]")
        if (lastBracket > 0) {
            val before = currentCode.substring(0, lastBracket)
            val after = currentCode.substring(lastBracket)
            val needsComma = before.trimEnd().let {
                it.endsWith("}") || it.endsWith("]") || it.endsWith("\"")
            }
            val separator = if (needsComma) ",\n" else "\n"
            _code.value = before + separator + dslSnippet + "\n" + after
            _statusMessage.value = "Component inserted"
        } else {
            _statusMessage.value = "Cannot insert: no children array found"
        }
    }

    fun updateElementAttribute(path: String, attributeKey: String, attributeValue: String) {
        try {
            val jsonElement = prettyJson.parseToJsonElement(_code.value)
            val pathParts = parsePath(path)
            val updatedJson = navigateAndUpdateAttribute(jsonElement, pathParts, attributeKey, attributeValue)
            val updatedCode = prettyJson.encodeToString(updatedJson)

            undoStack.add(_code.value)
            if (undoStack.size > 50) undoStack.removeAt(0)
            redoStack.clear()
            _code.value = updatedCode
            render()
            _statusMessage.value = "Attribute '$attributeKey' updated"
        } catch (e: Exception) {
            _statusMessage.value = "Update failed: ${e.message}"
        }
    }

    fun deleteElement(path: String) {
        try {
            if (path == "root") {
                _statusMessage.value = "Cannot delete root element"
                return
            }
            val jsonElement = prettyJson.parseToJsonElement(_code.value)
            val pathParts = parsePath(path)
            val updatedJson = navigateAndDelete(jsonElement, pathParts)
            val updatedCode = prettyJson.encodeToString(updatedJson)

            undoStack.add(_code.value)
            if (undoStack.size > 50) undoStack.removeAt(0)
            redoStack.clear()
            _code.value = updatedCode
            _selectedElementPath.value = null
            render()
            _statusMessage.value = "Element deleted"
        } catch (e: Exception) {
            _statusMessage.value = "Delete failed: ${e.message}"
        }
    }

    private fun parsePath(path: String): List<String> {
        if (path == "root") return emptyList()
        return path.removePrefix("root.").split(".")
    }

    private fun navigateAndUpdateAttribute(
        json: JsonElement,
        pathParts: List<String>,
        key: String,
        value: String
    ): JsonElement {
        if (pathParts.isEmpty()) {
            return updateAttributeInObject(json.jsonObject, key, value)
        }

        val part = pathParts[0]
        val remaining = pathParts.drop(1)

        if (part.startsWith("children[")) {
            val index = part.removePrefix("children[").removeSuffix("]").toInt()
            val obj = json.jsonObject
            val children = obj["children"]?.jsonArray ?: return json
            if (index >= children.size) return json

            val updatedChild = navigateAndUpdateAttribute(children[index], remaining, key, value)
            val updatedChildren = children.toMutableList()
            updatedChildren[index] = updatedChild

            return buildJsonObject {
                obj.forEach { (k, v) ->
                    if (k == "children") {
                        put("children", JsonArray(updatedChildren))
                    } else {
                        put(k, v)
                    }
                }
            }
        }

        return json
    }

    private fun navigateAndDelete(json: JsonElement, pathParts: List<String>): JsonElement {
        if (pathParts.isEmpty()) return json

        val part = pathParts[0]
        val remaining = pathParts.drop(1)

        if (part.startsWith("children[")) {
            val index = part.removePrefix("children[").removeSuffix("]").toInt()
            val obj = json.jsonObject
            val children = obj["children"]?.jsonArray ?: return json
            if (index >= children.size) return json

            if (remaining.isEmpty()) {
                val updatedChildren = children.toMutableList()
                updatedChildren.removeAt(index)
                return buildJsonObject {
                    obj.forEach { (k, v) ->
                        if (k == "children") {
                            put("children", JsonArray(updatedChildren))
                        } else {
                            put(k, v)
                        }
                    }
                }
            } else {
                val updatedChild = navigateAndDelete(children[index], remaining)
                val updatedChildren = children.toMutableList()
                updatedChildren[index] = updatedChild
                return buildJsonObject {
                    obj.forEach { (k, v) ->
                        if (k == "children") {
                            put("children", JsonArray(updatedChildren))
                        } else {
                            put(k, v)
                        }
                    }
                }
            }
        }

        return json
    }

    private fun updateAttributeInObject(obj: JsonObject, key: String, value: String): JsonObject {
        return buildJsonObject {
            var hasAttributes = false
            obj.forEach { (k, v) ->
                if (k == "attributes") {
                    hasAttributes = true
                    val attrs = v.jsonObject.toMutableMap()
                    attrs[key] = JsonPrimitive(value)
                    put("attributes", JsonObject(attrs))
                } else {
                    put(k, v)
                }
            }
            if (!hasAttributes) {
                put("attributes", buildJsonObject { put(key, value) })
            }
        }
    }

    @kotlinx.serialization.Serializable
    data class DocumentTab(
        val id: Int,
        val name: String,
        val code: String
    )

    private val _openDocuments = MutableStateFlow(listOf(DocumentTab(0, "Document 1", "")))
    val openDocuments: StateFlow<List<DocumentTab>> = _openDocuments.asStateFlow()

    private val _activeDocIndex = MutableStateFlow(0)
    val activeDocIndex: StateFlow<Int> = _activeDocIndex.asStateFlow()

    private var nextDocId = 1

    private var dataStore: DataStore<Preferences>? = null
    private var saveJob: Job? = null
    private val saveScope = CoroutineScope(Dispatchers.IO)

    companion object {
        private val KEY_DOCUMENTS = stringPreferencesKey("open_documents")
        private val KEY_ACTIVE_INDEX = intPreferencesKey("active_doc_index")
        private val KEY_IS_DARK = booleanPreferencesKey("is_dark_theme")
        private val KEY_DEVICE_PRESET = stringPreferencesKey("device_preset")
        private val KEY_NEXT_DOC_ID = intPreferencesKey("next_doc_id")
    }

    fun addDocument() {
        val docs = _openDocuments.value.toMutableList()
        docs[_activeDocIndex.value] = docs[_activeDocIndex.value].copy(code = _code.value)
        val newDoc = DocumentTab(nextDocId, "Document ${nextDocId + 1}", "")
        nextDocId++
        _openDocuments.value = docs + newDoc
        _activeDocIndex.value = _openDocuments.value.size - 1
        _code.value = ""
        _document.value = null
        _diagnostics.value = DiagnosticsEngine()
        _selectedElementPath.value = null
        scheduleAutoSave()
    }

    fun switchDocument(index: Int) {
        if (index == _activeDocIndex.value || index >= _openDocuments.value.size) return
        val docs = _openDocuments.value.toMutableList()
        docs[_activeDocIndex.value] = docs[_activeDocIndex.value].copy(code = _code.value)
        _openDocuments.value = docs
        _activeDocIndex.value = index
        _code.value = docs[index].code
        _selectedElementPath.value = null
        render()
        scheduleAutoSave()
    }

    fun closeDocument(index: Int) {
        if (_openDocuments.value.size <= 1) return
        val docs = _openDocuments.value.toMutableList()
        docs.removeAt(index)
        _openDocuments.value = docs
        if (_activeDocIndex.value >= docs.size) {
            _activeDocIndex.value = docs.size - 1
        } else if (_activeDocIndex.value > index) {
            _activeDocIndex.value = _activeDocIndex.value - 1
        }
        _code.value = docs[_activeDocIndex.value].code
        _selectedElementPath.value = null
        render()
        scheduleAutoSave()
    }

    fun updateElementModifier(path: String, modifierField: String, value: String) {
        try {
            val jsonElement = prettyJson.parseToJsonElement(_code.value)
            val pathParts = parsePath(path)
            val updatedJson = navigateAndUpdateModifier(jsonElement, pathParts, modifierField, value)
            val updatedCode = prettyJson.encodeToString(updatedJson)

            undoStack.add(_code.value)
            if (undoStack.size > 50) undoStack.removeAt(0)
            redoStack.clear()
            _code.value = updatedCode
            render()
            _statusMessage.value = "Modifier '$modifierField' updated"
        } catch (e: Exception) {
            _statusMessage.value = "Modifier update failed: ${e.message}"
        }
    }

    private fun navigateAndUpdateModifier(
        json: JsonElement,
        pathParts: List<String>,
        field: String,
        value: String
    ): JsonElement {
        if (pathParts.isEmpty()) {
            return updateModifierInObject(json.jsonObject, field, value)
        }
        val part = pathParts[0]
        val remaining = pathParts.drop(1)
        if (part.startsWith("children[")) {
            val index = part.removePrefix("children[").removeSuffix("]").toInt()
            val obj = json.jsonObject
            val children = obj["children"]?.jsonArray ?: return json
            if (index >= children.size) return json
            val updatedChild = navigateAndUpdateModifier(children[index], remaining, field, value)
            val updatedChildren = children.toMutableList()
            updatedChildren[index] = updatedChild
            return buildJsonObject {
                obj.forEach { (k, v) ->
                    if (k == "children") put("children", JsonArray(updatedChildren))
                    else put(k, v)
                }
            }
        }
        return json
    }

    private fun updateModifierInObject(obj: JsonObject, field: String, value: String): JsonObject {
        return buildJsonObject {
            var hasModifier = false
            obj.forEach { (k, v) ->
                if (k == "modifier") {
                    hasModifier = true
                    val modifier = v.jsonObject.toMutableMap()
                    when {
                        field == "padding.all" -> {
                            val padding = modifier["padding"]?.jsonObject?.toMutableMap() ?: mutableMapOf()
                            padding["all"] = JsonPrimitive(value.toFloat())
                            modifier["padding"] = JsonObject(padding)
                        }
                        field == "width" -> modifier["width"] = JsonPrimitive(value.toFloat())
                        field == "height" -> modifier["height"] = JsonPrimitive(value.toFloat())
                        field == "cornerRadius" -> modifier["cornerRadius"] = JsonPrimitive(value.toFloat())
                        field == "background" -> modifier["background"] = JsonPrimitive(value)
                        field == "fillMaxWidth" -> modifier["fillMaxWidth"] = JsonPrimitive(value.toBoolean())
                        field == "fillMaxHeight" -> modifier["fillMaxHeight"] = JsonPrimitive(value.toBoolean())
                        field == "elevation" -> modifier["elevation"] = JsonPrimitive(value.toFloat())
                    }
                    put("modifier", JsonObject(modifier))
                } else {
                    put(k, v)
                }
            }
            if (!hasModifier) {
                put("modifier", buildJsonObject {
                    when {
                        field == "padding.all" -> put("padding", buildJsonObject { put("all", value.toFloat()) })
                        field == "width" -> put("width", value.toFloat())
                        field == "height" -> put("height", value.toFloat())
                        field == "cornerRadius" -> put("cornerRadius", value.toFloat())
                        field == "background" -> put("background", value)
                        field == "fillMaxWidth" -> put("fillMaxWidth", value.toBoolean())
                        field == "fillMaxHeight" -> put("fillMaxHeight", value.toBoolean())
                        field == "elevation" -> put("elevation", value.toFloat())
                    }
                })
            }
        }
    }

    fun importJsonFromUri(context: Context, uri: Uri) {
        try {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
            if (content != null) {
                undoStack.add(_code.value)
                redoStack.clear()
                _code.value = content
                render()
                _statusMessage.value = "JSON imported successfully"
            }
        } catch (e: Exception) {
            _statusMessage.value = "JSON import failed: ${e.message}"
        }
    }

    fun importXmlFromUri(context: Context, uri: Uri) {
        val xmlContent = try {
            context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }
        if (xmlContent == null) {
            _statusMessage.value = "Failed to read XML file"
            return
        }

        // 统一走插件管线：选中 Android XML 插件解析为 UiGraph，再转回 DSL 渲染。
        viewModelScope.launch {
            // 若能通过 uri 路径解析出工程根，则刷新资源表（供 @string/@color/@dimen 解析）
            uri.path?.let { p -> findAndroidProjectRoot(File(p)) }
                ?.let { _resourceTable = buildResourceTable(it) }
            val baseArtifact = UiArtifact(
                id = "imported-xml",
                uri = uri.toString(),
                displayName = "imported xml",
                content = xmlContent,
                extension = "xml"
            )
            val artifact = baseArtifact.copy(detectedKind = ArtifactDetector.detect(baseArtifact))
            val plugin = PluginManager.selectFor(
                artifact,
                requires = setOf(UiCapability.RENDER_INTERACTIVE)
            )
            if (plugin == null) {
                fallbackXmlDirect(xmlContent, "无匹配插件")
                return@launch
            }
            when (val result = plugin.parse(artifact, PreviewContext(resourceTable = _resourceTable))) {
                is ParseResult.Success -> applyImportedGraph(result.graph, xmlContent)
                is ParseResult.Partial -> applyImportedGraph(result.graph, xmlContent)
                is ParseResult.Failed -> fallbackXmlDirect(xmlContent, "插件解析失败")
            }
        }
    }

    private fun applyImportedGraph(graph: UiGraph?, xmlContent: String) {
        val g = graph ?: run {
            fallbackXmlDirect(xmlContent, "无图")
            return
        }
        _uiGraph.value = g
        val doc = toUiDocument(g)
        val jsonString = prettyJson.encodeToString(doc)
        undoStack.add(_code.value)
        redoStack.clear()
        _code.value = jsonString
        _document.value = doc
        _renderMode.value = RenderMode.JSON_DSL
        _statusMessage.value = "XML imported via ${g.meta.parser}"
    }

    private fun fallbackXmlDirect(xmlContent: String, reason: String) {
        _renderMode.value = RenderMode.XML_DIRECT
        _xmlContent.value = xmlContent
        _statusMessage.value = "XML 直接预览模式（$reason）"
    }

    fun exportJsonToUri(context: Context, uri: Uri) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(_code.value.toByteArray(Charsets.UTF_8))
            }
            _statusMessage.value = "JSON exported successfully"
        } catch (e: Exception) {
            _statusMessage.value = "JSON export failed: ${e.message}"
        }
    }

    fun exportPngToUri(context: Context, uri: Uri, bitmap: Bitmap) {
        try {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
            _statusMessage.value = "PNG exported successfully"
        } catch (e: Exception) {
            _statusMessage.value = "PNG export failed: ${e.message}"
        }
    }

    fun clearStatus() {
        _statusMessage.value = ""
    }

    // ───────────────────────── 动态插件市场 ─────────────────────────

    /** 加载插件仓库索引（优先内置 assets，离线可用）并刷新已注册插件清单。 */
    fun loadPluginRepository(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            _pluginRepository.value = PluginRepositoryProvider.loadFromAssets(context)
            refreshInstalledPlugins()
        }
    }

    /** 从远端 URL 拉取仓库索引（网络）。 */
    fun loadPluginRepositoryRemote(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { PluginRepositoryProvider.fetchRemote(url) }
                .onSuccess { _pluginRepository.value = it; refreshInstalledPlugins() }
                .onFailure { _statusMessage.value = "仓库索引拉取失败: ${it.message}" }
        }
    }

    /** 重新同步已注册插件（内置 + 动态加载）的清单，供 UI 展示。 */
    fun refreshInstalledPlugins() {
        _installedPlugins.value = PluginManager.all().map { it.descriptor }
    }

    /** 完整流程：多源测速下载（带缓存 + 进度）→ SHA-256 校验 → DexClassLoader 加载 → 热注册。 */
    fun installPlugin(context: Context, manifest: RemotePluginManifest) {
        viewModelScope.launch(Dispatchers.IO) {
            _installingPluginIds.value = _installingPluginIds.value + manifest.id
            try {
                val file = PluginDownloader.downloadSmart(context, manifest)
                DynamicPluginManager.install(context, manifest, file)
                refreshInstalledPlugins()
                _statusMessage.value = "已安装插件 ${manifest.displayName} v${manifest.version}"
            } catch (e: Exception) {
                _statusMessage.value = "插件安装失败: ${e.message}"
            } finally {
                _installingPluginIds.value = _installingPluginIds.value - manifest.id
            }
        }
    }

    /** 卸载插件：触发 onDestroy 生命周期清理 + 删除本地 .dex + 从注册中心移除。 */
    fun uninstallPlugin(context: Context, id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (DynamicPluginManager.uninstall(context, id)) {
                refreshInstalledPlugins()
                _statusMessage.value = "已卸载插件 $id（重启应用可彻底释放内存）"
            }
        }
    }

    /**
     * 本地插件热加载（开发者调试用）：从文档选择器读取 dex/jar/apk 并加载。
     * 约定入口类为 com.foundry.plugin.LocalPlugin，并跳过 SHA-256 校验。
     */
    fun installPluginFromUri(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
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
                _statusMessage.value = "已加载本地插件 $name"
            } catch (e: Exception) {
                _statusMessage.value = "本地插件加载失败: ${e.message}"
            }
        }
    }

    private fun scheduleAutoSave() {
        saveJob?.cancel()
        saveJob = saveScope.launch {
            delay(2000)
            performSave()
        }
    }

    private suspend fun performSave() {
        val store = dataStore ?: return
        try {
            val docsJson = kotlinx.serialization.json.Json.encodeToString(_openDocuments.value)
            store.edit { prefs ->
                prefs[KEY_DOCUMENTS] = docsJson
                prefs[KEY_ACTIVE_INDEX] = _activeDocIndex.value
                prefs[KEY_IS_DARK] = _isDarkTheme.value
                prefs[KEY_DEVICE_PRESET] = _devicePreset.value
                prefs[KEY_NEXT_DOC_ID] = nextDocId
            }
        } catch (e: Exception) {
            // 保存失败静默处理
        }
    }

    fun saveNow() {
        saveJob?.cancel()
        saveScope.launch {
            performSave()
        }
    }

    fun loadTemplate(templateDsl: String) {
        undoStack.add(_code.value)
        if (undoStack.size > 50) undoStack.removeAt(0)
        redoStack.clear()
        _code.value = templateDsl
        render()
        _statusMessage.value = "Template loaded"
        scheduleAutoSave()
    }

    // ===== v1.9 库模块集成 =====

    private val _generatedCode = MutableStateFlow("")
    val generatedCode: StateFlow<String> = _generatedCode.asStateFlow()

    private val _accessibilityReport = MutableStateFlow("")
    val accessibilityReport: StateFlow<String> = _accessibilityReport.asStateFlow()

    fun generateComposeCode() {
        _document.value?.let { doc ->
            val rootNode = toCodegenNode(doc.root)
            val generator = ComposeCodeGenerator()
            _generatedCode.value = generator.generate(rootNode)
            _statusMessage.value = "Compose code generated (${_generatedCode.value.length} chars)"
        } ?: run {
            _statusMessage.value = "No document rendered"
        }
    }

    fun runAccessibilityAudit() {
        _document.value?.let { doc ->
            val rootNode = toA11yNode(doc.root)
            val auditor = AccessibilityAuditor()
            val issues = auditor.audit(rootNode, doc.theme.backgroundColor)
            _accessibilityReport.value = auditor.formatReport(issues)
            _statusMessage.value = "Audit: ${issues.size} issue(s) found"
        } ?: run {
            _statusMessage.value = "No document rendered"
        }
    }

    private fun toCodegenNode(element: UiElement): CodegenNode {
        return CodegenNode(
            type = element.type,
            modifier = CodegenModifier(
                fillMaxWidth = element.modifier.fillMaxWidth,
                fillMaxHeight = element.modifier.fillMaxHeight,
                fillMaxSize = element.modifier.fillMaxSize,
                width = element.modifier.width,
                height = element.modifier.height,
                padding = element.modifier.padding?.let { p ->
                    CodegenPadding(
                        all = p.all, horizontal = p.horizontal, vertical = p.vertical,
                        start = p.start, top = p.top, end = p.end, bottom = p.bottom
                    )
                },
                background = element.modifier.background,
                cornerRadius = element.modifier.cornerRadius,
                borderWidth = element.modifier.borderWidth,
                borderColor = element.modifier.borderColor,
                elevation = element.modifier.elevation,
                horizontalAlignment = element.modifier.horizontalAlignment,
                verticalArrangement = element.modifier.verticalArrangement,
                verticalAlignment = element.modifier.verticalAlignment,
                horizontalArrangement = element.modifier.horizontalArrangement,
                contentAlignment = element.modifier.contentAlignment
            ),
            attributes = element.attributes,
            children = element.children.map { toCodegenNode(it) }
        )
    }

    private fun toA11yNode(element: UiElement): A11yNode {
        return A11yNode(
            type = element.type,
            attributes = element.attributes,
            modifier = A11yModifier(
                width = element.modifier.width,
                height = element.modifier.height,
                background = element.modifier.background
            ),
            children = element.children.map { toA11yNode(it) }
        )
    }

    // ===== 任务 D：项目级索引 / 加载（Stage 4 原型） =====

    fun setProjectRootPath(path: String) {
        _projectRootPath.value = path
    }

    /** 扫描项目目录，列出可预览的 XML/JSON/KT 文件。 */
    fun indexProject() {
        val root = _projectRootPath.value.trim()
        if (root.isEmpty()) {
            _statusMessage.value = "请输入项目根目录路径"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val index = ProjectIndexer.index(root)
            _projectIndex.value = index
            _statusMessage.value = "索引完成：${index.previewableCount} 个可预览文件（共 ${index.files.size} 个）"
        }
    }

    /** 从项目索引中加载某个文件，走统一插件管线渲染。 */
    fun loadProjectFile(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = runCatching { File(path).readText(Charsets.UTF_8) }
                .getOrElse { e ->
                    _statusMessage.value = "读取失败：${e.message}"
                    return@launch
                }
            val ext = path.substringAfterLast('.', "").lowercase()
            // 基于被加载文件所在工程根刷新资源表（向上找含 res/ 或 AndroidManifest.xml 的目录）
            _resourceTable = buildResourceTable(findAndroidProjectRoot(File(path)) ?: File(path))
            val baseArtifact = UiArtifact(
                id = "project:$path",
                uri = path,
                displayName = path.substringAfterLast('/').substringAfterLast('\\'),
                content = content,
                extension = ext
            )
            val artifact = baseArtifact.copy(detectedKind = ArtifactDetector.detect(baseArtifact))
            val plugin = PluginManager.selectFor(artifact, requires = setOf(UiCapability.RENDER_INTERACTIVE))
                ?: PluginManager.selectFor(artifact)
            val p = plugin ?: run {
                _statusMessage.value = "无匹配插件：${artifact.displayName}"
                return@launch
            }
            when (val result = p.parse(artifact, PreviewContext(resourceTable = _resourceTable))) {
                is ParseResult.Success -> {
                    _uiGraph.value = result.graph
                    _document.value = toUiDocument(result.graph)
                    _code.value = content
                    _renderMode.value = RenderMode.JSON_DSL
                    _statusMessage.value = "已加载并预览：${artifact.displayName}"
                }
                is ParseResult.Partial -> {
                    _uiGraph.value = result.graph
                    _document.value = result.graph?.let { toUiDocument(it) }
                    _code.value = content
                    _statusMessage.value = "部分预览（有降级）：${artifact.displayName}"
                }
                is ParseResult.Failed -> {
                    _statusMessage.value = "解析失败：${artifact.displayName}"
                }
            }
        }
    }
}
