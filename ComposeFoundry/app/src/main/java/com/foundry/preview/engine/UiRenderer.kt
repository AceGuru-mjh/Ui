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
    val typeLower = element.type.lowercase()

    when (typeLower) {
        "column" -> RenderColumn(element, modifier, diagnostics, path)
        "row" -> RenderRow(element, modifier, diagnostics, path)
        "box" -> RenderBox(element, modifier, diagnostics, path)
        "text" -> RenderText(element, modifier)
        "button" -> RenderButton(element, modifier)
        "spacer" -> Spacer(modifier = modifier)
        "card" -> RenderCard(element, modifier, diagnostics, path)
        "divider" -> HorizontalDivider(modifier = modifier)
        "image" -> RenderImagePlaceholder(modifier, element)
        "textfield" -> RenderTextField(element, modifier)
        "scroll" -> RenderScroll(element, modifier, diagnostics, path)
        "surface" -> RenderSurface(element, modifier, diagnostics, path)
        "lazycolumn" -> RenderLazyColumn(element, modifier, diagnostics, path)
        "switch" -> RenderSwitch(element, modifier)
        "checkbox" -> RenderCheckbox(element, modifier)
        "slider" -> RenderSliderComponent(element, modifier)
        "progressindicator" -> RenderProgressIndicator(element, modifier)
        "tabrow" -> RenderTabRow(element, modifier, diagnostics, path)
        else -> {
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

@Composable
private fun RenderColumn(
    element: UiElement,
    modifier: Modifier,
    diagnostics: DiagnosticsEngine,
    path: String
) {
    val horizontalAlignment = when (element.modifier.horizontalAlignment?.lowercase()) {
        "center", "centerhorizontally" -> Alignment.CenterHorizontally
        "end", "right" -> Alignment.End
        else -> Alignment.Start
    }
    val verticalArrangement = when (element.modifier.verticalArrangement?.lowercase()) {
        "center" -> Arrangement.Center
        "end", "bottom" -> Arrangement.Bottom
        "spacebetween" -> Arrangement.SpaceBetween
        "spacearound" -> Arrangement.SpaceAround
        "spaceevenly" -> Arrangement.SpaceEvenly
        else -> Arrangement.Top
    }

    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = verticalArrangement
    ) {
        element.children.forEachIndexed { index, child ->
            RenderElement(child, diagnostics, "$path.children[$index]")
        }
    }
}

@Composable
private fun RenderRow(
    element: UiElement,
    modifier: Modifier,
    diagnostics: DiagnosticsEngine,
    path: String
) {
    val verticalAlignment = when (element.modifier.verticalAlignment?.lowercase()) {
        "center", "centervertically" -> Alignment.CenterVertically
        "bottom" -> Alignment.Bottom
        else -> Alignment.Top
    }
    val horizontalArrangement = when (element.modifier.horizontalArrangement?.lowercase()) {
        "center" -> Arrangement.Center
        "end", "right" -> Arrangement.End
        "spacebetween" -> Arrangement.SpaceBetween
        "spacearound" -> Arrangement.SpaceAround
        "spaceevenly" -> Arrangement.SpaceEvenly
        else -> Arrangement.Start
    }

    Row(
        modifier = modifier,
        verticalAlignment = verticalAlignment,
        horizontalArrangement = horizontalArrangement
    ) {
        element.children.forEachIndexed { index, child ->
            RenderElement(child, diagnostics, "$path.children[$index]")
        }
    }
}

@Composable
private fun RenderBox(
    element: UiElement,
    modifier: Modifier,
    diagnostics: DiagnosticsEngine,
    path: String
) {
    val contentAlignment = when (element.modifier.contentAlignment?.lowercase()) {
        "center" -> Alignment.Center
        "topstart" -> Alignment.TopStart
        "topcenter" -> Alignment.TopCenter
        "topend" -> Alignment.TopEnd
        "centerstart" -> Alignment.CenterStart
        "centerend" -> Alignment.CenterEnd
        "bottomstart" -> Alignment.BottomStart
        "bottomcenter" -> Alignment.BottomCenter
        "bottomend" -> Alignment.BottomEnd
        else -> Alignment.TopStart
    }

    Box(modifier = modifier, contentAlignment = contentAlignment) {
        element.children.forEachIndexed { index, child ->
            RenderElement(child, diagnostics, "$path.children[$index]")
        }
    }
}

@Composable
private fun RenderText(element: UiElement, modifier: Modifier) {
    val text = element.attributes["text"] ?: ""
    val fontSize = element.attributes["fontSize"]?.toFloatOrNull() ?: 14f
    val colorStr = element.attributes["color"]
    val fontWeight = when (element.attributes["fontWeight"]?.lowercase()) {
        "bold" -> FontWeight.Bold
        "medium" -> FontWeight.Medium
        "light" -> FontWeight.Light
        "thin" -> FontWeight.Thin
        else -> FontWeight.Normal
    }
    val textAlign = when (element.attributes["textAlign"]?.lowercase()) {
        "center" -> TextAlign.Center
        "end", "right" -> TextAlign.End
        else -> TextAlign.Start
    }

    Text(
        text = text,
        fontSize = fontSize.sp,
        fontWeight = fontWeight,
        textAlign = textAlign,
        color = colorStr?.let { parseColor(it) } ?: MaterialTheme.colorScheme.onBackground,
        modifier = modifier
    )
}

@Composable
private fun RenderButton(element: UiElement, modifier: Modifier) {
    val text = element.attributes["text"] ?: "Button"
    Button(onClick = { }, modifier = modifier) {
        Text(text = text)
    }
}

@Composable
private fun RenderCard(
    element: UiElement,
    modifier: Modifier,
    diagnostics: DiagnosticsEngine,
    path: String
) {
    val radius = element.modifier.cornerRadius ?: 12f
    val elevation = element.modifier.elevation ?: 2f

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(radius.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation.dp)
    ) {
        element.children.forEachIndexed { index, child ->
            RenderElement(child, diagnostics, "$path.children[$index]")
        }
    }
}

