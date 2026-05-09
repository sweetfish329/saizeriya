# Phase 5: 注文実行パイプライン統合

## 概要

Phase 1〜4 で実装した個別コンポーネントを結合し、
「注文URL受信 → 文脈収集 → メニュー取得 → LLM推論 → 注文実行」の
エンドツーエンドパイプラインを `OrderPipeline` として実装する。

## 前提条件

- Phase 1（SaizeriyaClient, MenuRepository）完了
- Phase 2（ContextCollector）完了
- Phase 3（LlmEngine, ResponseParser）完了
- Phase 4（PromptBuilder）完了

## 想定期間: 2日

---

## 実装タスク一覧

- [ ] Task 5-1: OrderPipeline の実装
- [ ] Task 5-2: OrderExecutor の実装
- [ ] Task 5-3: エラーハンドリング・リトライ
- [ ] Task 5-4: パイプライン統合テスト

---

## Task 5-1: OrderPipeline の実装

### ファイル: `order/OrderPipeline.kt`

```kotlin
package com.example.saizeriya.order

import com.example.saizeriya.context.ContextCollector
import com.example.saizeriya.data.model.ContextData
import com.example.saizeriya.data.model.MenuItem
import com.example.saizeriya.data.repository.MenuRepository
import com.example.saizeriya.llm.LlmEngine
import com.example.saizeriya.llm.PromptBuilder
import com.example.saizeriya.llm.ResponseParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 注文パイプラインの全工程を管理するクラス。
 *
 * パイプライン:
 * 1. 文脈データ収集（ContextCollector）
 * 2. メニューデータ取得（MenuRepository）
 * 3. プロンプト生成（PromptBuilder）
 * 4. LLM推論（LlmEngine）
 * 5. レスポンスパース（ResponseParser）
 * 6. 注文実行（OrderExecutor）
 */
class OrderPipeline(
    private val contextCollector: ContextCollector,
    private val menuRepository: MenuRepository,
    private val llmEngine: LlmEngine,
    private val promptBuilder: PromptBuilder,
    private val responseParser: ResponseParser,
    private val orderExecutor: OrderExecutor
) {
    /** パイプラインの現在の状態 */
    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state: StateFlow<PipelineState> = _state

    /**
     * パイプラインを実行する。
     *
     * @param qrUrl サイゼリヤQRコードURL
     * @param peopleCount 人数
     * @param latitude 現在の緯度
     * @param longitude 現在の経度
     * @return 注文結果
     */
    suspend fun execute(
        qrUrl: String,
        peopleCount: Int,
        latitude: Double,
        longitude: Double
    ): PipelineResult {
        try {
            // ステップ1: 文脈データ収集
            _state.value = PipelineState.CollectingContext
            val contextData = contextCollector.collectAll(latitude, longitude)

            // ステップ2: メニューデータ取得
            _state.value = PipelineState.FetchingMenu
            val menuItems = menuRepository.getAllMenuItems()

            // ステップ3: プロンプト生成
            _state.value = PipelineState.BuildingPrompt
            val (systemPrompt, userPrompt) = promptBuilder.build(contextData, menuItems)

            // ステップ4: LLM推論
            _state.value = PipelineState.RunningInference
            val llmOutput = llmEngine.generateResponse(systemPrompt, userPrompt)

            // ステップ5: レスポンスパース
            _state.value = PipelineState.ParsingResponse
            val menuCodes = responseParser.parseMenuCodes(llmOutput)

            // コードのバリデーション
            val validCodes = validateCodes(menuCodes, menuItems)
            if (validCodes.isEmpty()) {
                return PipelineResult.Error("LLMが有効なメニューコードを出力しませんでした")
            }

            // ステップ6: 注文実行
            _state.value = PipelineState.PlacingOrder
            val orderResult = orderExecutor.execute(qrUrl, peopleCount, validCodes)

            _state.value = PipelineState.Completed
            return PipelineResult.Success(
                selectedMenuCodes = validCodes,
                selectedMenuItems = menuItems.filter { it.code in validCodes },
                reasoning = extractReasoning(llmOutput),
                contextData = contextData
            )
        } catch (e: Exception) {
            _state.value = PipelineState.Failed(e.message ?: "不明なエラー")
            return PipelineResult.Error(e.message ?: "パイプライン実行中にエラーが発生しました")
        }
    }

    /**
     * メニューコードのバリデーション。
     * 存在しないコードを除外する。
     */
    private fun validateCodes(
        codes: List<String>,
        menuItems: List<MenuItem>
    ): List<String> {
        val validCodeSet = menuItems.map { it.code }.toSet()
        return codes.filter { it in validCodeSet }
    }

    /**
     * LLM出力からreasoningを抽出する。
     */
    private fun extractReasoning(llmOutput: String): String {
        return try {
            val response = kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
            }.decodeFromString<com.example.saizeriya.llm.LlmMenuResponse>(llmOutput)
            response.reasoning
        } catch (e: Exception) {
            ""
        }
    }
}

/** パイプラインの実行状態 */
sealed class PipelineState {
    data object Idle : PipelineState()
    data object CollectingContext : PipelineState()
    data object FetchingMenu : PipelineState()
    data object BuildingPrompt : PipelineState()
    data object RunningInference : PipelineState()
    data object ParsingResponse : PipelineState()
    data object PlacingOrder : PipelineState()
    data object Completed : PipelineState()
    data class Failed(val message: String) : PipelineState()
}

/** パイプラインの実行結果 */
sealed class PipelineResult {
    data class Success(
        val selectedMenuCodes: List<String>,
        val selectedMenuItems: List<MenuItem>,
        val reasoning: String,
        val contextData: ContextData
    ) : PipelineResult()

    data class Error(val message: String) : PipelineResult()
}
```

