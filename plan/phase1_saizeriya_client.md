# Phase 1: サイゼリヤAPI統合（メニュー取得・注文実行）

## 概要

[saizeriya.js](https://github.com/pnsk-lab/saizeriya) のHTTPプロトコルを解析し、
Kotlin/Ktorで同等の機能を持つ `SaizeriyaClient` を実装する。
メニューデータの取得と注文実行の2つの主要機能を提供する。

## 前提条件

- Phase 0 が完了していること
- saizeriya.js の [GitHubリポジトリ](https://github.com/pnsk-lab/saizeriya) のソースコードにアクセス可能であること

## 想定期間: 3日

---

## 実装タスク一覧

- [ ] Task 1-1: saizeriya.js のHTTPプロトコル解析
- [ ] Task 1-2: データモデル定義（MenuItem, CartItem, ClientState）
- [ ] Task 1-3: SaizeriyaClient の実装
- [ ] Task 1-4: メニューデータ取得機能
- [ ] Task 1-5: 注文実行機能（addItem, submitOrder）
- [ ] Task 1-6: ユニットテスト

---

## Task 1-1: saizeriya.js HTTPプロトコル解析

### 調査対象

saizeriya.js の `packages/client/` と `packages/server/` のソースコードを読み、
以下のHTTPエンドポイントとリクエスト形式を特定する。

### 必要な情報

1. **QR URL の構造**: `https://example.com/saizeriya3/qr?table=abc123`
   - `shopId`, `tableNo`, `sessionId` の抽出方法

2. **セッション確立**: QR URLアクセス時のHTTPリクエスト/レスポンス
   - エンドポイント、HTTPメソッド、ヘッダー、ボディ

3. **人数登録**: `peopleCount` の送信方法

4. **カート操作**: アイテム追加のリクエスト形式
   - `code` (メニューコード), `count` (数量) の送信

5. **注文送信**: `submitOrder` のリクエスト形式

### 調査方法

```bash
# saizeriya.js リポジトリをクローン
git clone https://github.com/pnsk-lab/saizeriya.git /tmp/saizeriya-ref

# クライアント実装を確認
cat /tmp/saizeriya-ref/packages/client/src/*.ts

# サーバー実装からHTTPルートを確認
cat /tmp/saizeriya-ref/packages/server/src/*.ts
```

### 成果物

HTTPプロトコル仕様書（以下の形式でコメントに記載）:

```
POST /saizeriya3/api/session
  Body: { shopId, tableNo }
  Response: { sessionId, pageKind, ... }

POST /saizeriya3/api/order/add
  Body: { sessionId, itemCode, count }
  Response: { cart: [...] }

POST /saizeriya3/api/order/submit
  Body: { sessionId }
  Response: { pageKind: "call" }
```

> **注意**: 上記は推定構造。実際のsaizeriya.jsソースから正確なAPI仕様を抽出すること。

---

## Task 1-2: データモデル定義

### ファイル: `data/model/MenuItem.kt`

```kotlin
package com.example.saizeriya.data.model

import kotlinx.serialization.Serializable

/**
 * サイゼリヤのメニュー1品を表すデータクラス。
 * LLMプロンプトに注入する際にもこのモデルを使用する。
 */
@Serializable
data class MenuItem(
    /** メニューコード（例: "1202"） - 注文時のキー */
    val code: String,
    /** メニュー名（例: "小ｴﾋﾞのｻﾗﾀﾞ"） */
    val name: String,
    /** 価格（税込、円） */
    val price: Int,
    /** カテゴリ（例: "サラダ", "パスタ", "ドリンク"） */
    val category: String
)
```

### ファイル: `data/model/CartItem.kt`

```kotlin
package com.example.saizeriya.data.model

import kotlinx.serialization.Serializable

/**
 * カート内の1アイテム。
 */
@Serializable
data class CartItem(
    /** メニューコード */
    val code: String,
    /** メニュー名 */
    val name: String,
    /** 価格 */
    val price: Int,
    /** 数量 */
    val count: Int,
    /** 再注文フラグ */
    val reorder: Int = 0
)
```

### ファイル: `data/model/OrderSession.kt`

```kotlin
package com.example.saizeriya.data.model

import kotlinx.serialization.Serializable

/**
 * サイゼリヤ注文セッションの状態。
 */
@Serializable
data class OrderSession(
    /** 店舗ID */
    val shopId: String,
    /** テーブル番号 */
    val tableNo: String,
    /** セッションID */
    val sessionId: String,
    /** 現在のページ種類: "menu", "call" 等 */
    val pageKind: String,
    /** 人数 */
    val peopleCount: Int,
    /** 次のリクエストID */
    val nextId: Int = 0,
    /** カート内容 */
    val cart: List<CartItem> = emptyList()
)
```

---

## Task 1-3: SaizeriyaClient 実装

### ファイル: `order/SaizeriyaClient.kt`

```kotlin
package com.example.saizeriya.order

import com.example.saizeriya.data.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/**
 * サイゼリヤモバイルオーダーHTTPクライアント。
 * saizeriya.js の createClient と同等の機能を提供する。
 *
 * 使用例:
 * ```
 * val client = SaizeriyaClient()
 * val session = client.createSession(qrUrl = "https://...", peopleCount = 2)
 * client.addItem(session.sessionId, "1202", count = 1)
 * client.submitOrder(session.sessionId)
 * client.close()
 * ```
 */
class SaizeriyaClient {

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }

    /**
     * QR URLからセッションを作成する。
     *
     * @param qrUrl サイゼリヤQRコードのURL
     * @param peopleCount 人数
     * @return 作成されたOrderSession
     */
    suspend fun createSession(qrUrl: String, peopleCount: Int): OrderSession {
        // TODO: Task 1-1 の解析結果に基づいて実装
        // 1. QR URLをパースして shopId, tableNo を抽出
        // 2. セッション確立のHTTPリクエストを送信
        // 3. レスポンスをOrderSessionに変換
        throw NotImplementedError("Task 1-1 完了後に実装")
    }

    /**
     * カートにアイテムを追加する。
     *
     * @param sessionId セッションID
     * @param code メニューコード（例: "1202"）
     * @param count 数量（デフォルト: 1）
     * @return 更新されたカート
     */
    suspend fun addItem(sessionId: String, code: String, count: Int = 1): List<CartItem> {
        // TODO: HTTPリクエストでアイテム追加
        throw NotImplementedError("Task 1-1 完了後に実装")
    }

    /**
     * 注文を送信する。
     *
     * @param sessionId セッションID
     * @return 注文完了後のセッション状態
     */
    suspend fun submitOrder(sessionId: String): OrderSession {
        // TODO: HTTPリクエストで注文送信
        throw NotImplementedError("Task 1-1 完了後に実装")
    }

    /**
     * HTTPクライアントを閉じてリソースを解放する。
     */
    fun close() {
        httpClient.close()
    }
}
```

### 実装時の重要ポイント

1. **QR URLのパース**: URL パラメータから `shopId` と `tableNo` を抽出する
   ```kotlin
   // 例: https://example.com/saizeriya3/qr?table=abc123
   val uri = Uri.parse(qrUrl)
   val tableParam = uri.getQueryParameter("table")
   ```

2. **Cookie/セッション管理**: saizeriya.jsのHTTPリクエストにCookie等が必要な場合、Ktorの `HttpCookies` プラグインを使用
   ```kotlin
   install(HttpCookies)
   ```

3. **エラーハンドリング**: ネットワークエラー、タイムアウト、サーバーエラーの適切な処理

4. **スレッドセーフティ**: saizeriya.jsではキューロックで直列化しているため、Kotlinでは `Mutex` を使用
   ```kotlin
   private val mutex = Mutex()
   suspend fun addItem(...) = mutex.withLock { ... }
   ```

---

## Task 1-4: メニューデータ取得

### ファイル: `data/repository/MenuRepository.kt`

```kotlin
package com.example.saizeriya.data.repository

import com.example.saizeriya.data.model.MenuItem

/**
 * メニューデータの取得・管理を行うリポジトリ。
 *
 * メニューデータの取得方法（優先順位）:
 * 1. saizeriya.jsサーバーのAPIからリアルタイム取得
 * 2. ローカルにバンドルしたJSONファイルをフォールバックとして使用
 */
class MenuRepository {

    /**
     * 全メニューを取得する。
     * メニュー数は約100〜130品。
     *
     * @return メニューリスト
     */
    suspend fun getAllMenuItems(): List<MenuItem> {
        // TODO: saizeriya.jsのメニュー取得API呼び出し
        // フォールバック: assets/menu.json からローカル読み込み
        throw NotImplementedError("実装予定")
    }

    /**
     * カテゴリ別にメニューをグループ化する。
     *
     * @return カテゴリ名 → メニューリスト のマップ
     */
    suspend fun getMenuByCategory(): Map<String, List<MenuItem>> {
        return getAllMenuItems().groupBy { it.category }
    }

    /**
     * メニューをLLMプロンプト用のJSON文字列に変換する。
     * 各メニューは { "code": "1202", "name": "小ｴﾋﾞのｻﾗﾀﾞ", "price": 350, "category": "サラダ" } 形式。
     *
     * @return JSON文字列
     */
    suspend fun toPromptJson(): String {
        val items = getAllMenuItems()
        return kotlinx.serialization.json.Json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(MenuItem.serializer()),
            items
        )
    }
}
```

### フォールバック用ローカルメニュー

`app/src/main/assets/menu.json` にバンドルするサンプル:

```json
[
  { "code": "1202", "name": "小ｴﾋﾞのｻﾗﾀﾞ", "price": 350, "category": "サラダ" },
  { "code": "3201", "name": "ﾃｨﾗﾐｽｸﾗｼｺ", "price": 300, "category": "デザート" },
  { "code": "2101", "name": "ﾐﾗﾉ風ﾄﾞﾘｱ", "price": 300, "category": "ドリア・グラタン" }
]
```

> **重要**: このファイルはsaizeriya.jsリポジトリの `scripts/` ディレクトリにある
> メニュースクレイピングスクリプトで最新データを生成して更新すること。

---

## Task 1-5: 注文実行機能の実装

saizeriya.js の API メソッドとKotlin実装の対応表:

| saizeriya.js | Kotlin (SaizeriyaClient) | 説明 |
|---|---|---|
| `createClient({ qrURLSource, peopleCount })` | `createSession(qrUrl, peopleCount)` | セッション作成 |
| `client.addItem('1202')` | `addItem(sessionId, "1202")` | カートにアイテム追加 |
| `client.addItem('1202', { count: 2 })` | `addItem(sessionId, "1202", 2)` | 数量指定で追加 |
| `client.submitOrder()` | `submitOrder(sessionId)` | 注文送信 |
| `client.getState()` | `getState(sessionId)` | 状態取得 |

---

## Task 1-6: ユニットテスト

### ファイル: `test/kotlin/com/example/saizeriya/order/SaizeriyaClientTest.kt`

```kotlin
package com.example.saizeriya.order

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*

class SaizeriyaClientTest {

    @Test
    fun `QR URLからshopIdとtableNoを正しく抽出できる`() = runTest {
        // TODO: QR URLパーステスト
    }

    @Test
    fun `セッション作成が正しく動作する`() = runTest {
        // TODO: Ktorモッククライアントでセッション作成テスト
    }

    @Test
    fun `カートへのアイテム追加が正しく動作する`() = runTest {
        // TODO: アイテム追加テスト
    }

    @Test
    fun `空カートでsubmitOrderするとエラー`() = runTest {
        // TODO: 空カートエラーテスト
    }
}
```

### Ktor モッククライアントの使い方

```kotlin
val mockEngine = MockEngine { request ->
    when (request.url.encodedPath) {
        "/saizeriya3/api/session" -> respond(
            content = """{"sessionId":"test123","pageKind":"menu"}""",
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
        else -> respondError(HttpStatusCode.NotFound)
    }
}
```

---

## 完了条件

- [ ] saizeriya.js のHTTPプロトコルが文書化されている
- [ ] `MenuItem`, `CartItem`, `OrderSession` データクラスが定義済み
- [ ] `SaizeriyaClient` が createSession, addItem, submitOrder を実装
- [ ] `MenuRepository` がメニュー取得・JSON変換を実装
- [ ] Ktor MockEngineを使ったユニットテストが通過
- [ ] `./gradlew test` が成功

## 参考リンク

- [saizeriya.js GitHub](https://github.com/pnsk-lab/saizeriya)
- [saizeriya.js client README](https://github.com/pnsk-lab/saizeriya/blob/main/packages/client/README.md)
- [Ktor Client ドキュメント](https://ktor.io/docs/client.html)
