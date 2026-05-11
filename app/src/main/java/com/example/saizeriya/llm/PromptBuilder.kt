package com.example.saizeriya.llm

import com.example.saizeriya.data.model.ContextData
import com.example.saizeriya.data.model.MenuItem
import kotlinx.serialization.json.Json

/**
 * LLMに送信するプロンプトを組み立てるビルダー。
 */
class PromptBuilder {

    private val json = Json { prettyPrint = false; encodeDefaults = true }

    companion object {
        /** システムプロンプトテンプレート */
        private val SYSTEM_PROMPT = """
あなたはサイゼリヤの注文アシスタントです。
ユーザーの生活文脈（健康データ・天候・食事履歴）とメニュー一覧を受け取り、
最適なメニューを3〜5品選定してください。

## ルール
1. 必ず以下のJSON形式で回答してください。それ以外のテキストは出力しないでください。
2. "codes" にはメニューの "code" を配列で指定してください。
3. "reasoning" には選定理由を簡潔に記述してください。
4. 合計金額は2000円以内を目安にしてください。
5. 栄養バランスを考慮してください。

## 出力形式（厳守）
```json
{
  "codes": ["1202", "3201", "2101"],
  "reasoning": "選定理由をここに記述"
}
```
        """.trimIndent()

        /** ユーザープロンプトテンプレート */
        private val USER_PROMPT_TEMPLATE = """
## あなたの現在の状況

%s

## サイゼリヤ メニュー一覧

%s

上記の状況とメニューを踏まえて、最適な注文を提案してください。
        """.trimIndent()
    }

    /**
     * システムプロンプトとユーザープロンプトを生成する。
     *
     * @param contextData 文脈データ
     * @param menuItems メニューリスト
     * @return Pair(システムプロンプト, ユーザープロンプト)
     */
    fun build(
        contextData: ContextData,
        menuItems: List<MenuItem>
    ): Pair<String, String> {
        val contextJson = json.encodeToString(ContextData.serializer(), contextData)
        val menuText = toTextFormat(menuItems)

        val userPrompt = USER_PROMPT_TEMPLATE.format(contextJson, menuText)

        return Pair(SYSTEM_PROMPT, userPrompt)
    }

    /**
     * プロンプト全体の推定トークン数を計算する。
     * 日本語の場合、おおよそ 1文字 ≈ 1.5トークンと推定。
     *
     * @return 推定トークン数
     */
    fun estimateTokenCount(
        contextData: ContextData,
        menuItems: List<MenuItem>
    ): Int {
        val (system, user) = build(contextData, menuItems)
        val totalChars = system.length + user.length
        return (totalChars * 1.5).toInt()
    }

    /**
     * メニューをトークン効率の良いテキスト形式に変換する。
     * JSON形式よりも約40%トークン数を削減可能。
     */
    fun toTextFormat(items: List<MenuItem>): String {
        return items.groupBy { it.category }
            .entries.joinToString("\n\n") { (category, categoryItems) ->
                "【${category}】\n" + categoryItems.joinToString("\n") { item ->
                    "${item.code} ${item.name} ¥${item.price}"
                }
            }
    }
}
