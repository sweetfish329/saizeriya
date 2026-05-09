# Phase 4: プロンプト設計・メニュー選定ロジック

## 概要

文脈データとメニュー全件をLLMに渡すプロンプトテンプレートを設計し、
LLMがメニューコードを出力するまでの一連のロジックを `PromptBuilder` に実装する。

## 前提条件

- Phase 1（メニューデータモデル）完了
- Phase 2（ContextData モデル）完了
- Phase 3（LlmEngine・ResponseParser）完了

## 想定期間: 2日

---

## 実装タスク一覧

- [ ] Task 4-1: プロンプトテンプレートの設計
- [ ] Task 4-2: PromptBuilder の実装
- [ ] Task 4-3: トークン数の推算と最適化
- [ ] Task 4-4: プロンプトのテスト（CLI検証）

---

## Task 4-1: プロンプトテンプレートの設計

### システムプロンプト

```text
あなたはサイゼリヤの注文アシスタントです。
ユーザーの生活文脈（健康データ・天候・食事履歴）とメニュー一覧を受け取り、
最適なメニューを3〜5品選定してください。

## ルール
1. 必ず以下のJSON形式で回答してください。それ以外のテキストは出力しないでください。
2. "codes" にはメニューの "code" を配列で指定してください。
3. "reasoning" には選定理由を簡潔に記述してください。
4. 合計金額は2000円以内を目安にしてください。
5. 栄養バランスを考慮してください（主食・副菜・デザート/ドリンクの組み合わせ）。

## 出力形式（厳守）
```json
{
  "codes": ["1202", "3201", "2101"],
  "reasoning": "選定理由をここに記述"
}
```
```

### ユーザープロンプト

```text
## あなたの現在の状況

{context_json}

## サイゼリヤ メニュー一覧

{menu_json}

上記の状況とメニューを踏まえて、最適な注文を提案してください。
```

### プロンプト全体の構成図

```
┌─────────────────────────────────────────┐
│ System Prompt（固定テンプレート）          │
│ - 役割定義                               │
│ - 出力形式ルール                          │
│ - 制約条件（予算・栄養バランス）           │
├─────────────────────────────────────────┤
│ User Prompt                              │
│ ┌─────────────────────────────────────┐ │
│ │ 文脈JSON（ContextData）             │ │
│ │ - 歩数, カロリー, 睡眠              │ │
│ │ - 天候, 気温                        │ │
│ │ - 最近の食事, 出費                  │ │
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ メニューJSON（MenuItem[]）           │ │
│ │ - 100〜130品の全メニュー             │ │
│ │ - code, name, price, category       │ │
│ └─────────────────────────────────────┘ │
│ 指示文                                   │
└─────────────────────────────────────────┘
```

---

## Task 4-2: PromptBuilder の実装

### ファイル: `llm/PromptBuilder.kt`

```kotlin
package com.example.saizeriya.llm

import com.example.saizeriya.data.model.ContextData
import com.example.saizeriya.data.model.MenuItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * LLMに送信するプロンプトを組み立てるビルダー。
 *
 * 使用例:
 * ```
 * val builder = PromptBuilder()
 * val (system, user) = builder.build(contextData, menuItems)
 * val response = llmEngine.generateResponse(system, user)
 * ```
 */
class PromptBuilder {

    private val json = Json { prettyPrint = false }

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
        val menuJson = json.encodeToString(
            ListSerializer(MenuItem.serializer()), menuItems
        )

        val userPrompt = USER_PROMPT_TEMPLATE.format(contextJson, menuJson)

        return Pair(SYSTEM_PROMPT, userPrompt)
    }

    /**
     * プロンプト全体の推定トークン数を計算する。
     * 日本語の場合、おおよそ 1文字 ≈ 1〜2トークン。
     *
     * @return 推定トークン数
     */
    fun estimateTokenCount(
        contextData: ContextData,
        menuItems: List<MenuItem>
    ): Int {
        val (system, user) = build(contextData, menuItems)
        val totalChars = system.length + user.length
        // 日本語+JSON混在の場合、1文字あたり約1.5トークンと推定
        return (totalChars * 1.5).toInt()
    }
}
```

---

## Task 4-3: トークン数の推算と最適化

### トークン数の見積もり

