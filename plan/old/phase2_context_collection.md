# Phase 2: 文脈データ収集

## 概要

ユーザーの生活文脈（健康データ・天候・購買履歴）を収集し、LLMプロンプトに注入する「文脈JSON」を生成する。
3つのデータソース（Health Connect, Weather API, Gmail API）それぞれのProviderを実装し、
`ContextCollector` で統合する。

## 前提条件

- Phase 0 が完了していること
- Galaxy S24 実機で Health Connect アプリがインストール済みであること
- OpenWeatherMap API キーを取得済みであること

## 想定期間: 4日

---

## 実装タスク一覧

+ [x] Task 2-1: 文脈データモデル定義（ContextData）
+ [x] Task 2-2: HealthDataProvider 実装（Health Connect API）
+ [x] Task 2-3: WeatherProvider 実装（OpenWeatherMap API）
+ [x] Task 2-4: GmailProvider 実装（Gmail API）
+ [x] Task 2-5: ContextCollector 統合
+ [x] Task 2-6: ユニットテスト

---

## Task 2-1: 文脈データモデル定義

### ファイル: `data/model/ContextData.kt`

```kotlin
package com.example.saizeriya.data.model

import kotlinx.serialization.Serializable

/**
 * LLMに渡す文脈データの統合モデル。
 * 各Providerが個別にデータを収集し、ContextCollectorが統合する。
 */
@Serializable
data class ContextData(
    /** 健康データ */
    val health: HealthData? = null,
    /** 天候データ */
    val weather: WeatherData? = null,
    /** 購買行動データ */
    val purchase: PurchaseData? = null,
    /** 収集日時（ISO 8601形式） */
    val collectedAt: String
)

@Serializable
data class HealthData(
    /** 直近24時間の歩数 */
    val stepsLast24h: Int,
    /** 直近24時間の消費カロリー（kcal） */
    val caloriesBurnedLast24h: Double,
    /** 昨夜の睡眠時間（分） */
    val sleepDurationMinutes: Int
)

@Serializable
data class WeatherData(
    /** 天候の説明（例: "晴れ", "曇り", "雨"） */
    val description: String,
    /** 現在気温（℃） */
    val temperatureCelsius: Double,
    /** 体感温度（℃） */
    val feelsLikeCelsius: Double,
    /** 湿度（%） */
    val humidity: Int
)

@Serializable
data class PurchaseData(
    /** 昨日の食事内容（メールから抽出） */
    val recentMeals: List<String>,
    /** 直近の出費傾向（円） */
    val recentSpending: Int
)
```

---

## Task 2-2: HealthDataProvider 実装

### ファイル: `context/HealthDataProvider.kt`

```kotlin
package com.example.saizeriya.context

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.saizeriya.data.model.HealthData
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Health Connect APIから健康データを取得するProvider。
 *
 * 使用前にパーミッション確認が必要:
 * - android.permission.health.READ_STEPS
 * - android.permission.health.READ_TOTAL_CALORIES_BURNED
 * - android.permission.health.READ_SLEEP
 */
class HealthDataProvider(private val context: Context) {

    private val healthConnectClient: HealthConnectClient by lazy {
        HealthConnectClient.getOrCreate(context)
    }

    /**
     * 直近24時間の健康データを収集する。
     *
     * @return HealthData（歩数・消費カロリー・睡眠時間）
     * @throws Exception パーミッション未付与またはHealth Connect未インストール時
     */
    suspend fun collect(): HealthData {
        val now = Instant.now()
        val yesterday = now.minus(24, ChronoUnit.HOURS)
        val timeRange = TimeRangeFilter.between(yesterday, now)

        // 歩数取得
        val steps = readSteps(timeRange)

        // 消費カロリー取得
        val calories = readCalories(timeRange)

        // 睡眠時間取得
        val sleepMinutes = readSleep(timeRange)

        return HealthData(
            stepsLast24h = steps,
            caloriesBurnedLast24h = calories,
            sleepDurationMinutes = sleepMinutes
        )
    }

    /**
     * 直近24時間の合計歩数を取得する。
     */
    private suspend fun readSteps(timeRange: TimeRangeFilter): Int {
        val request = ReadRecordsRequest(
            recordType = StepsRecord::class,
            timeRangeFilter = timeRange
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sumOf { it.count.toInt() }
    }

    /**
     * 直近24時間の消費カロリーを取得する。
     */
    private suspend fun readCalories(timeRange: TimeRangeFilter): Double {
        val request = ReadRecordsRequest(
            recordType = TotalCaloriesBurnedRecord::class,
            timeRangeFilter = timeRange
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sumOf {
            it.energy.inKilocalories
        }
    }

    /**
     * 直近24時間の睡眠時間（分）を取得する。
     */
    private suspend fun readSleep(timeRange: TimeRangeFilter): Int {
        val request = ReadRecordsRequest(
            recordType = SleepSessionRecord::class,
            timeRangeFilter = timeRange
        )
        val response = healthConnectClient.readRecords(request)
        return response.records.sumOf { record ->
            ChronoUnit.MINUTES.between(record.startTime, record.endTime).toInt()
        }
    }

    companion object {
        /** Health Connect が利用可能か確認する */
        fun isAvailable(context: Context): Boolean {
            val status = HealthConnectClient.getSdkStatus(context)
            return status == HealthConnectClient.SDK_AVAILABLE
        }
    }
}
```

