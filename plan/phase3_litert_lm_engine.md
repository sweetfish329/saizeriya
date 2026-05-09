# Phase 3: LiteRT-LM 推論エンジン統合

## 概要

LiteRT-LM をAndroidアプリに統合し、Qualcomm NPUバックエンドでオンデバイスLLM推論を実行可能にする。
モデルの準備・配置、Engine初期化、Conversation管理、レスポンス処理を実装する。

## 前提条件

- Phase 0 が完了していること
- Galaxy S24 実機が利用可能であること（NPUテスト用）
- HuggingFace から `.litertlm` モデルファイルをダウンロード済みであること

## 想定期間: 3日

---

## 実装タスク一覧

- [ ] Task 3-1: LiteRT-LM 依存関係の追加
- [ ] Task 3-2: モデルファイルの準備・配置
- [ ] Task 3-3: LlmEngine クラスの実装
- [ ] Task 3-4: Conversation管理・推論実行
- [ ] Task 3-5: ResponseParser 実装
- [ ] Task 3-6: ベンチマーク・動作確認

---

## Task 3-1: LiteRT-LM 依存関係の追加

### `app/build.gradle.kts` に追加

```kotlin
dependencies {
    // LiteRT-LM（Maven座標は公式ドキュメントで確認）
    // 方法1: Maven Central からの取得（推奨）
    implementation("com.google.ai.edge:litert-lm:1.0.0")

    // 方法2: AARファイルをローカルに配置する場合
    // implementation(files("libs/litert-lm.aar"))

    // NPU Delegate のネイティブライブラリ
    // Qualcomm NPU 用のSOファイルがLiteRT-LMに同梱されている場合は不要
}
```

