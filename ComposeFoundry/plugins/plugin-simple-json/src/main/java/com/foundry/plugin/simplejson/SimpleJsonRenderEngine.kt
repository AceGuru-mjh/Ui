package com.foundry.plugin.simplejson

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.foundry.core.renderer.IRenderEngine
import com.google.gson.Gson
import com.google.gson.JsonObject

/**
 * 第一个离线渲染引擎：将简单 JSON 渲染为 Android View。
 *
 * ## 协议格式
 * ```json
 * {
 *   "text": "Hello World",
 *   "color": "#2196F3",
 *   "size": 28,
 *   "background": "#FFFFFF",
 *   "subtitle": "可选副标题"
 * }
 * ```
 *
 * ## ClassLoader 说明
 * - 编译时：通过 `compileOnly(project(":core:renderer-api"))` 引用 [IRenderEngine]
 * - 运行时：[IRenderEngine] 由父 ClassLoader（:renderer 进程 CL）提供
 * - Gson 会被打包进本插件的 JAR，与主程序的 JSON 库完全隔离
 */
class SimpleJsonRenderEngine : IRenderEngine {

    private val gson = Gson()

    override fun render(payload: String, context: Context): View {
        return try {
            val json = gson.fromJson(payload, JsonObject::class.java)
            buildView(json, context)
        } catch (e: Exception) {
            createErrorView("JSON 解析失败: ${e.message}", context)
        }
    }

    private fun buildView(json: JsonObject, context: Context): View {
        val text = json.get("text")?.asString ?: "Hello from Plugin"
        val color = parseColor(json.get("color")?.asString, "#FF5722")
        val size = json.get("size")?.asFloat ?: 28f
        val background = parseColor(json.get("background")?.asString, "#F5F5F5")
        val subtitle = json.get("subtitle")?.asString

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(background)
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // 主标题
        container.addView(TextView(context).apply {
            this.text = "\uD83D\uDE80 离线插件渲染成功"
            setTextColor(Color.parseColor("#333333"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 12)
        })

        // 内容文本
        container.addView(TextView(context).apply {
            this.text = text
            setTextColor(color)
            textSize = size
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, 0, 0, 8)
        })

        // 副标题（可选）
        if (!subtitle.isNullOrBlank()) {
            container.addView(TextView(context).apply {
                this.text = subtitle
                setTextColor(Color.GRAY)
                textSize = 16f
                gravity = Gravity.CENTER
            })
        }

        // 底部标识
        container.addView(TextView(context).apply {
            this.text = "Powered by SimpleJsonRenderEngine v1.0.0"
            setTextColor(Color.LTGRAY)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 0)
        })

        return container
    }

    private fun createErrorView(message: String, context: Context): View {
        return TextView(context).apply {
            text = "插件渲染崩溃\n\n$message"
            setTextColor(Color.RED)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            setBackgroundColor(Color.parseColor("#FFF3F3"))
        }
    }

    private fun parseColor(hex: String?, default: String): Int {
        return try {
            Color.parseColor(hex ?: default)
        } catch (_: Exception) {
            Color.parseColor(default)
        }
    }
}
