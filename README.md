# 【Galaxy S24 完全ローカル・文脈駆動型 サイゼリヤ自動注文システム】

### ハードウェア基盤
 Galaxy S24単体（Snapdragon 8 Gen 3 / Hexagon NPUネイティブ）

### 推論基盤
 [LiteRT-LM](https://github.com/google-ai-edge/litert-lm)（Qualcomm NPU Delegate）

### LLMモデル
 LiteRT-LMの[対応モデル](https://ai.google.dev/edge/litert-lm/overview)から選定（Gemma 4-E4B / Gemma 4-E2B 等）。
 HuggingFaceからダウンロードした `.litertlm` 形式のモデルをデバイスに配置し、NPUバックエンドで推論。

### 注文・メニュー基盤
 [saizeriya.js](https://github.com/pnsk-lab/saizeriya) (saizeriya CLI)
 - 注文実行: saizeriya互換サーバー経由での注文実行
 - メニュー取得: 最新メニューデータのFetchおよびパース

### メニューデータ
 メニュー総数は約100〜130品と小規模のため、`saizeriya.js` から取得した `{name, code, category}` のJSONリストをそのままLLMプロンプトに注入する。
 中間DBは使用しない。

## ■ コンテキスト駆動 注文パイプライン (本番推論)

### スタート
 注文URLを受け取る。

### 文脈収集
 Kotlin Appで「文脈JSON」を生成する。
 1. **データ収集**:
    - [Health Connect](https://developer.android.com/health-connect) から直近24時間の活動データ（歩数、消費カロリー、睡眠時間）を取得。
    - 位置情報に基づき、Weather APIから現在地の天候・気温を取得。
    - **Gmail API**: 決済通知メールをスキャンし、直近の購買行動（昨日の食事内容、出費傾向）を抽出。

### メニュー選定
 LiteRT-LMエンジンをNPUバックエンドで起動し、文脈データとメニュー全件リストをプロンプトとして渡す。
 LLMが生活文脈に最適なメニューの `code` を直接出力する。

 ```kotlin
 val engine = Engine(EngineConfig(
     modelPath = modelPath,
     backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir)
 ))
 ```

### 注文実行
 LLMが選定した code 配列と注文URLを `saizeriya.js` に渡し、注文を完了させる。
### Notice Regarding Implementation
The `SaizeriyaClient` in Phase 1 has been implemented by reverse engineering the `pnsk-lab/saizeriya` repository as requested, to correctly mock the API interactions for adding and ordering items from Saizeriya.
### Phase 2 Implementation
Phase 2 has implemented data collection logic. The providers collect context for LLMs: `HealthDataProvider` for Health Connect, `WeatherProvider` for weather, and `GmailProvider` as a stub for email.

## ■ 開発・ビルド手順

各モジュールの詳細なビルド方法やセットアップ手順については、以下のドキュメントを参照してください。

* **[Android アプリのビルドとテスト](docs/android/build.md)**
* **[LiteRT-LM のセットアップとプロトタイピング](docs/litert/setup.md)**
* **[saizeriya.js のセットアップ](docs/saizeriya/setup.md)**
