package com.foundry.preview.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundry.preview.dsl.UiDocument
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

@Composable
fun InspectScreen(viewModel: FoundryViewModel) {
    val document by viewModel.document.collectAsState()

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

    val items = flattenTree(document!!.root)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp)
    ) {
        item {
            Text(
                text = "Component Tree",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "${items.size} elements total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        items(items) { item ->
            InspectRow(item)
        }
    }
}

@Composable
private fun InspectRow(item: InspectItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Spacer(modifier = Modifier.width((item.depth * 16).dp))

            Text(
                text = item.element.type,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
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
