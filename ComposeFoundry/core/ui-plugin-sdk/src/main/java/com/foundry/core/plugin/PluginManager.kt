package com.foundry.core.plugin

import com.foundry.core.uimodel.UiCapability

/**
 * 插件注册中心（单例服务层）。
 *
 * 职责（对照“服务层”原则）：
 *  - 注册 / 注销 / 发现格式插件，边界处校验描述符（失败快速）；
 *  - 为给定工件选择最匹配插件，支持能力（UiCapability）协商；
 *  - 线程安全（@Synchronized），可在 Application.onCreate 并发初始化中使用。
 */
object PluginManager {

    private val plugins = mutableListOf<UiFormatPlugin>()

    /** 注册插件；非法描述符直接抛 IllegalArgumentException（失败快速）。重复 id 静默忽略。 */
    @Synchronized
    fun register(plugin: UiFormatPlugin) {
        require(plugin.descriptor.isValid()) { "Invalid PluginDescriptor: ${plugin.descriptor}" }
        if (plugins.none { it.descriptor.id == plugin.descriptor.id }) {
            plugins.add(plugin)
        }
    }

    /** 仅在不存在同 id 插件时注册，返回是否真正注册成功。 */
    @Synchronized
    fun registerIfAbsent(plugin: UiFormatPlugin): Boolean {
        require(plugin.descriptor.isValid()) { "Invalid PluginDescriptor: ${plugin.descriptor}" }
        if (plugins.any { it.descriptor.id == plugin.descriptor.id }) return false
        plugins.add(plugin)
        return true
    }

    @Synchronized
    fun unregister(id: String) {
        plugins.removeAll { it.descriptor.id == id }
    }

    @Synchronized
    fun clear() {
        plugins.clear()
    }

    @Synchronized
    fun all(): List<UiFormatPlugin> = plugins.toList()

    /**
     * 为工件选择最匹配的插件。
     * @param requires 调用方要求的能力（如渲染交互控件需 RENDER_INTERACTIVE）；
     *                 不具备这些能力的插件即使匹配得分高也会被剔除。
     * 同分时按 descriptor.priority 取高者。
     */
    @Synchronized
    fun selectFor(artifact: UiArtifact, requires: Set<UiCapability> = emptySet()): UiFormatPlugin? =
        plugins
            .filter { plugin -> requires.all { it in plugin.descriptor.capabilities } }
            .map { it to it.canHandle(artifact) }
            .filter { it.second.score > 0 }
            .maxWithOrNull(compareBy({ it.second.score }, { it.first.descriptor.priority }))
            ?.first

    /** 可读的已注册插件清单，便于排查（observability）。 */
    @Synchronized
    fun describe(): String = buildString {
        appendLine("PluginManager(registered=${plugins.size})")
        plugins.forEach { p ->
            appendLine(
                "  - ${p.descriptor.id} v${p.descriptor.version} " +
                    "caps=${p.descriptor.capabilities} prio=${p.descriptor.priority}"
            )
        }
    }
}
