package com.foundry.gradient

/**
 * 渐变 DSL 解析器。
 *
 * 支持语法：
 *   纯色:     "#FF6200EE"
 *   线性渐变: "linear(45deg, #FF6200EE 0%, #FF00BCD4 100%)"
 *   径向渐变: "radial(center=0.5,0.5, #FF6200EE 0%, #FF00BCD4 100%)"
 *   锥形渐变: "conic(0deg, #F00 0%, #0F0 50%, #00F 100%)"
 *   预设渐变: "gradient:sunset", "gradient:ocean", "gradient:aurora"
 */
object GradientParser {

    private val PRESET_GRADIENTS = mapOf(
        "sunset" to GradientConfig(
            type = GradientType.LINEAR, angleDegrees = 135f,
            stops = listOf(
                GradientStop("#FFFF512F", 0f),
                GradientStop("#FFF09819", 0.5f),
                GradientStop("#FFDD2476", 1f)
            )
        ),
        "ocean" to GradientConfig(
            type = GradientType.LINEAR, angleDegrees = 180f,
            stops = listOf(
                GradientStop("#FF2E3192", 0f),
                GradientStop("#FF1BFFFF", 1f)
            )
        ),
        "aurora" to GradientConfig(
            type = GradientType.LINEAR, angleDegrees = 45f,
            stops = listOf(
                GradientStop("#FF00C9FF", 0f),
                GradientStop("#FF92FE9D", 0.5f),
                GradientStop("#FF00C9FF", 1f)
            )
        ),
        "fire" to GradientConfig(
            type = GradientType.LINEAR, angleDegrees = 0f,
            stops = listOf(
                GradientStop("#FFF83600", 0f),
                GradientStop("#FFF9D423", 1f)
            )
        ),
        "midnight" to GradientConfig(
            type = GradientType.LINEAR, angleDegrees = 135f,
            stops = listOf(
                GradientStop("#FF232526", 0f),
                GradientStop("#FF414345", 1f)
            )
        ),
        "candy" to GradientConfig(
            type = GradientType.LINEAR, angleDegrees = 90f,
            stops = listOf(
                GradientStop("#FFD4145A", 0f),
                GradientStop("#FF0ED2F7", 1f)
            )
        ),
        "forest" to GradientConfig(
            type = GradientType.LINEAR, angleDegrees = 180f,
            stops = listOf(
                GradientStop("#FF134E5E", 0f),
                GradientStop("#FF71B280", 1f)
            )
        ),
        "royal" to GradientConfig(
            type = GradientType.LINEAR, angleDegrees = 45f,
            stops = listOf(
                GradientStop("#FF141E30", 0f),
                GradientStop("#FF243B55", 1f)
            )
        )
    )

    /**
     * 判断字符串是否为渐变语法（非纯色）。
     */
    fun isGradient(str: String): Boolean {
        val t = str.trim()
        return t.startsWith("gradient:") ||
               t.startsWith("linear(") ||
               t.startsWith("radial(") ||
               t.startsWith("conic(")
    }

    /**
     * 解析 background 字符串为 GradientConfig。
     * 非渐变语法返回 SOLID 类型。
     */
    fun parse(backgroundStr: String): GradientConfig {
        val trimmed = backgroundStr.trim()

        if (trimmed.startsWith("gradient:")) {
            val name = trimmed.removePrefix("gradient:").lowercase()
            return PRESET_GRADIENTS[name] ?: GradientConfig(
                type = GradientType.SOLID,
                stops = listOf(GradientStop(trimmed, 0f))
            )
        }
        if (trimmed.startsWith("linear(")) return parseLinear(trimmed)
        if (trimmed.startsWith("radial(")) return parseRadial(trimmed)
        if (trimmed.startsWith("conic(")) return parseConic(trimmed)

        return GradientConfig(
            type = GradientType.SOLID,
            stops = listOf(GradientStop(trimmed, 0f))
        )
    }

    private fun parseLinear(str: String): GradientConfig {
        val content = str.removePrefix("linear(").removeSuffix(")")
        val parts = content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return GradientConfig(type = GradientType.LINEAR)

        var angle = 0f
        val stops = mutableListOf<GradientStop>()
        for (part in parts) {
            if (part.endsWith("deg") && !part.contains("#")) {
                angle = part.removeSuffix("deg").toFloatOrNull() ?: 0f
            } else {
                parseStop(part)?.let { stops.add(it) }
            }
        }

        if (stops.isNotEmpty() && stops.all { it.offset == 0f }) {
            val step = 1f / (stops.size - 1).coerceAtLeast(1)
            return GradientConfig(
                type = GradientType.LINEAR, angleDegrees = angle,
                stops = stops.mapIndexed { i, s -> s.copy(offset = i * step) }
            )
        }
        return GradientConfig(type = GradientType.LINEAR, angleDegrees = angle, stops = stops)
    }

    private fun parseRadial(str: String): GradientConfig {
        val content = str.removePrefix("radial(").removeSuffix(")")
        val parts = content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return GradientConfig(type = GradientType.RADIAL)

        var cx = 0.5f; var cy = 0.5f; var radius: Float? = null
        val stops = mutableListOf<GradientStop>()
        var i = 0
        while (i < parts.size) {
            val part = parts[i]
            when {
                part.startsWith("center=") -> {
                    val xRaw = part.removePrefix("center=")
                    // center coords are comma-separated, but the top-level split above
                    // already separated "center=x" and "y" into two parts, so the y
                    // coordinate is the next part when xRaw has no comma of its own.
                    val xCoords = xRaw.split(",")
                    if (xCoords.size >= 2) {
                        cx = xCoords[0].toFloatOrNull() ?: 0.5f
                        cy = xCoords[1].toFloatOrNull() ?: 0.5f
                    } else if (i + 1 < parts.size) {
                        cx = xRaw.toFloatOrNull() ?: 0.5f
                        cy = parts[i + 1].toFloatOrNull() ?: 0.5f
                        i++ // consume the y part
                    }
                }
                part.startsWith("radius=") -> radius = part.removePrefix("radius=").toFloatOrNull()
                else -> parseStop(part)?.let { stops.add(it) }
            }
            i++
        }
        return GradientConfig(type = GradientType.RADIAL, centerX = cx, centerY = cy, radius = radius, stops = stops)
    }

    private fun parseConic(str: String): GradientConfig {
        val content = str.removePrefix("conic(").removeSuffix(")")
        val parts = content.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return GradientConfig(type = GradientType.CONIC)

        var angle = 0f
        val stops = mutableListOf<GradientStop>()
        for (part in parts) {
            if (part.endsWith("deg") && !part.contains("#")) {
                angle = part.removeSuffix("deg").toFloatOrNull() ?: 0f
            } else {
                parseStop(part)?.let { stops.add(it) }
            }
        }
        return GradientConfig(type = GradientType.CONIC, angleDegrees = angle, stops = stops)
    }

    private fun parseStop(str: String): GradientStop? {
        val trimmed = str.trim()
        val spaceIdx = trimmed.lastIndexOf(' ')
        if (spaceIdx > 0 && trimmed.substring(spaceIdx + 1).endsWith("%")) {
            val color = trimmed.substring(0, spaceIdx).trim()
            val offset = trimmed.substring(spaceIdx + 1).removeSuffix("%").toFloatOrNull()?.div(100f) ?: 0f
            return GradientStop(color, offset)
        }
        return if (trimmed.startsWith("#")) GradientStop(trimmed, 0f) else null
    }
}