| 要素 | 文字数(推定) | トークン数(推定) |
|------|-------------|-----------------|
| システムプロンプト | ~300文字 | ~450 |
| 文脈JSON | ~200文字 | ~300 |
| メニューJSON（130品） | ~6,500文字 | ~9,750 |
| 指示文 | ~50文字 | ~75 |
| **合計（入力）** | **~7,050文字** | **~10,575** |
| 出力（期待） | ~200文字 | ~300 |

### Gemma 4-E2B のコンテキストウィンドウ

- 最大コンテキスト長: **8,192 トークン**（E2Bの場合）
- 入力+出力が 10,575+300 = 10,875 トークンとなり、**超過する可能性がある**

### 最適化戦略

1. **メニューJSONの圧縮**: 不要なフィールドを除外
   ```kotlin
   // 最小形式: {"c":"1202","n":"小ｴﾋﾞのｻﾗﾀﾞ","p":350,"g":"サラダ"}
   fun toCompactJson(items: List<MenuItem>): String {
       return items.joinToString(",", "[", "]") { item ->
           """{"c":"${item.code}","n":"${item.name}","p":${item.price},"g":"${item.category}"}"""
       }
   }
   ```

2. **カテゴリ別グルーピング**:
   ```
   【サラダ】
   1202 小ｴﾋﾞのｻﾗﾀﾞ ¥350
   1203 ﾜｶﾒｻﾗﾀﾞ ¥250
   ```

3. **Gemma 4-E4B の使用**: E4Bはコンテキストウィンドウが大きい可能性がある

4. **メニュー件数の制限**: カテゴリごとに上位N件に絞る（非推奨、全件を渡す設計方針に反する）

### 推奨: テキスト形式でのメニュー表現

```kotlin
/**
 * メニューをトークン効率の良いテキスト形式に変換する。
 * JSON形式よりも約40%トークン数を削減可能。
 */
fun toTextFormat(items: List<MenuItem>): String {
    return items.groupBy { it.category }
        .entries.joinToString("\n\n") { (category, categoryItems) ->
            "【$category】\n" + categoryItems.joinToString("\n") { item ->
                "${item.code} ${item.name} ¥${item.price}"
            }
        }
}
```

---

## Task 4-4: プロンプトのテスト（CLI検証）

### LiteRT-LM CLI でのテスト

```bash
litert-lm run \
  --from-huggingface-repo=litert-community/gemma-4-E2B-it-litert-lm \
  gemma-4-E2B-it.litertlm \
  --prompt="$(cat test_prompt.txt)"
```

### テストプロンプトファイル: `test_prompt.txt`

```text
あなたはサイゼリヤの注文アシスタントです。以下のJSON形式で回答してください。
{"codes":["メニューコード1","メニューコード2"],"reasoning":"理由"}

## 状況
{"health":{"stepsLast24h":8500,"caloriesBurnedLast24h":2100,"sleepDurationMinutes":420},"weather":{"description":"晴れ","temperatureCelsius":28.5,"feelsLikeCelsius":30.0,"humidity":65}}

## メニュー
【パスタ】
2101 ﾐﾗﾉ風ﾄﾞﾘｱ ¥300
2201 ﾍﾟﾍﾟﾛﾝﾁｰﾉ ¥400
【サラダ】
1202 小ｴﾋﾞのｻﾗﾀﾞ ¥350
【デザート】
3201 ﾃｨﾗﾐｽｸﾗｼｺ ¥300

最適な注文を提案してください。
```

### 検証項目

- [ ] LLMがJSON形式で出力するか
- [ ] codesに有効なメニューコードが含まれるか
- [ ] reasoningが文脈に基づいているか
- [ ] 応答時間が許容範囲内か（< 10秒）

---

## 完了条件

- [ ] `PromptBuilder` がシステム/ユーザープロンプトを生成可能
- [ ] トークン数がモデルのコンテキストウィンドウ内に収まる
- [ ] LiteRT-LM CLI でプロンプトテストが成功（正しいJSON出力）
- [ ] トークン最適化戦略が実装済み
- [ ] ユニットテストが通過

## 参考リンク

- [Gemma 4 プロンプトガイド](https://ai.google.dev/gemma)
- [LiteRT-LM API ドキュメント](https://github.com/google-ai-edge/litert-lm)
