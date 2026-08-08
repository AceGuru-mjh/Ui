package com.foundry.preview.engine

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement

/**
 * 组件渲染器接口。每个 UI 组件类型对应一个实现，
 * 通过 [ComponentRegistry] 注册，避免 UiRenderer 中 when 分发膨胀。
 */
interface ComponentRenderer {
    val type: String

    @Composable
    fun Render(
        element: UiElement,
        modifier: Modifier,
        diagnostics: DiagnosticsEngine,
        path: String
    )
}
