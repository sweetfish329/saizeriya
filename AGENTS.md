# AGENTS.md — Galaxy S24 サイゼリヤ自動注文システム

## プロジェクト概要
Galaxy S24（Snapdragon 8 Gen 3）上で完全にローカル動作する、文脈駆動型サイゼリヤ自動注文システム。
ユーザーの生活文脈（健康データ・天候・購買履歴）をLLMに入力し、最適なメニューを自動選定・注文する。

## アーキテクチャ

### 技術スタック
| レイヤー | 技術 | 役割 |
|----------|------|------|
| Android App | Kotlin | UI・文脈収集・LLM呼び出し・注文実行の統合 |
| 推論エンジン | [LiteRT-LM](https://github.com/google-ai-edge/litert-lm) | Qualcomm NPU DelegateによるオンデバイスLLM推論 |
| LLMモデル | Gemma 4-E4B / Gemma 4-E2B 等 | `.litertlm` 形式、HuggingFaceから取得 |
| メニュー・注文 | [saizeriya.js](https://github.com/pnsk-lab/saizeriya) | メニューJSON取得・注文実行 |

### パイプライン
```
注文URL受信 → 文脈収集(Health Connect / Weather / Gmail)
           → メニューJSON取得(saizeriya.js)
           → LLMプロンプト生成(文脈 + メニュー全件)
           → LiteRT-LM推論(NPU) → メニューcode出力
           → 注文実行(saizeriya.js)
```

### 重要な設計判断
- **中間DB不使用**: メニュー数が約100〜130品と小規模のため、ベクトルDB/SQLiteを使わずJSONをそのままLLMコンテキストに注入する。
- **Termux不要**: LiteRT-LMのKotlinネイティブAPIを使い、アプリ内で推論が完結する。ExecuTorch + Termux構成は採用しない。
- **OneComp非採用**: GPTQ形式は`.litertlm`への変換パスが存在しないため、LiteRT-LM公式の量子化パイプラインを使用する。

## コーディング規約

### Kotlin
- 公式Kotlinコーディング規約に準拠
- 公開APIには明示的な型注釈を付与
- ML推論・ネットワーク通信はバックグラウンドスレッド（`Dispatchers.IO` / Coroutines）で実行
- LiteRT-LMの `Engine` は `close()` でリソースを必ず解放する

### TypeScript / JavaScript (saizeriya.js関連)
- async/awaitを使用
- Prettier / ESLint標準設定に準拠

### 全般
- コメントおよびコミットメッセージは日本語
- README.mdはアーキテクチャ変更時に必ず同期更新

## ビルド・実行コマンド

### Android (Kotlin)
```bash
./gradlew build    # ビルド
./gradlew test     # テスト実行
```

### LiteRT-LM CLI（プロトタイピング用）
```bash
uv tool install litert-lm
litert-lm run --from-huggingface-repo=litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it.litertlm --prompt="..."
litert-lm benchmark my-model -p 256 -d 256
```

### saizeriya.js
```bash
npm install
```

## 文脈データソース
| データ | API | 取得内容 |
|--------|-----|----------|
| 健康データ | [Health Connect](https://developer.android.com/health-connect) | 歩数・消費カロリー・睡眠時間（直近24h） |
| 天候 | Weather API | 現在地の天候・気温 |
| 購買行動 | Gmail API | 決済通知メールから昨日の食事・出費傾向 |

## 残存課題
1. **注文API検証**: `saizeriya.js` が公式モバイルオーダーのエンドポイントに直接接続可能か調査が必要。
2. **モデル選定**: Gemma 4-E4B / E2B のメモリ使用量とGalaxy S24上での推論速度のベンチマーク。
3. **プロンプト設計**: 文脈JSON + メニュー全件をトークン上限内に収めるプロンプトテンプレートの策定。
