package com.foundry.preview.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 验证所有内置渲染器都已注册，避免预览中出现红色 Unknown 框。
 * 新增组件类型时，必须在此处登记预期，否则测试会失败。
 */
class RendererRegistryTest {

    private val expectedTypes = setOf(
        "text", "column", "row", "box", "button", "spacer", "card", "divider",
        "image", "textfield", "scroll", "surface", "lazycolumn", "switch",
        "checkbox", "slider", "progressindicator", "tabrow", "radiobutton",
        "spinner", "chip", "navigationbar", "alertdialog", "badge", "runtimeview"
    )

    @Test
    fun `all built-in renderers are registered`() {
        initializeRenderers()
        val missing = expectedTypes.filter { !ComponentRegistry.isRegistered(it) }
        assertTrue("missing renderers: $missing", missing.isEmpty())
    }
}
