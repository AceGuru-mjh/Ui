package com.foundry.preview.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.foundry.core.plugin.PluginDescriptor
import com.foundry.core.plugin.RemotePluginManifest
import com.foundry.preview.state.FoundryViewModel

/**
 * 插件市场（下载中心）：展示云端仓库索引中的可用插件，支持按需下载/热更新，
 * 并列出当前已注册的插件（内置 + 动态加载）。对应文章第一、二节的“动态插件市场”。
 */
@Composable
fun MarketplaceScreen(viewModel: FoundryViewModel) {
    val context = LocalContext.current
    val repository by viewModel.pluginRepository.collectAsState()
    val installed by viewModel.installedPlugins.collectAsState()
    val installing by viewModel.installingPluginIds.collectAsState()

    val openLocalLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.installPluginFromUri(context, it) } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("插件市场", style = MaterialTheme.typography.titleLarge)
                Button(onClick = { openLocalLauncher.launch(arrayOf("application/octet-stream", "*/*")) }) {
                    Text("安装本地插件")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "主程序仅保留核心，UI 库/解析器/渲染器按需从云端下载并热加载。",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))
        }

        val plugins = repository?.plugins ?: emptyList()
        if (plugins.isEmpty()) {
            item { Text("暂无可用插件（仓库索引为空或未加载）。") }
        }
        items(plugins) { manifest ->
            PluginCard(
                manifest = manifest,
                installedVersion = installed.firstOrNull { it.id == manifest.id }?.version,
                isInstalling = installing.contains(manifest.id),
                onInstall = { viewModel.installPlugin(context, manifest) }
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            Text("已注册插件（内置 + 动态加载）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
        }
        items(installed) { desc -> InstalledRow(desc) }
    }
}

@Composable
private fun PluginCard(
    manifest: RemotePluginManifest,
    installedVersion: String?,
    isInstalling: Boolean,
    onInstall: () -> Unit
) {
    val hasUpdate = installedVersion != null && installedVersion != manifest.version
    val buttonText = when {
        isInstalling -> "安装中…"
        installedVersion == null -> "下载并安装"
        hasUpdate -> "更新"
        else -> "已安装"
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(manifest.displayName, style = MaterialTheme.typography.titleMedium)
            Text("v${manifest.version}  •  ${manifest.id}", style = MaterialTheme.typography.bodySmall)
            if (!manifest.description.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(manifest.description, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onInstall, enabled = !isInstalling && (installedVersion == null || hasUpdate)) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun InstalledRow(desc: PluginDescriptor) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                "• ${desc.displayName}  v${desc.version}  (${desc.id})",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
