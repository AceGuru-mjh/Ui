package com.foundry.preview.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.state.FoundryViewModel

data class InspectNode(
    val path: String,
    val depth: Int,
    val element: UiElement
)

fun flattenTree(element: UiElement, path: String = "root", depth: Int = 0): List<InspectNode> {
    val nodes = mutableListOf(InspectNode(path, depth, element))
    element.children.forEachIndexed { index, child ->
        nodes.addAll(flattenTree(child, "$path.children[$index]", depth + 1))
    }
    return nodes
}

fun findElementByPath(element: UiElement, path: String): UiElement? {
    if (path == "root") return element
    val parts = path.removePrefix("root.").split(".")
    var current = element
    for (part in parts) {
        if (part.startsWith("children[")) {
            val index = part.removePrefix("children[").removeSuffix("]").toIntOrNull() ?: return null
            if (index < current.children.size) {
                current = current.children[index]
            } else {
                return null
            }
        }
    }
    return current
}

@Composable
fun InspectScreen(viewModel: FoundryViewModel) {
    val document by viewModel.document.collectAsState()
    val selectedPath by viewModel.selectedElementPath.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    if (document == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No document rendered.\nGo to Editor tab and click Render.",
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
        return
    }

    val allNodes = flattenTree(document!!.root)
    val filteredNodes = if (searchQuery.isBlank()) {
        allNodes
    } else {
        allNodes.filter { node ->
            node.element.type.contains(searchQuery, ignoreCase = true) ||
            node.element.attributes.values.any { it.contains(searchQuery, ignoreCase = true) } ||
            node.path.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Text(
            text = "Component Tree",
            style = MaterialTheme.typography.titleMedium
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            label = { Text("Search by type or attribute") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall
        )

        Text(
            text = "${filteredNodes.size} / ${allNodes.size} elements",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            items(filteredNodes) { node ->
                InspectNodeRow(
                    node = node,
                    isSelected = node.path == selectedPath,
                    onClick = { viewModel.selectElement(node.path) }
                )
            }
        }

        selectedPath?.let { path ->
            val element = findElementByPath(document!!.root, path)
            if (element != null) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ElementEditPanel(
                    path = path,
                    element = element,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun InspectNodeRow(
    node: InspectNode,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width((node.depth * 16).dp))

        Box(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = node.element.type,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val summary = buildSummary(node.element)
        if (summary.isNotEmpty()) {
            Text(
                text = summary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ElementEditPanel(
    path: String,
    element: UiElement,
    viewModel: FoundryViewModel
) {
    var textValue by remember(element) { mutableStateOf(element.attributes["text"] ?: "") }
    var fontSizeValue by remember(element) { mutableStateOf(element.attributes["fontSize"] ?: "") }
    var colorValue by remember(element) { mutableStateOf(element.attributes["color"] ?: "") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit: ${element.type}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                if (path != "root") {
                    IconButton(onClick = { viewModel.deleteElement(path) }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete element",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Text(
                text = "Path: $path",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                label = { Text("text") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )

            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = fontSizeValue,
                    onValueChange = { fontSizeValue = it },
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                    label = { Text("fontSize") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = colorValue,
                    onValueChange = { colorValue = it },
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                    label = { Text("color (#)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (textValue.isNotEmpty()) {
                        viewModel.updateElementAttribute(path, "text", textValue)
                    }
                    if (fontSizeValue.isNotEmpty()) {
                        viewModel.updateElementAttribute(path, "fontSize", fontSizeValue)
                    }
                    if (colorValue.isNotEmpty()) {
                        viewModel.updateElementAttribute(path, "color", colorValue)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apply Changes")
            }
        }
    }
}

private fun buildSummary(element: UiElement): String {
    val parts = mutableListOf<String>()
    element.attributes["text"]?.let { parts.add("text=\"$it\"") }
    if (element.modifier.fillMaxWidth) parts.add("fillMaxW")
    element.modifier.width?.let { parts.add("w=${it}dp") }
    element.modifier.height?.let { parts.add("h=${it}dp") }
    element.modifier.background?.let { parts.add("bg") }
    if (element.children.isNotEmpty()) parts.add("${element.children.size} children")
    return parts.joinToString(" ")
}
