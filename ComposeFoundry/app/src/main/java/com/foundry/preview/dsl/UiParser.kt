package com.foundry.preview.dsl

import kotlinx.serialization.json.Json

/**
 * JSON → UiDocument 解析器。
 * 使用 kotlinx.serialization，容忍未知字段、宽松模式。
 */
class UiParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    /**
     * 解析 JSON 字符串为 UiDocument。
     * @param jsonString 原始 JSON 文本
     * @return Result.success(UiDocument) 或 Result.failure(Exception)
     */
    fun parse(jsonString: String): Result<UiDocument> {
        return try {
            val document = json.decodeFromString<UiDocument>(jsonString)
            Result.success(document)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
