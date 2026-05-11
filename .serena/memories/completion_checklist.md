# Completion Checklist

タスク完了前に以下を確認すること：
- [ ] すべてのコードコメントおよびGitのコミットメッセージは日本語で書かれているか？
- [ ] アーキテクチャや重要な設計に変更があった場合、`README.md` や関連するドキュメント (`AGENTS.md`等) も更新されているか？
- [ ] Kotlinの公開APIには明示的な型注釈が付与されているか？
- [ ] ML推論やネットワーク通信の処理は、メインスレッドをブロックせず `Dispatchers.IO` やCoroutinesで行われているか？
- [ ] LiteRT-LMの `Engine` リソースは適切に `close()` されているか？
- [ ] （JS側）`async/await`が使用され、Prettier/ESLintのフォーマットに準拠しているか？
- [ ] 必要に応じて `./gradlew build` または `./gradlew test` を実行し、ビルドエラーやテスト失敗がないか確認したか？