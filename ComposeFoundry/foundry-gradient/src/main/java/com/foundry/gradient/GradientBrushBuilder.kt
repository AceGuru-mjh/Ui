package com.foundry.gradient

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode

/**
 * 将 GradientConfig 转换为 Compose Brush。
 *
 * 角度→坐标转换：
 *   start = center - cos/sin(angle) * 0.5
 *   end   = center + cos/sin(angle) * 0.5
 * 使用归一化坐标 [0,1]，Compose 自动映射到实际尺寸。
 */
object GradientBrushBuilder {

    fun buildBrush(config: GradientConfig): Brush {
        if (config.stops.isEmpty()) return Brush.linearGradient(listOf(Color.Gray, Color.Gray))

        val colors = config.stops.map { parseColor(it.color) }

        return when (config.type) {
            GradientType.SOLID -> Brush.linearGradient(listOf(colors.first(), colors.first()))

            GradientType.LINEAR -> {
                val rad = Math.toRadians(config.angleDegrees.toDouble())
                val cos = Math.cos(rad).toFloat()
                val sin = Math.sin(rad).toFloat()
                Brush.linearGradient(
                    colors = colors,
                    start = Offset(0.5f - cos * 0.5f, 0.5f - sin * 0.5f),
                    end = Offset(0.5f + cos * 0.5f, 0.5f + sin * 0.5f),
                    tileMode = parseTileMode(config.tileMode)
                )
            }

            GradientType.RADIAL -> Brush.radialGradient(
                colors = colors,
                center = Offset(config.centerX, config.centerY),
                radius = config.radius ?: Float.POSITIVE_INFINITY,
                tileMode = parseTileMode(config.tileMode)
            )

            GradientType.CONIC -> Brush.sweepGradient(
                colors = colors,
                center = Offset(config.centerX, config.centerY)
            )
        }
    }

    /**
     * 颜色解析。独立实现，不依赖 app 模块的 parseColor。
     */
    fun parseColor(colorString: String): Color {
        return try {
            Color(android.graphics.Color.parseColor(colorString))
        } catch (e: Exception) {
            Color.Gray
        }
    }

    private fun parseTileMode(mode: String): TileMode {
        return when (mode.lowercase()) {
            "repeat" -> TileMode.Repeated
            "mirror" -> TileMode.Mirrored
            else -> TileMode.Clamp
        }
    }
}