> **重要**: LiteRT-LM の正確な Maven 座標は
> [公式リポジトリ](https://github.com/google-ai-edge/litert-lm) で確認すること。
> Maven未公開の場合は、リポジトリから手動ビルドしてAARを配置する。

---

## Task 3-2: モデルファイルの準備・配置

### モデル選定

| モデル | パラメータ数 | メモリ使用量(推定) | 推奨用途 |
|--------|-------------|-------------------|----------|
| Gemma 4-E2B | ~2B | ~2GB | 高速推論・メモリ制約時 |
| Gemma 4-E4B | ~4B | ~4GB | 高精度推論 |

### ダウンロード方法

```bash
# LiteRT-LM CLI でダウンロード
uv tool install litert-lm
litert-lm download --from-huggingface-repo=litert-community/gemma-4-E2B-it-litert-lm

# または HuggingFace から直接ダウンロード
# https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
```

### デバイスへの配置

```bash
# モデルファイルをデバイスのストレージにpush
adb push gemma-4-E2B-it.litertlm /sdcard/Download/models/

# アプリ内部ストレージに配置する場合（推奨）
# アプリ初回起動時にassetsまたはダウンロードからコピー
```

### アプリからのモデルパス取得

```kotlin
// 内部ストレージにコピーしたモデルのパス
val modelPath = File(context.filesDir, "models/gemma-4-E2B-it.litertlm").absolutePath

// または外部ストレージ
val modelPath = File(
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
    "models/gemma-4-E2B-it.litertlm"
).absolutePath
```

---

## Task 3-3: LlmEngine クラスの実装

### ファイル: `llm/LlmEngine.kt`

```kotlin
package com.example.saizeriya.llm

import android.content.Context
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM エンジンのライフサイクル管理クラス。
 * NPUバックエンドでの推論を担当する。
 *
 * 使用例:
 * ```
 * val llmEngine = LlmEngine(context)
 * llmEngine.initialize("/path/to/model.litertlm")
 * val response = llmEngine.generateResponse("おすすめのメニューは？")
 * llmEngine.close()
 * ```
 *
 * 注意:
 * - initialize() は数秒かかるため、バックグラウンドスレッドで実行すること
 * - 使用後は必ず close() を呼ぶこと
 */
class LlmEngine(private val context: Context) {

    private var engine: Engine? = null

    /**
     * LiteRT-LM エンジンを初期化する。
     * NPUバックエンドを使用。NPU非対応の場合はCPUにフォールバック。
     *
     * @param modelPath .litertlm ファイルの絶対パス
     * @throws IllegalStateException モデルファイルが存在しない場合
     */
    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        require(java.io.File(modelPath).exists()) {
            "モデルファイルが存在しません: $modelPath"
        }

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.NPU(
                nativeLibraryDir = context.applicationInfo.nativeLibraryDir
            ),
            cacheDir = context.cacheDir.absolutePath
        )

        engine = Engine(engineConfig).also {
            it.initialize()  // 数秒かかる
        }
    }

    /**
     * CPUバックエンドで初期化する（NPU非対応環境用）。
     */
    suspend fun initializeWithCpu(modelPath: String) = withContext(Dispatchers.IO) {
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath
        )
        engine = Engine(engineConfig).also { it.initialize() }
    }

    /**
     * 同期的にプロンプトを送信し、完全なレスポンスを取得する。
     *
     * @param systemPrompt システム指示
     * @param userPrompt ユーザープロンプト（文脈 + メニュー）
     * @return LLMの応答テキスト
     */
    suspend fun generateResponse(
        systemPrompt: String,
        userPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val eng = engine ?: throw IllegalStateException("エンジンが初期化されていません")

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            samplerConfig = SamplerConfig(
                topK = 10,
                topP = 0.95,
                temperature = 0.3  // 低温で確定的な出力を促す
            )
        )

        eng.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessage(userPrompt)
        }
    }

    /**
     * ストリーミングでレスポンスを取得する（UI表示用）。
     *
     * @param systemPrompt システム指示
     * @param userPrompt ユーザープロンプト
     * @return トークンのFlow
     */
    suspend fun generateResponseStream(
        systemPrompt: String,
        userPrompt: String
    ): Flow<String> = withContext(Dispatchers.IO) {
        val eng = engine ?: throw IllegalStateException("エンジンが初期化されていません")

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            samplerConfig = SamplerConfig(
                topK = 10,
                topP = 0.95,
                temperature = 0.3
            )
        )

        val conversation = eng.createConversation(conversationConfig)
        conversation.sendMessageAsync(userPrompt)
    }

    /**
     * エンジンのリソースを解放する。
     * 必ず使用後に呼ぶこと。
     */
    fun close() {
        engine?.close()
        engine = null
    }

    /** エンジンが初期化済みか確認する */
    fun isInitialized(): Boolean = engine != null
}
```

### 重要な設計ポイント

1. **バックエンド選択**:
   - 本番: `Backend.NPU()` — Qualcomm Hexagon NPUで高速推論
   - 開発/テスト: `Backend.CPU()` — エミュレータやNPU非対応端末用
   - `Backend.GPU()` も選択可能（GPUの方が速い場合もある）

2. **temperature 設定**:
   - `0.3` に設定し、メニューコード出力の確定性を高める
   - 創造的な推薦文が不要なため、低温が適切

3. **cacheDir**:
   - 設定すると2回目以降の読み込みが高速化される
   - `context.cacheDir` を推奨

---

## Task 3-4: Conversation管理・推論実行

### 推論のフロー

```
1. LlmEngine.initialize(modelPath) — アプリ起動時に1回
2. LlmEngine.generateResponse(systemPrompt, userPrompt) — 注文ごとに1回
3. LlmEngine.close() — アプリ終了時に1回
```

### 注意: メモリ管理

```kotlin
// Conversation は use ブロックで自動クローズ
eng.createConversation(config).use { conversation ->
    val response = conversation.sendMessage(prompt)
    // conversation は use ブロック終了時に自動的にclose()される
}
```

---

## Task 3-5: ResponseParser 実装

### ファイル: `llm/ResponseParser.kt`

```kotlin
package com.example.saizeriya.llm

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LLMの出力テキストからメニューコードリストを抽出するパーサー。
 *
 * LLMの出力形式（期待値）:
 * ```json
 * {
 *   "codes": ["1202", "3201", "2101"],
 *   "reasoning": "歩数が多く消費カロリーが高いため、高カロリーなドリアと..."
 * }
 * ```
 */
class ResponseParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * LLM出力からメニューコードのリストを抽出する。
     *
     * @param llmOutput LLMの生テキスト出力
     * @return メニューコードのリスト（例: ["1202", "3201"]）
     * @throws ParseException パースに失敗した場合
     */
    fun parseMenuCodes(llmOutput: String): List<String> {
        // 方法1: JSON形式のレスポンスをパース
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
```

---

## Task 3-6: ベンチマーク・動作確認

### LiteRT-LM CLIでのベンチマーク

```bash
litert-lm benchmark gemma-4-E2B-it.litertlm -p 256 -d 256
```

### アプリ内ベンチマーク

```kotlin
// 推論時間の計測
val startTime = System.currentTimeMillis()
val response = llmEngine.generateResponse(systemPrompt, userPrompt)
val elapsedMs = System.currentTimeMillis() - startTime
Log.i("Benchmark", "推論時間: ${elapsedMs}ms")
```

### 目標メトリクス

| メトリクス | 目標値 |
|-----------|--------|
| 初期化時間 | < 10秒 |
| 推論時間（256トークン入力→64トークン出力） | < 5秒 |
| メモリ使用量 | < 3GB |

---

## 完了条件

- [ ] LiteRT-LM 依存関係が `build.gradle.kts` に追加済み
- [ ] `.litertlm` モデルファイルがデバイスに配置済み
- [ ] `LlmEngine` がNPUバックエンドで初期化・推論を実行可能
- [ ] `ResponseParser` がLLM出力からメニューコードを抽出可能
- [ ] Galaxy S24 実機でベンチマーク実行済み
- [ ] `./gradlew build` が成功

## 参考リンク

- [LiteRT-LM GitHub](https://github.com/google-ai-edge/litert-lm)
- [LiteRT-LM Kotlin API Getting Started](https://github.com/google-ai-edge/litert-lm/blob/main/docs/api/kotlin/getting_started.md)
- [Gemma 4 LiteRT-LM モデル](https://huggingface.co/litert-community)
