package com.foundry.preview.engine.renderers

import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.engine.ComponentRenderer
import com.foundry.preview.engine.DiagnosticsEngine

class SliderRenderer : ComponentRenderer {
    override val type = "slider"

    @Composable
    override fun Render(element: UiElement, modifier: Modifier, diagnostics: DiagnosticsEngine, path: String) {
        var sliderValue by remember {
            mutableStateOf(element.attributes["value"]?.toFloatOrNull() ?: 0.5f)
        }
        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            valueRange = 0f..1f,
            modifier = modifier
        )
    }
}
