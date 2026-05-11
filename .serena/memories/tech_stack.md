# Tech Stack

- **Android App**: Kotlin (UI・文脈収集・LLM呼び出し・注文実行の統合)
- **推論エンジン**: LiteRT-LM (Qualcomm NPU DelegateによるオンデバイスLLM推論)
- **LLMモデル**: Gemma 4-E4B / Gemma 4-E2B 等 (`.litertlm` 形式、HuggingFaceから取得)
- **メニュー・注文**: saizeriya.js (TypeScript / JavaScript, メニューJSON取得・注文実行)

文脈データソースとして、Health Connect (歩数・カロリー・睡眠)、Weather API (天候・気温)、Gmail API (決済通知からの出費・食事傾向) を利用します。