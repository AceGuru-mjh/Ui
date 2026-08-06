package com.foundry.preview.sandbox

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.foundry.preview.dsl.ThemeConfig
import com.foundry.preview.dsl.parseColor

@Composable
fun FoundryTheme(
    themeConfig: ThemeConfig,
    isDarkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val primaryColor = parseColor(themeConfig.primaryColor)
    val backgroundColor = parseColor(themeConfig.backgroundColor)
    val surfaceColor = parseColor(themeConfig.surfaceColor)
    val textColor = parseColor(themeConfig.textColor)

    val colorScheme = if (isDarkTheme) {
        darkColorScheme(
            primary = primaryColor,
            background = backgroundColor,
            surface = surfaceColor,
            onBackground = textColor,
            onSurface = textColor
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            background = backgroundColor,
            surface = surfaceColor,
            onBackground = textColor,
            onSurface = textColor
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
