package com.foundry.preview.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foundry.core.plugin.ArtifactKind
import com.foundry.preview.project.ProjectIndexer
import com.foundry.preview.state.FoundryViewModel

@Composable
fun ProjectScreen(viewModel: FoundryViewModel) {
    val rootPath by viewModel.projectRootPath.collectAsState()
    val index by viewModel.projectIndex.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("项目级索引器（Stage 4 原型）", style = MaterialTheme.typography.titleLarge)
        Text(
            "扫描目录，列出可预览的 JSON DSL / Android XML Layout / Compose 源码文件。",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = rootPath,
                onValueChange = { viewModel.setProjectRootPath(it) },
                label = { Text("项目根目录路径") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            OutlinedButton(
                onClick = { viewModel.indexProject() },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("扫描")
            }
        }

        if (index == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("输入路径后点击「扫描」", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            val previewable = index!!.files.filter { it.previewable }
            Text(
                "共 ${index!!.files.size} 个文件，可预览 ${previewable.size} 个",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(previewable) { file ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { viewModel.loadProjectFile(file.path) }) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = kindColor(file.kind),
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                Text(
                                    kindLabel(file.kind),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(file.name, style = MaterialTheme.typography.bodyMedium)
                                Text(file.path, style = MaterialTheme.typography.labelSmall, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun kindLabel(kind: ArtifactKind): String = when (kind) {
    ArtifactKind.JSON_DSL -> "JSON"
    ArtifactKind.ANDROID_XML_LAYOUT -> "XML"
    ArtifactKind.KOTLIN_COMPOSE -> "KT"
    else -> "?"
}

@Composable
private fun kindColor(kind: ArtifactKind) = when (kind) {
    ArtifactKind.JSON_DSL -> MaterialTheme.colorScheme.primaryContainer
    ArtifactKind.ANDROID_XML_LAYOUT -> MaterialTheme.colorScheme.secondaryContainer
    ArtifactKind.KOTLIN_COMPOSE -> MaterialTheme.colorScheme.tertiaryContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}
