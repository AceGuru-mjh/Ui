package com.foundry.preview.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.state.FoundryViewModel

data class InspectItem(
    val path: String,
    val depth: Int,
    val element: UiElement
)

private fun flattenTree(
    element: UiElement,
    path: String = "root",
    depth: Int = 0
): List<InspectItem> {
    val items = mutableListOf(InspectItem(path, depth, element))
    element.children.forEachIndexed { index, child ->
        items.addAll(flattenTree(child, "$path.children[$index]", depth + 1))
    }
    return items
}

private fun matchesQuery(item: InspectItem, query: String): Boolean {
    if (query.isBlank()) return true
    val q = query.trim().lowercase()
    if (item.element.type.lowercase().contains(q)) return true
    return item.element.attributes.any { (k, v) ->
        k.lowercase().contains(q) || v.lowercase().contains(q)
    }
}

@Composable
fun InspectScreen(viewModel: FoundryViewModel) {
    val document by viewModel.document.collectAsState()
    val selectedPath by viewModel.selectedElementPath.collectAsState()

    var searchQuery by remember { mutableStateOf("") }

    if (document == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp)
        ) {
            Text(
                text = "No document rendered",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Go to Editor tab, enter DSL, and click Render first.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val allItems = remember(document) { flattenTree(document!!.root) }
    val filteredItems = allItems.filter { matchesQuery(it, searchQuery) }
    val selectedItem = selectedPath?.let { path ->
        allItems.firstOrNull { it.path == path }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp)
    ) {
        Text(
            text = "Component Tree",
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = "${filteredItems.size} / ${allItems.size} elements" +
                if (searchQuery.isNotBlank()) " (filtered)" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            placeholder = { Text("Search by type or attribute…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            items(filteredItems) { item ->
                InspectRow(
                    item = item,
                    isSelected = item.path == selectedPath,
                    onClick = {
                        viewModel.selectElement(
                            if (selectedPath == item.path) null else item.path
                        )
                    }
                )
            }
        }

        selectedItem?.let { item ->
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            EditPanel(
                item = item,
                onUpdateAttribute = { key, value ->
                    viewModel.updateElementAttribute(item.path, key, value)
                },
                onDelete = {
                    if (item.path != "root") {
                        viewModel.deleteElement(item.path)
                    }
                }
            )
        }
    }
}

@Composable
private fun InspectRow(
    item: InspectItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.width((item.depth * 16).dp))

            Text(
                text = item.element.type,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(8.dp))

            val summary = buildSummary(item.element)
            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }

        if (item.element.attributes.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (item.depth * 16 + 16).dp, end = 8.dp, bottom = 6.dp)
            ) {
                item.element.attributes.forEach { (key, value) ->
                    Text(
                        text = "$key = \"$value\"",
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun EditPanel(
    item: InspectItem,
    onUpdateAttribute: (String, String) -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Edit: ${item.element.type}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = item.path,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.padding(4.dp))

            if (item.element.attributes.isEmpty()) {
                Text(
                    text = "No attributes on this element.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                item.element.attributes.forEach { (key, current) ->
                    AttributeEditor(
                        key = key,
                        value = current,
                        onValueChange = { newValue -> onUpdateAttribute(key, newValue) }
                    )
                    Spacer(modifier = Modifier.padding(2.dp))
                }
            }

            if (item.path != "root") {
                Spacer(modifier = Modifier.padding(4.dp))
                Button(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Delete Element")
                }
            } else {
                Text(
                    text = "Root element cannot be deleted.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AttributeEditor(
    key: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    var fieldValue by remember(value) { mutableStateOf(value) }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { fieldValue = it },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        label = { Text(key) },
        singleLine = true,
        trailingIcon = {
            if (fieldValue != value) {
                IconButton(onClick = { onValueChange(fieldValue) }) {
                    Icon(Icons.Filled.Search, contentDescription = "Apply")
                }
            }
        }
    )
}

private fun buildSummary(element: UiElement): String {
    val parts = mutableListOf<String>()
    element.attributes["text"]?.let { parts.add("text=\"$it\"") }
    element.modifier.fillMaxWidth.let { if (it) parts.add("fillMaxW") }
    element.modifier.width?.let { parts.add("w=${it}dp") }
    element.modifier.height?.let { parts.add("h=${it}dp") }
    element.modifier.background?.let { parts.add("bg=$it") }
    if (element.children.isNotEmpty()) parts.add("${element.children.size} children")
    return parts.joinToString(" ")
}
