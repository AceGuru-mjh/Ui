package com.foundry.preview.engine.renderers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.RangeSlider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine
import kotlin.ranges.ClosedFloatingPointRange

/**
 * 双滑块区间选择器（RangeSlider）。默认区间 0.2..0.8，可由 from/to 属性覆盖。
 */
class RangeSliderRenderer : ComponentRenderer {
    override val type = "rangeslider"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        val from = element.attributes["from"]?.toFloatOrNull() ?: 0.2f
        val to = element.attributes["to"]?.toFloatOrNull() ?: 0.8f
        var range: ClosedFloatingPointRange<Float> by remember { mutableStateOf(from..to) }
        RangeSlider(
            value = range,
            onValueChange = { range = it },
            valueRange = 0f..1f,
            modifier = modifier.fillMaxWidth()
        )
    }
}
