# Style and Conventions

**全般:**
- チャット、コメント、コミットメッセージはすべて「日本語」を使用すること。
- アーキテクチャ変更時は必ず README.md を同期更新すること。

**Kotlin:**
- 公式Kotlinコーディング規約に準拠。
- 公開APIには明示的な型注釈を付与。
- ML推論・ネットワーク通信などの重い処理は、バックグラウンドスレッド（`Dispatchers.IO` / Coroutines）で実行すること。
- LiteRT-LMの `Engine` は `close()` でリソースを必ず解放すること。

**TypeScript / JavaScript (saizeriya.js関連):**
- 非同期処理には async/await を使用。
- Prettier / ESLint 標準設定に準拠すること。