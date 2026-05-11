# Suggested Commands

**ビルド・実行 (Android / Kotlin):**
- `./gradlew build` : プロジェクトのビルド
- `./gradlew test` : テストの実行
※ Windows環境の場合は、`.\gradlew.bat build` や `.\gradlew.bat test` を適宜使用。

**Node.js (saizeriya.js):**
- `npm install` : 依存関係のインストール

**LiteRT-LM CLI（プロトタイピング用）:**
- `uv tool install litert-lm` : ツールインストール
- `litert-lm run --from-huggingface-repo=litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it.litertlm --prompt="..."` : 推論テスト
- `litert-lm benchmark my-model -p 256 -d 256` : ベンチマーク実行

**Windows システムユーティリティ:**
- Windows環境のため、ファイルの検索・閲覧等にはSerenaのツール（find_symbol, replace_content, search_for_pattern, view_file, grep_search 等）を優先して使用すること。
- PowerShell等の標準コマンドが利用可能だが、原則として専用ツールを使うこと。