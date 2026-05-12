# LiteRT-LM のセットアップとプロトタイピング

このドキュメントでは、オンデバイスLLM推論エンジンである LiteRT-LM (Qualcomm NPU Delegate) のプロトタイピングとモデル検証のための手順を説明します。

## CLI ツールのインストール

Python環境 (uv がインストールされていることを推奨) で以下のコマンドを実行し、CLI ツールをインストールします。

```bash
uv tool install litert-lm
```

## モデルの取得と実行

HuggingFace からモデルを取得し、ローカルで実行する例です。

```bash
# Gemma 4-E2B モデルの取得とプロンプト実行例
litert-lm run --from-huggingface-repo=litert-community/gemma-4-E2B-it-litert-lm gemma-4-E2B-it.litertlm --prompt="こんにちは、元気ですか？"
```

## ベンチマークの実行

モデルのパフォーマンスを計測するには、以下のコマンドを使用します。

```bash
# プロンプト長 256、出力長 256 でのベンチマーク
litert-lm benchmark my-model -p 256 -d 256
```
