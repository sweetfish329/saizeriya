# Phase 7: テスト・最適化・リリース準備

## 概要

全コンポーネントの統合テスト、NPUベンチマーク、パフォーマンス最適化、
ドキュメント更新を行い、リリース可能な状態にする。

## 前提条件

- Phase 0〜6 が完了していること
- Galaxy S24 実機が利用可能であること

## 想定期間: 3日

---

## 実装タスク一覧

- [ ] Task 7-1: ユニットテスト整備
- [ ] Task 7-2: 統合テスト（E2E）
- [ ] Task 7-3: NPUベンチマーク
- [ ] Task 7-4: パフォーマンス最適化
- [ ] Task 7-5: プロンプト改善
- [ ] Task 7-6: Gmail API 本実装（オプション）
- [ ] Task 7-7: ドキュメント更新

---

## Task 7-1: ユニットテスト整備

### テスト対象と方針

| クラス | テスト方針 |
|--------|-----------|
| `SaizeriyaClient` | Ktor MockEngine でHTTP通信をモック |
| `MenuRepository` | ローカルJSONファイルからの読み込みテスト |
| `HealthDataProvider` | Robolectric または モッククラス |
| `WeatherProvider` | Ktor MockEngine |
| `ContextCollector` | 各Providerをモックして統合テスト |
| `PromptBuilder` | 生成されたプロンプトの構造検証 |
| `ResponseParser` | 各種LLM出力パターンのパーステスト |
| `OrderPipeline` | 全コンポーネントモックでのフローテスト |
| `OrderViewModel` | StateFlow の状態遷移テスト |

### ResponseParser テスト例

```kotlin
class ResponseParserTest {
    private val parser = ResponseParser()

    @Test
    fun `正常なJSON出力をパースできる`() {
        val input = """{"codes":["1202","3201"],"reasoning":"テスト"}"""
        val codes = parser.parseMenuCodes(input)
        assertEquals(listOf("1202", "3201"), codes)
    }

    @Test
    fun `コードブロック付きJSON出力をパースできる`() {
        val input = """
            以下がおすすめです。
            ```json
            {"codes":["1202","3201"],"reasoning":"テスト"}
            ```
        """.trimIndent()
        val codes = parser.parseMenuCodes(input)
        assertEquals(listOf("1202", "3201"), codes)
    }

    @Test
    fun `テキスト形式の出力からコードを抽出できる`() {
        val input = "おすすめは 1202 と 3201 です。"
        val codes = parser.parseMenuCodes(input)
        assertEquals(listOf("1202", "3201"), codes)
    }

    @Test
    fun `空の出力では空リストを返す`() {
        val codes = parser.parseMenuCodes("")
        assertTrue(codes.isEmpty())
    }
}
```

---

## Task 7-2: 統合テスト（E2E）

### テストシナリオ

| # | シナリオ | 検証項目 |
|---|---------|---------|
| 1 | 正常フロー | URL入力→文脈収集→推論→注文→結果表示 |
| 2 | NPU推論 | NPUバックエンドでの推論成功 |
| 3 | CPU フォールバック | NPU非対応時にCPUで推論成功 |
| 4 | ネットワーク断 | オフライン時のエラーハンドリング |
| 5 | Health Connect未対応 | パーミッション拒否時の部分文脈推論 |
| 6 | ディープリンク | QR URLからの直接起動 |

### E2Eテスト手順（手動）

```
1. Galaxy S24 でアプリを起動
2. QR URL を入力（テスト用URL使用）
3. 人数を選択して「注文開始」をタップ
4. 進捗表示が順次更新されることを確認
5. 結果画面にメニューと理由が表示されることを確認
```

---

## Task 7-3: NPUベンチマーク

### 測定項目

| メトリクス | 測定方法 | 目標値 |
|-----------|---------|--------|
| モデル初期化時間 | `System.currentTimeMillis()` | < 10秒 |
| TTFT (Time to First Token) | `sendMessageAsync` の最初のemitまで | < 1秒 |
| トークン生成速度 | トークン数 / 生成時間 | > 20 tokens/sec |
| 総推論時間 | プロンプト送信〜レスポンス完了 | < 10秒 |
| メモリ使用量 | Android Profiler | < 3GB |
| バッテリー消費 | 1回注文あたり | < 1% |

### ベンチマークコード

