package com.foundry.preview.state

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.foundry.preview.dsl.ThemeConfig
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.dsl.UiDocument
import com.foundry.preview.dsl.UiParser
import com.foundry.preview.dsl.UiValidator
import com.foundry.preview.dsl.XmlLayoutParser
import com.foundry.preview.engine.DiagnosticsEngine
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
        val engine = DiagnosticsEngine()
        val parser = UiParser()
        val validator = UiValidator()

        parser.parse(_code.value).fold(
            onSuccess = { doc ->
                engine.addAll(validator.validate(doc))
                if (!engine.hasErrors) {
                    _document.value = doc
                    _statusMessage.value = "Rendered successfully"
                } else {
                    _document.value = null
                    _statusMessage.value = "Render blocked: ${engine.errorCount} error(s)"
                }
            },
            onFailure = { e ->
                engine.addError("Parse error: ${e.message}")
                _document.value = null
                _statusMessage.value = "Parse failed"
            }
        )
        _diagnostics.value = engine
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
        try {
            val xmlContent = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.use { it.readText() }
            if (xmlContent == null) {
                _statusMessage.value = "Failed to read XML file"
                return
            }

            val xmlParser = XmlLayoutParser()
            xmlParser.parse(xmlContent).fold(
                onSuccess = { doc ->
                    undoStack.add(_code.value)
                    redoStack.clear()
                    val jsonString = prettyJson.encodeToString(doc)
                    _code.value = jsonString
                    render()
                    _statusMessage.value = "XML layout imported and converted"
                },
                onFailure = { e ->
                    _statusMessage.value = "XML import failed: ${e.message}"
                }
            )
        } catch (e: Exception) {
            _statusMessage.value = "XML import failed: ${e.message}"
        }
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
}