### パーミッション要求の実装（UI側で呼び出し）

```kotlin
// Activity/Composable で呼び出す
val HEALTH_PERMISSIONS = setOf(
    HealthPermission.getReadPermission(StepsRecord::class),
    HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    HealthPermission.getReadPermission(SleepSessionRecord::class)
)

val requestPermissions = rememberLauncherForActivityResult(
    PermissionController.createRequestPermissionResultContract()
) { granted ->
    if (granted.containsAll(HEALTH_PERMISSIONS)) {
        // パーミッション付与済み → データ収集開始
    }
}
```

---

## Task 2-3: WeatherProvider 実装

### ファイル: `context/WeatherProvider.kt`

```kotlin
package com.example.saizeriya.context

import com.example.saizeriya.data.model.WeatherData
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * OpenWeatherMap APIから天候データを取得するProvider。
 *
 * @param apiKey OpenWeatherMap APIキー
 */
class WeatherProvider(private val apiKey: String) {

    private val httpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /**
     * 指定座標の現在の天候データを取得する。
     *
     * @param latitude 緯度
     * @param longitude 経度
     * @return WeatherData
     */
    suspend fun collect(latitude: Double, longitude: Double): WeatherData {
        val response: OpenWeatherResponse = httpClient.get(
            "https://api.openweathermap.org/data/2.5/weather"
        ) {
            parameter("lat", latitude)
            parameter("lon", longitude)
            parameter("appid", apiKey)
            parameter("units", "metric")  // 摂氏
            parameter("lang", "ja")       // 日本語
        }.body()

        return WeatherData(
            description = response.weather.firstOrNull()?.description ?: "不明",
            temperatureCelsius = response.main.temp,
            feelsLikeCelsius = response.main.feelsLike,
            humidity = response.main.humidity
        )
    }

    fun close() {
        httpClient.close()
    }
}

// OpenWeatherMap APIレスポンスモデル（内部用）
@Serializable
private data class OpenWeatherResponse(
    val weather: List<WeatherDescription>,
    val main: MainWeather
)

@Serializable
private data class WeatherDescription(
    val description: String
)

@Serializable
private data class MainWeather(
    val temp: Double,
    @kotlinx.serialization.SerialName("feels_like")
    val feelsLike: Double,
    val humidity: Int
)
```

### 位置情報の取得（前提）

```kotlin
// FusedLocationProviderClient で現在位置を取得
// （android.permission.ACCESS_FINE_LOCATION が必要）
val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
fusedLocationClient.lastLocation.addOnSuccessListener { location ->
    if (location != null) {
        val weather = weatherProvider.collect(location.latitude, location.longitude)
    }
}
```

---

## Task 2-4: GmailProvider 実装

### ファイル: `context/GmailProvider.kt`

