package com.example.saizeriya.context

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