```kotlin
suspend fun benchmark(llmEngine: LlmEngine, systemPrompt: String, userPrompt: String) {
    val initStart = System.currentTimeMillis()
    llmEngine.initialize(modelPath)
    val initTime = System.currentTimeMillis() - initStart
    Log.i("Bench", "初期化: ${initTime}ms")

    val inferStart = System.currentTimeMillis()
    val response = llmEngine.generateResponse(systemPrompt, userPrompt)
    val inferTime = System.currentTimeMillis() - inferStart
    Log.i("Bench", "推論: ${inferTime}ms")
    Log.i("Bench", "出力長: ${response.length} chars")
}
```

### モデル比較

| モデル | 初期化 | 推論 | メモリ | 品質 |
|--------|--------|------|--------|------|
| Gemma 4-E2B | 計測中 | 計測中 | 計測中 | 計測中 |
| Gemma 4-E4B | 計測中 | 計測中 | 計測中 | 計測中 |

---

## Task 7-4: パフォーマンス最適化

### 最適化ポイント

1. **エンジン事前初期化**: アプリ起動時にバックグラウンドで `LlmEngine.initialize()` を実行
   ```kotlin
   class SaizeriyaApp : Application() {
       lateinit var llmEngine: LlmEngine
       override fun onCreate() {
           super.onCreate()
           CoroutineScope(Dispatchers.IO).launch {
               llmEngine = LlmEngine(this@SaizeriyaApp)
               llmEngine.initialize(getModelPath())
           }
       }
   }
   ```

2. **メニューデータキャッシュ**: 初回取得後にローカルにキャッシュ
   ```kotlin
   private var cachedMenu: List<MenuItem>? = null
   suspend fun getAllMenuItems(): List<MenuItem> {
       cachedMenu?.let { return it }
       return fetchFromApi().also { cachedMenu = it }
   }
   ```

3. **文脈収集の並列化**: `coroutineScope` + `async` で3つのProviderを並列実行（Task 2-5で実装済み）

4. **cacheDir活用**: LiteRT-LMのキャッシュで2回目以降の初期化を高速化

---

## Task 7-5: プロンプト改善

### 改善手順

1. 実際のLLM出力を収集（ログに記録）
2. JSON形式遵守率を計測
3. 選定メニューの妥当性を評価
4. 問題があればプロンプトを調整

### 改善例

```
# 元のプロンプト（精度低い場合）
「最適なメニューを選んでください」

# 改善後（Few-shot追加）
「以下は過去の選定例です:
入力: 歩数8000, 気温30℃
出力: {"codes":["2101","1202","3201"],"reasoning":"..."}

あなたも同様に選定してください。」
```

---

## Task 7-6: Gmail API 本実装（オプション）

Phase 2 でスタブ実装した GmailProvider の本実装。

### 実装手順

1. Google Cloud Console でプロジェクト作成
2. Gmail API を有効化
3. OAuth 2.0 クライアントIDを作成
4. Android アプリに Google Sign-In を統合
5. Gmail API でメール検索・解析

> **優先度: 低** — MVP では省略可能。他の文脈データで十分な推論品質が得られるか確認してから判断。

---

## Task 7-7: ドキュメント更新

### 更新対象

- [ ] `README.md` — セットアップ手順、使い方を追加
- [ ] `AGENTS.md` — 残存課題の更新
- [ ] コード内コメント — 全クラスにKDocを追加

### README.md 追記内容

```markdown
## セットアップ

1. Android Studio でプロジェクトを開く
2. `gradle/libs.versions.toml` のバージョンを確認
3. `.litertlm` モデルファイルをデバイスに配置
4. `./gradlew build` でビルド
5. Galaxy S24 に実機デプロイ

## 使い方

1. アプリを起動
2. サイゼリヤのQRコードURLを入力
3. 人数を選択して「注文開始」をタップ
4. AIが文脈に基づいてメニューを自動選定
5. 注文が自動的に送信される
```

---

## 完了条件

- [ ] 全ユニットテストが通過（`./gradlew test` 成功）
- [ ] Galaxy S24 実機でE2Eテストが通過
- [ ] NPUベンチマーク結果が目標値内
- [ ] パフォーマンス最適化が適用済み
- [ ] ドキュメントが最新に更新済み
- [ ] ProGuard/R8 でリリースビルドが成功

## 参考リンク

- [Android Testing ドキュメント](https://developer.android.com/training/testing)
- [Android Profiler](https://developer.android.com/studio/profile/android-profiler)
- [LiteRT-LM ベンチマーク](https://github.com/google-ai-edge/litert-lm)
