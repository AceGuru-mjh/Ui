package com.foundry.preview.engine

import com.foundry.core.plugin.UiFormatPlugin
import com.foundry.core.plugin.PluginManager
import com.foundry.preview.plugin.AndroidUiJsonPlugin
import com.foundry.preview.plugin.AndroidUiXmlPlugin
import com.foundry.preview.plugin.AndroidUiComposePlugin

/**
 * 渲染器 / 插件 注册门面。
 * 对上层（MainActivity / ViewModel）屏蔽底层组件注册表与插件管理器的细节，
 * 作为平台化后“格式插件 + 渲染后端”的统一入口。
 */
object RendererManager {

    /** 注册所有内置组件渲染器，并把 JSON DSL 注册为平台第一个格式插件。 */
    fun initialize() {
        initializeRenderers()
        PluginManager.register(AndroidUiJsonPlugin())
        PluginManager.register(AndroidUiXmlPlugin())
        PluginManager.register(AndroidUiComposePlugin())
    }

    fun get(type: String): ComponentRenderer? = ComponentRegistry.get(type)
    fun isRegistered(type: String): Boolean = ComponentRegistry.isRegistered(type)
    fun allTypes(): Set<String> = ComponentRegistry.allTypes()

    fun registerPlugin(plugin: UiFormatPlugin) = PluginManager.register(plugin)
    fun registeredPlugins(): List<UiFormatPlugin> = PluginManager.all()
}
