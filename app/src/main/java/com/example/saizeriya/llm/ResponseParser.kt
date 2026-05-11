package com.example.saizeriya.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LLMの出力テキストからメニューコードリストを抽出するパーサー。
 */
class ResponseParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * LLM出力からメニューコードのリストを抽出する。
     *
     * @param llmOutput LLMの生テキスト出力
     * @return メニューコードのリスト（例: ["1202", "3201"]）
     */
    fun parseMenuCodes(llmOutput: String): List<String> {
        val jsonBlock = extractJsonBlock(llmOutput)
        if (jsonBlock != null) {
            return try {
                val response = json.decodeFromString<LlmMenuResponse>(jsonBlock)
                response.codes
            } catch (e: Exception) {
                // JSON パース失敗 → 方法2へフォールバック
                extractCodesFromText(llmOutput)
            }
        }

        // 方法2: テキストから正規表現でメニューコードを抽出
        return extractCodesFromText(llmOutput)
    }

    /**
     * テキストからJSONブロックを抽出する。
     * ```json ... ``` または { ... } を検索。
     */
    private fun extractJsonBlock(text: String): String? {
        // ```json ... ``` パターン
        val codeBlockRegex = Regex("```json\\s*(\\{[\\s\\S]*?\\})\\s*```")
        codeBlockRegex.find(text)?.let { return it.groupValues[1] }

        // { ... } パターン
        val jsonRegex = Regex("\\{[\\s\\S]*\"codes\"[\\s\\S]*\\}")
        jsonRegex.find(text)?.let { return it.value }

        return null
    }

    /**
     * テキストからメニューコード（4桁数字）を抽出する。
     */
    private fun extractCodesFromText(text: String): List<String> {
        val codeRegex = Regex("\\b(\\d{4})\\b")
        return codeRegex.findAll(text).map { it.value }.toList()
    }
}

@Serializable
data class LlmMenuResponse(
    val codes: List<String>,
    val reasoning: String = ""
)
