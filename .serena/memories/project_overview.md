# Project Overview

Galaxy S24 (Snapdragon 8 Gen 3) 上で完全にローカル動作する、文脈駆動型サイゼリヤ自動注文システム。
ユーザーの生活文脈（健康データ・天候・購買履歴）をLLMに入力し、最適なメニューを自動選定・注文します。

**重要な設計判断:**
- 中間DB不使用: メニュー数が約100〜130品と小規模のため、ベクトルDB/SQLiteを使わずJSONをそのままLLMコンテキストに注入。
- Termux不要: LiteRT-LMのKotlinネイティブAPIを使い、Androidアプリ内で推論が完結。
- OneComp非採用: LiteRT-LM公式の量子化パイプラインを使用。