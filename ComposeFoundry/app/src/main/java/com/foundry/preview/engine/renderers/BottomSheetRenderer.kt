package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

/**
 * 底部弹层（ModalBottomSheet）。预览中以展开态静态呈现面板内容。
 */
class BottomSheetRenderer : ComponentRenderer {
    override val type = "bottomsheet"

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val title = element.attributes["title"] ?: element.attributes["text"]
        ModalBottomSheet(
            onDismissRequest = { },
            modifier = modifier
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(title ?: "Bottom Sheet")
            }
        }
    }
}
