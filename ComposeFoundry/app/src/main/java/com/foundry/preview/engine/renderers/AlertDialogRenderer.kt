package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

/**
 * 对话框（AlertDialog）。标题/正文取自属性，确认/取消按钮取自子节点 action。
 * 预览中对话框以静态卡片形式呈现（交互点击在预览中无意义）。
 */
class AlertDialogRenderer : ComponentRenderer {
    override val type = "alertdialog"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val title = element.attributes["title"] ?: element.attributes["text"] ?: "Dialog"
        val message = element.attributes["message"] ?: element.attributes["body"] ?: ""
        val confirm = element.attributes["confirmText"] ?: "OK"
        val dismiss = element.attributes["dismissText"]
        AlertDialog(
            onDismissRequest = { },
            confirmButton = {
                TextButton(onClick = { }) { Text(confirm) }
            },
            dismissButton = if (dismiss != null) {
                { TextButton(onClick = { }) { Text(dismiss) } }
            } else null,
            title = { Text(title) },
            text = if (message.isNotBlank()) {
                { Text(message) }
            } else null,
            modifier = modifier.fillMaxWidth().padding(8.dp)
        )
    }
}
