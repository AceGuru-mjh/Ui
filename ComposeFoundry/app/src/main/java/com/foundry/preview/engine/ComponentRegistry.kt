package com.foundry.preview.engine

/**
 * 组件渲染器注册表。类型名以小写形式作为 key，
 * 支持在运行时查询/注册渲染器，替代 when 分发。
 */
object ComponentRegistry {
    private val renderers = mutableMapOf<String, ComponentRenderer>()

    @Synchronized
    fun register(renderer: ComponentRenderer) {
        renderers[renderer.type.lowercase()] = renderer
    }

    @Synchronized
    fun get(type: String): ComponentRenderer? = renderers[type.lowercase()]

    @Synchronized
    fun isRegistered(type: String): Boolean = renderers.containsKey(type.lowercase())

    @Synchronized
    fun allTypes(): Set<String> = renderers.keys.toSet()
}