@Composable
private fun RenderImagePlaceholder(modifier: Modifier, element: UiElement) {
    val label = element.attributes["contentDescription"] ?: "Image"
    Box(
        modifier = modifier
            .background(Color(0xFFE0E0E0), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(text = label, fontSize = 14.sp, color = Color.Gray)
    }
}

@Composable
private fun RenderTextField(element: UiElement, modifier: Modifier) {
    val label = element.attributes["label"] ?: ""
    val placeholder = element.attributes["placeholder"] ?: ""
    var text by remember { mutableStateOf(element.attributes["value"] ?: "") }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = if (label.isNotEmpty()) {
            { Text(label) }
        } else null,
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder) }
        } else null,
        modifier = modifier
    )
}

@Composable
private fun RenderScroll(
    element: UiElement,
    modifier: Modifier,
    diagnostics: DiagnosticsEngine,
    path: String
) {
    val scrollState = rememberScrollState()
    Box(modifier = modifier.verticalScroll(scrollState)) {
        element.children.forEachIndexed { index, child ->
            RenderElement(child, diagnostics, "$path.children[$index]")
        }
    }
}

@Composable
private fun RenderSurface(
    element: UiElement,
    modifier: Modifier,
    diagnostics: DiagnosticsEngine,
    path: String
) {
    val radius = element.modifier.cornerRadius ?: 0f
    Surface(
        modifier = modifier,
        shape = if (radius > 0f) RoundedCornerShape(radius.dp) else RoundedCornerShape(0.dp),
        color = element.modifier.background?.let { parseColor(it) }
            ?: MaterialTheme.colorScheme.surface
    ) {
        element.children.forEachIndexed { index, child ->
            RenderElement(child, diagnostics, "$path.children[$index]")
        }
    }
}

@Composable
private fun RenderLazyColumn(
    element: UiElement,
    modifier: Modifier,
    diagnostics: DiagnosticsEngine,
    path: String
) {
    LazyColumn(modifier = modifier) {
        items(element.children.size) { index ->
            RenderElement(element.children[index], diagnostics, "$path.children[$index]")
        }
    }
}

@Composable
private fun RenderSwitch(element: UiElement, modifier: Modifier) {
    val label = element.attributes["label"] ?: ""
    var checked by remember { mutableStateOf(element.attributes["checked"]?.toBoolean() ?: false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (label.isNotEmpty()) {
            Text(text = label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        }
        Switch(checked = checked, onCheckedChange = { checked = it })
    }
}

@Composable
private fun RenderCheckbox(element: UiElement, modifier: Modifier) {
    val label = element.attributes["label"] ?: ""
    var checked by remember { mutableStateOf(element.attributes["checked"]?.toBoolean() ?: false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { checked = it })
        if (label.isNotEmpty()) {
            Text(text = label, fontSize = 14.sp)
        }
    }
}

@Composable
private fun RenderSliderComponent(element: UiElement, modifier: Modifier) {
    val min = element.attributes["min"]?.toFloatOrNull() ?: 0f
    val max = element.attributes["max"]?.toFloatOrNull() ?: 100f
    var value by remember { mutableStateOf(element.attributes["value"]?.toFloatOrNull() ?: min) }
    Column(modifier = modifier) {
        Slider(
            value = value,
            onValueChange = { value = it },
            valueRange = min..max
        )
        Text(text = "${value.toInt()}", fontSize = 10.sp, color = Color.Gray)
    }
}

@Composable
private fun RenderProgressIndicator(element: UiElement, modifier: Modifier) {
    val progress = element.attributes["progress"]?.toFloatOrNull()
    if (progress != null) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = modifier
        )
    } else {
        LinearProgressIndicator(modifier = modifier)
    }
}

@Composable
private fun RenderTabRow(
    element: UiElement,
    modifier: Modifier,
    diagnostics: DiagnosticsEngine,
    path: String
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = element.children
    if (tabs.isEmpty()) return

    TabRow(
        selectedTabIndex = selectedTabIndex.coerceIn(0, tabs.size - 1),
        modifier = modifier
    ) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = selectedTabIndex == index,
                onClick = { selectedTabIndex = index },
                text = {
                    Text(tab.attributes["text"] ?: "Tab ${index + 1}")
                }
            )
        }
    }

    if (selectedTabIndex < tabs.size && tabs[selectedTabIndex].children.isNotEmpty()) {
        tabs[selectedTabIndex].children.forEachIndexed { index, child ->
            RenderElement(child, diagnostics, "$path.children[$selectedTabIndex].children[$index]")
        }
    }
}