---

## Task 5-2: OrderExecutor の実装

### ファイル: `order/OrderExecutor.kt`

```kotlin
package com.example.saizeriya.order

import com.example.saizeriya.data.model.OrderSession

/**
 * SaizeriyaClientを使って実際の注文を実行する。
 */
class OrderExecutor(
    private val saizeriyaClient: SaizeriyaClient
) {
    /**
     * メニューコードリストで注文を実行する。
     *
     * @param qrUrl QRコードURL
     * @param peopleCount 人数
     * @param menuCodes 注文するメニューコードリスト
     * @return 注文完了後のセッション状態
     */
    suspend fun execute(
        qrUrl: String,
        peopleCount: Int,
        menuCodes: List<String>
    ): OrderSession {
        // 1. セッション作成
        val session = saizeriyaClient.createSession(qrUrl, peopleCount)

        // 2. カートにアイテム追加
        for (code in menuCodes) {
            saizeriyaClient.addItem(session.sessionId, code, count = 1)
        }

        // 3. 注文送信
        return saizeriyaClient.submitOrder(session.sessionId)
    }
}
```

---

## Task 5-3: エラーハンドリング・リトライ

### エラーパターンと対処

| エラー | 原因 | 対処 |
|--------|------|------|
| ネットワークエラー | 通信断 | 3回リトライ（指数バックオフ） |
| LLM出力パースエラー | 不正な出力形式 | プロンプト再送（温度を下げて再試行） |
| 無効なメニューコード | LLM幻覚 | バリデーションで除外、残りで注文 |
| セッション期限切れ | QR URL期限切れ | ユーザーにQR再スキャンを要求 |
| カート空エラー | 全コード無効 | エラー表示、手動注文への誘導 |

### リトライロジック

```kotlin
/**
 * 指数バックオフでリトライする。
 */
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(maxRetries - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            kotlinx.coroutines.delay(currentDelay)
            currentDelay *= 2
        }
    }
    return block() // 最後の1回はそのまま例外を投げる
}
```

---

## Task 5-4: パイプライン統合テスト

### テストシナリオ

1. **正常系**: 全ステップが成功し、注文が完了する
2. **LLM出力異常**: JSONでないテキストが出力された場合のフォールバック
3. **部分的な文脈**: Health Connect未対応時に天候のみで推論
4. **ネットワークエラー**: メニュー取得失敗時のエラーハンドリング

### モックベースの統合テスト

```kotlin
@Test
fun `パイプラインが正常に注文を完了する`() = runTest {
    // モック設定
    val mockContextCollector = // 固定のContextDataを返す
    val mockMenuRepository = // 固定のMenuItemリストを返す
    val mockLlmEngine = // 固定のJSON出力を返す
    val mockOrderExecutor = // 成功を返す

    val pipeline = OrderPipeline(
        contextCollector = mockContextCollector,
        menuRepository = mockMenuRepository,
        llmEngine = mockLlmEngine,
        promptBuilder = PromptBuilder(),
        responseParser = ResponseParser(),
        orderExecutor = mockOrderExecutor
    )

    val result = pipeline.execute("https://...", 2, 35.6, 139.7)
    assertTrue(result is PipelineResult.Success)
}
```

---

## 完了条件

- [ ] `OrderPipeline` が全6ステップを順次実行可能
- [ ] `OrderExecutor` が SaizeriyaClient 経由で注文実行可能
- [ ] `PipelineState` の状態遷移が正しく動作
- [ ] エラーハンドリング・リトライロジックが実装済み
- [ ] 統合テストが通過
- [ ] `./gradlew test` が成功
