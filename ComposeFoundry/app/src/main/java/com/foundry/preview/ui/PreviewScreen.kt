package com.foundry.preview.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ExperimentalGraphicsApi
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.drawToBitmap
import com.foundry.preview.sandbox.PreviewSurface
import com.foundry.preview.state.FoundryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalGraphicsApi::class)
@Composable
fun PreviewScreen(viewModel: FoundryViewModel) {
    val document by viewModel.document.collectAsState()
    val diagnostics by viewModel.diagnostics.collectAsState()
    val devicePreset by viewModel.devicePreset.collectAsState()
    val zoomLevel by viewModel.zoomLevel.collectAsState()
    val (width, height) = viewModel.getDeviceDimensions()
    val context = LocalContext.current
    val view = LocalView.current
    val graphicsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

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
    ) {
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

        OutlinedButton(
            onClick = { viewModel.render() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("Refresh Preview")
        }

        IconButton(onClick = { pngExportLauncher.launch("preview.png") }) {
            Icon(Icons.Filled.Save, contentDescription = "Export PNG")
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier.graphicsLayer(
                    scaleX = zoomLevel,
                    scaleY = zoomLevel
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
                    PreviewSurface(
                        document = document,
                        deviceWidth = width,
                        deviceHeight = height,
                        diagnostics = diagnostics
                    )
                }
            }
        }
    }
}
