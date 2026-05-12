# saizeriya.js のセットアップ

このドキュメントでは、メニューデータの取得と注文実行を行う `saizeriya.js` モジュールのセットアップ手順について説明します。

## 概要
`saizeriya.js` は [pnsk-lab/saizeriya](https://github.com/pnsk-lab/saizeriya) リポジトリの仕様に基づき、メニューJSONの取得と、saizeriya互換サーバーを経由した注文実行を担います。

## セットアップ手順

Node.jsがインストールされている環境で、対象のディレクトリ（存在する場合）またはリポジトリ内で以下のコマンドを実行して依存関係をインストールします。

```bash
# パッケージのインストール
npm install
```

## コーディング規約
- `async/await` を積極的に使用する
- コードフォーマットは Prettier / ESLint 標準設定に準拠する
