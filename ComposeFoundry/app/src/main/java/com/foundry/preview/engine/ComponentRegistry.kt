package com.foundry.preview.engine

/**
 * 组件渲染器注册表。类型名以小写形式作为 key，
 * 支持在运行时查询/注册渲染器，替代 when 分发。
 */
object ComponentRegistry {
    private val renderers = mutableMapOf<String, ComponentRenderer>()

    fun register(renderer: ComponentRenderer) {
        renderers[renderer.type.lowercase()] = renderer
    }

    fun get(type: String): ComponentRenderer? = renderers[type.lowercase()]

    fun isRegistered(type: String): Boolean = renderers.containsKey(type.lowercase())

    fun allTypes(): Set<String> = renderers.keys.toSet()
}
