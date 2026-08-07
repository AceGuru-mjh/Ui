package com.foundry.codegen

/**
 * 代码生成器的输入树模型。
 * 独立于 app 的 UiElement，由 app 模块提供转换适配器。
 */
data class CodegenNode(
    val type: String,
    val modifier: CodegenModifier = CodegenModifier(),
    val attributes: Map<String, String> = emptyMap(),
    val children: List<CodegenNode> = emptyList()
)

data class CodegenModifier(
    val fillMaxWidth: Boolean = false,
    val fillMaxHeight: Boolean = false,
    val fillMaxSize: Boolean = false,
    val width: Float? = null,
    val height: Float? = null,
    val padding: CodegenPadding? = null,
    val background: String? = null,
    val cornerRadius: Float? = null,
    val borderWidth: Float? = null,
    val borderColor: String? = null,
    val elevation: Float? = null,
    val horizontalAlignment: String? = null,
    val verticalArrangement: String? = null,
    val verticalAlignment: String? = null,
    val horizontalArrangement: String? = null,
    val contentAlignment: String? = null
)

data class CodegenPadding(
    val all: Float? = null,
    val horizontal: Float? = null,
    val vertical: Float? = null,
    val start: Float? = null,
    val top: Float? = null,
    val end: Float? = null,
    val bottom: Float? = null
)

/**
 * 生成器配置。
 */
data class GeneratorConfig(
    val functionName: String = "GeneratedScreen",
    val includePreview: Boolean = true,
    val includeImports: Boolean = true,
    val packageName: String = "com.example.generated"
)
