# Tech Stack

- **Android App**: Kotlin (for UI, intent orchestration, and data collection from APIs).
- **On-Device Inference**: LiteRT-LM (Google's LLM engine optimized for edge devices, using Qualcomm NPU direct delegate).
- **LLM Format**: `.litertlm` (converted and bundled models like Gemma 3n/4 from HuggingFace).
- **Ordering/CLI Backend**: `saizeriya.js` (TypeScript/JavaScript CLI for interacting with Saizeriya compatible endpoints).