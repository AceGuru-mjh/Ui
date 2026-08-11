package com.foundry.preview.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.drawToBitmap
import com.foundry.preview.engine.DiagnosticsEngine
import com.foundry.preview.engine.RendererManager
import com.foundry.preview.engine.XmlDirectPreview
import com.foundry.preview.sandbox.PreviewSurface
import com.foundry.preview.state.FoundryViewModel
import com.foundry.preview.state.RenderMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalGraphicsApi::class)
@Composable
fun PreviewScreen(viewModel: FoundryViewModel) {
    val diagnostics by viewModel.diagnostics.collectAsState()
    val renderMode by viewModel.renderMode.collectAsState()
    val xmlContent by viewModel.xmlContent.collectAsState()
    val uiGraph by viewModel.uiGraph.collectAsState()
    val devicePreset by viewModel.devicePreset.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val rendererBitmap by viewModel.rendererBitmap.collectAsState()
    val rendererDiagnostics by viewModel.rendererDiagnostics.collectAsState()
    val isRendererBusy by viewModel.isRendererBusy.collectAsState()
    val rendererMemoryMb by viewModel.rendererMemoryMb.collectAsState()
    val (width, height) = viewModel.getDeviceDimensions()
    val context = LocalContext.current
    val view = LocalView.current
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val pngExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    val imageBitmap = graphicsLayer.toImageBitmap()
                    val bitmap = imageBitmap.asAndroidBitmap()
                    viewModel.exportPngToUri(context, it, bitmap)
                } catch (e: Exception) {
                    val bitmap = view.drawToBitmap()
                    viewModel.exportPngToUri(context, it, bitmap)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
            .verticalScroll(scrollState)
    ) {
        // ── 标题行 ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Preview",
                style = MaterialTheme.typography.titleMedium
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.zoomOut() }) {
                    Icon(Icons.Filled.Remove, contentDescription = "Zoom Out")
                }
                Text(
                    text = "${(zoomLevel * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
                IconButton(onClick = { viewModel.zoomIn() }) {
                    Icon(Icons.Filled.Add, contentDescription = "Zoom In")
                }
                TextButton(onClick = { viewModel.resetZoom() }) {
                    Text("Reset", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Device:",
                modifier = Modifier.align(Alignment.CenterVertically),
                style = MaterialTheme.typography.bodySmall
            )
            FilterChip(
                selected = devicePreset == "Small Phone",
                onClick = { viewModel.setDevicePreset("Small Phone") },
                label = { Text("Small") }
            )
            FilterChip(
                selected = devicePreset == "Pixel 7",
                onClick = { viewModel.setDevicePreset("Pixel 7") },
                label = { Text("Phone") }
            )
            FilterChip(
                selected = devicePreset == "Pixel Tablet",
                onClick = { viewModel.setDevicePreset("Pixel Tablet") },
                label = { Text("Tablet") }
            )
        }

        // 平台元数据
        uiGraph?.meta?.let { meta ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Plugin: ${meta.parser}", style = MaterialTheme.typography.labelSmall)
                Text(text = "Level: ${meta.previewLevel.name}", style = MaterialTheme.typography.labelSmall)
                Text(text = "Confidence: ${meta.confidence.name}", style = MaterialTheme.typography.labelSmall)
            }
        }

        val plugins = RendererManager.registeredPlugins()
        if (plugins.isNotEmpty()) {
            Text(
                text = "Registered plugins: ${plugins.joinToString { it.descriptor.displayName }}",
                style = MaterialTheme.typography.labelSmall
            )
        }

        OutlinedButton(
            onClick = { viewModel.render() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("Refresh Preview")
        }

        IconButton(onClick = { pngExportLauncher.launch("preview.png") }) {
            Icon(Icons.Filled.Save, contentDescription = "Export PNG")
        }

        // ── 原生预览区域（Compose Canvas 渲染） ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height((height * zoomLevel * 0.6f).dp.coerceAtLeast(200.dp))
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.graphicsLayer(
                    scaleX = zoomLevel * 0.6f,
                    scaleY = zoomLevel * 0.6f
                )
            ) {
                Box(
                    modifier = Modifier.drawWithContent {
                        graphicsLayer.record {
                            this@drawWithContent.drawContent()
                        }
                        drawContent()
                    }
                ) {
                    when (renderMode) {
                        RenderMode.JSON_DSL -> {
                            PreviewSurface(
                                graph = uiGraph,
                                deviceWidth = width,
                                deviceHeight = height,
                                diagnostics = diagnostics
                            )
                        }
                        RenderMode.XML_DIRECT -> {
                            XmlDirectPreview(
                                xmlContent = xmlContent,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // ── 多进程沙箱渲染面板 ──
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        Text(
            text = "Sandbox Renderer (:renderer process)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // 内存占用指示
        if (rendererMemoryMb >= 0) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Icon(
                    Icons.Filled.Memory,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "Renderer memory: ${rendererMemoryMb}MB",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 渲染按钮
            OutlinedButton(
                onClick = { viewModel.renderViaIpc(context) },
                enabled = !isRendererBusy,
                modifier = Modifier.weight(1f)
            ) {
                if (isRendererBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(if (isRendererBusy) "Rendering..." else "Render via Sandbox")
            }
        }

        // 诊断输出
        if (rendererDiagnostics.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Text(
                    text = rendererDiagnostics,
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 16.sp
                )
            }
        }

        // 渲染结果位图
        rendererBitmap?.let { bmp ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Column {
                    Text(
                        text = "Sandbox Output",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(12.dp, 8.dp)
                    )
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "Renderer output",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}
