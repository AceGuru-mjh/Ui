package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

/**
 * 搜索栏（SearchBar）。预览中以非激活态静态呈现，placeholder 取自属性。
 */
class SearchBarRenderer : ComponentRenderer {
    override val type = "searchbar"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val placeholder = element.attributes["placeholder"] ?: element.attributes["hint"] ?: "Search"
        SearchBar(
            query = element.attributes["query"] ?: "",
            onQueryChange = { },
            onSearch = { },
            active = false,
            onActiveChange = { },
            modifier = modifier.fillMaxWidth()
        ) {
            Text(placeholder)
        }
    }
}
