package com.foundry.preview.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.ThemeConfig
import com.foundry.preview.sandbox.FoundryTheme
import com.foundry.preview.state.FoundryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: FoundryViewModel) {
    val context = LocalContext.current
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val document by viewModel.document.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val openJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importJsonFromUri(context, it) }
    }

    val openXmlLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importXmlFromUri(context, it) }
    }

    val saveJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { viewModel.exportJsonToUri(context, it) }
    }

    LaunchedEffect(statusMessage) {
        if (statusMessage.isNotEmpty()) {
            snackbarHostState.showSnackbar(statusMessage)
            viewModel.clearStatus()
        }
    }

    FoundryTheme(
        themeConfig = document?.theme ?: ThemeConfig(),
        isDarkTheme = isDarkTheme
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("ComposeFoundry") },
                        actions = {
                            TextButton(onClick = { openJsonLauncher.launch(arrayOf("application/json", "*/*")) }) {
                                Text("JSON")
                            }
                            TextButton(onClick = { openXmlLauncher.launch(arrayOf("text/xml", "application/xml", "*/*")) }) {
                                Text("XML")
                            }
                            TextButton(onClick = { saveJsonLauncher.launch("preview.androidui.json") }) {
                                Text("Save")
                            }
                            TextButton(onClick = { viewModel.toggleTheme() }) {
                                Text(if (isDarkTheme) "Light" else "Dark")
                            }
                        }
                    )
                    val openDocs by viewModel.openDocuments.collectAsState()
                    val activeIdx by viewModel.activeDocIndex.collectAsState()
                    ScrollableTabRow(
                        selectedTabIndex = activeIdx,
                        edgePadding = 0.dp,
                        divider = {}
                    ) {
                        openDocs.forEachIndexed { index, doc ->
                            Tab(
                                selected = index == activeIdx,
                                onClick = { viewModel.switchDocument(index) },
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(doc.name, style = MaterialTheme.typography.bodySmall)
                                        if (openDocs.size > 1) {
                                            IconButton(onClick = { viewModel.closeDocument(index) }) {
                                                Icon(Icons.Filled.Close, contentDescription = "Close")
                                            }
                                        }
                                    }
                                }
                            )
                        }
                        Tab(
                            selected = false,
                            onClick = { viewModel.addDocument() },
                            icon = { Icon(Icons.Filled.Add, contentDescription = "New Document") }
                        )
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.selectTab(0) },
                        icon = { Icon(Icons.Filled.Edit, contentDescription = "Editor") },
                        label = { Text("Editor") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.selectTab(1) },
                        icon = { Icon(Icons.Filled.Preview, contentDescription = "Preview") },
                        label = { Text("Preview") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.selectTab(2) },
                        icon = { Icon(Icons.Filled.Widgets, contentDescription = "Components") },
                        label = { Text("Components") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 3,
                        onClick = { viewModel.selectTab(3) },
                        icon = { Icon(Icons.Filled.Search, contentDescription = "Inspect") },
                        label = { Text("Inspect") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 4,
                        onClick = { viewModel.selectTab(4) },
                        icon = { Icon(Icons.Filled.Code, contentDescription = "Generate") },
                        label = { Text("Code") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 5,
                        onClick = { viewModel.selectTab(5) },
                        icon = { Icon(Icons.Filled.Accessibility, contentDescription = "A11y") },
                        label = { Text("A11y") }
                    )
                    NavigationBarItem(
                        selected = selectedTab == 6,
                        onClick = { viewModel.selectTab(6) },
                        icon = { Icon(Icons.Filled.Folder, contentDescription = "Project") },
                        label = { Text("Project") }
                    )
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (selectedTab) {
                    0 -> EditorScreen(viewModel = viewModel)
                    1 -> PreviewScreen(viewModel = viewModel)
                    2 -> ComponentsScreen(viewModel = viewModel)
                    3 -> InspectScreen(viewModel = viewModel)
                    4 -> CodeGeneratorScreen(viewModel = viewModel)
                    5 -> AccessibilityScreen(viewModel = viewModel)
                    6 -> ProjectScreen(viewModel = viewModel)
                }
            }
        }
    }
}