```kotlin
package com.example.saizeriya.context

import com.example.saizeriya.data.model.PurchaseData

/**
 * Gmail APIから決済通知メールを解析し、購買行動データを抽出するProvider。
 *
 * 主な対象メール:
 * - クレジットカード利用通知
 * - 電子マネー決済通知
 * - フードデリバリーの注文確認
 *
 * 注意: Gmail API の OAuth2 認証が必要。
 * Google Cloud Console でプロジェクトを作成し、Gmail API を有効化すること。
 */
class GmailProvider {

    /**
     * 直近24時間の決済通知メールから購買行動データを抽出する。
     *
     * 実装手順:
     * 1. Gmail API でメールを検索（クエリ: "決済" OR "利用" OR "注文"）
     * 2. メール本文からレストラン名・金額を正規表現で抽出
     * 3. PurchaseData に変換
     *
     * @return PurchaseData（食事内容・出費傾向）
     */
    suspend fun collect(): PurchaseData {
        // TODO: Gmail API 統合
        // MVP では空データを返し、Phase 7 で本実装する
        return PurchaseData(
            recentMeals = emptyList(),
            recentSpending = 0
        )
    }
}
```

> **MVP戦略**: Gmail API統合は複雑（OAuth2認証が必要）なため、
> Phase 2 では**スタブ実装**とし、Phase 7 で本実装する。
> Health Connect と Weather API を優先する。

---

## Task 2-5: ContextCollector 統合

### ファイル: `context/ContextCollector.kt`

```kotlin
package com.example.saizeriya.context

import android.content.Context
import com.example.saizeriya.data.model.ContextData
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant

/**
 * 全文脈データソースを統合し、LLMプロンプト用の ContextData を生成する。
 *
 * 各Providerのデータ収集を並列実行し、失敗したソースはnullとして扱う（部分的な文脈でも推論可能）。
 */
class ContextCollector(
    private val healthProvider: HealthDataProvider,
    private val weatherProvider: WeatherProvider,
    private val gmailProvider: GmailProvider
) {
    /**
     * 全データソースから文脈データを並列収集する。
     *
     * @param latitude 現在の緯度
     * @param longitude 現在の経度
     * @return 統合された ContextData
     */
    suspend fun collectAll(latitude: Double, longitude: Double): ContextData =
        coroutineScope {
            // 3つのデータソースを並列に収集
            val healthDeferred = async {
                runCatching { healthProvider.collect() }.getOrNull()
            }
            val weatherDeferred = async {
                runCatching { weatherProvider.collect(latitude, longitude) }.getOrNull()
            }
            val purchaseDeferred = async {
                runCatching { gmailProvider.collect() }.getOrNull()
            }

            ContextData(
                health = healthDeferred.await(),
                weather = weatherDeferred.await(),
                purchase = purchaseDeferred.await(),
                collectedAt = Instant.now().toString()
            )
        }

    /**
     * 文脈データをLLMプロンプト用のJSON文字列に変換する。
     */
    fun toPromptJson(contextData: ContextData): String {
        return kotlinx.serialization.json.Json.encodeToString(
            ContextData.serializer(),
            contextData
        )
    }
}
```

---

## Task 2-6: ユニットテスト

### テスト対象と方針

| テスト | 方針 |
|--------|------|
| HealthDataProvider | Health Connect のモック（Robolectric or Instrumentedテスト） |
| WeatherProvider | Ktor MockEngine でHTTPレスポンスをモック |
| GmailProvider | スタブなのでスキップ |
| ContextCollector | 各Providerをモックして統合テスト |

### WeatherProvider テスト例

```kotlin
@Test
fun `天候データを正しくパースできる`() = runTest {
    val mockEngine = MockEngine {
        respond(
            content = """{
                "weather": [{"description": "晴れ"}],
                "main": {"temp": 25.0, "feels_like": 27.0, "humidity": 60}
            }""",
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }
    // テスト用のWeatherProviderを作成（MockEngine注入が必要）
    // ...
}
```

---

## 完了条件

+ [x] `ContextData`, `HealthData`, `WeatherData`, `PurchaseData` データクラスが定義済み
+ [x] `HealthDataProvider` が Health Connect から歩数・カロリー・睡眠データを取得可能
+ [x] `WeatherProvider` が OpenWeatherMap API から天候データを取得可能
+ [x] `GmailProvider` のスタブ実装が完了
+ [x] `ContextCollector` が全データソースを並列収集し ContextData に統合
+ [x] ユニットテストが通過

## 参考リンク

- [Health Connect 開発ガイド](https://developer.android.com/health-and-fitness/guides/health-connect/develop/read-data)
- [Health Connect 歩数](https://developer.android.com/health-and-fitness/guides/health-connect/develop/steps)
- [OpenWeatherMap API](https://openweathermap.org/current)
- [Gmail API Android](https://developers.google.com/gmail/api)
