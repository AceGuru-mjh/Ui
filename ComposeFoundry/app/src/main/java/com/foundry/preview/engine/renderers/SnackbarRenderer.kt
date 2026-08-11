package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

/**
 * 轻量提示条（Snackbar）。message 取自属性；actionText 非空则显示操作按钮。
 * 预览中以静态形式呈现（交互 dismiss 在预览中无意义）。
 */
class SnackbarRenderer : ComponentRenderer {
    override val type = "snackbar"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val message = element.attributes["message"] ?: element.attributes["text"] ?: "Snackbar"
        val actionText = element.attributes["actionText"] ?: element.attributes["action"]
        Snackbar(
            modifier = modifier.fillMaxWidth().padding(8.dp),
            action = if (actionText != null) {
                { TextButton(onClick = { }) { Text(actionText) } }
            } else null,
            containerColor = SnackbarDefaults.color
        ) {
            Text(message)
        }
    }
}
