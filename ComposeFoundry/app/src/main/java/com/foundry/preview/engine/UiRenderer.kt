package com.foundry.preview.engine

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.TabRow
import androidx.compose.material3.Tab
import com.foundry.preview.dsl.PaddingSpec
import com.foundry.preview.dsl.UiElement
import com.foundry.preview.dsl.UiModifierSpec
import com.foundry.preview.dsl.parseColor
import com.foundry.gradient.GradientParser
import com.foundry.gradient.GradientBrushBuilder
import com.foundry.animation.AnimationParser
import com.foundry.animation.AnimatedWrapper
import com.foundry.animation.FoundryAnimationType

@Composable
fun RenderElement(
    element: UiElement,
    diagnostics: DiagnosticsEngine,
    path: String = "root"
) {
    val animSpec = AnimationParser.parseFromAttributes(element.attributes)

    if (animSpec.type != FoundryAnimationType.NONE) {
        AnimatedWrapper(spec = animSpec) {
            RenderElementContent(element, diagnostics, path)
        }
    } else {
        RenderElementContent(element, diagnostics, path)
    }
}

@Composable
private fun RenderElementContent(
    element: UiElement,
    diagnostics: DiagnosticsEngine,
    path: String
) {
    val modifier = buildModifier(element.modifier)
    val renderer = ComponentRegistry.get(element.type)
    if (renderer != null) {
        renderer.Render(element, modifier, diagnostics, path)
    } else {
        diagnostics.addWarning("Unknown element type: '${element.type}'", path)
        Box(
            modifier = modifier
                .background(Color(0xFFFFEBEE), RoundedCornerShape(4.dp))
                .padding(8.dp)
        ) {
            Text(
                text = "Unknown: ${element.type}",
                color = Color.Red,
                fontSize = 12.sp
            )
        }
    }
}

fun buildModifier(spec: UiModifierSpec): Modifier {
    var modifier: Modifier = Modifier

    if (spec.fillMaxSize) {
        modifier = modifier.fillMaxSize()
    } else {
        if (spec.fillMaxWidth) modifier = modifier.fillMaxWidth()
        if (spec.fillMaxHeight) modifier = modifier.fillMaxHeight()
    }

    if (spec.width != null && spec.height != null) {
        modifier = modifier.size(spec.width.dp, spec.height.dp)
    } else if (spec.width != null) {
        modifier = modifier.width(spec.width.dp)
    } else if (spec.height != null) {
        modifier = modifier.height(spec.height.dp)
    }

    modifier = applyPadding(modifier, spec.padding)

    spec.elevation?.let { e ->
        modifier = modifier.shadow(e.dp)
    }

    spec.background?.let { bg ->
        val radius = spec.cornerRadius ?: 0f
        if (GradientParser.isGradient(bg)) {
            val gradientConfig = GradientParser.parse(bg)
            val brush = GradientBrushBuilder.buildBrush(gradientConfig)
            if (radius > 0f) {
                modifier = modifier
                    .clip(RoundedCornerShape(radius.dp))
                    .background(brush, RoundedCornerShape(radius.dp))
            } else {
                modifier = modifier.background(brush)
            }
        } else {
            val color = parseColor(bg)
            if (radius > 0f) {
                modifier = modifier
                    .clip(RoundedCornerShape(radius.dp))
                    .background(color, RoundedCornerShape(radius.dp))
            } else {
                modifier = modifier.background(color)
            }
        }
    }

    if (spec.background == null && spec.cornerRadius != null && spec.cornerRadius > 0f) {
        modifier = modifier.clip(RoundedCornerShape(spec.cornerRadius.dp))
    }

    if (spec.borderWidth != null && spec.borderWidth > 0f) {
        val borderColor = spec.borderColor?.let { parseColor(it) } ?: Color.Gray
        val radius = spec.cornerRadius ?: 0f
        modifier = modifier.border(
            width = spec.borderWidth.dp,
            color = borderColor,
            shape = if (radius > 0f) RoundedCornerShape(radius.dp) else RoundedCornerShape(0.dp)
        )
    }

    return modifier
}

private fun applyPadding(modifier: Modifier, padding: PaddingSpec?): Modifier {
    if (padding == null) return modifier
    val start = padding.start ?: padding.horizontal ?: padding.all ?: 0f
    val top = padding.top ?: padding.vertical ?: padding.all ?: 0f
    val end = padding.end ?: padding.horizontal ?: padding.all ?: 0f
    val bottom = padding.bottom ?: padding.vertical ?: padding.all ?: 0f
    return modifier.padding(start = start.dp, top = top.dp, end = end.dp, bottom = bottom.dp)
}



