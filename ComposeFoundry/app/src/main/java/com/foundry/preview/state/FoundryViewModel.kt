package com.foundry.preview.state

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import com.foundry.preview.dsl.ThemeConfig
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
    }

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun setDevicePreset(preset: String) {
        _devicePreset.value = preset
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
}
