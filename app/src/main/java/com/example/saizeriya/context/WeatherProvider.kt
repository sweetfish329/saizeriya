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
open class WeatherProvider(private val apiKey: String, private val clientEngine: io.ktor.client.engine.HttpClientEngine = OkHttp.create()) {

    private val httpClient = HttpClient(clientEngine) {
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
    open suspend fun collect(latitude: Double, longitude: Double): WeatherData {
        val response: OpenWeatherResponse = httpClient.get(
            "https://api.openweathermap.org/data/2.5/weather"
        ) {
            url {
                parameters.append("lat", latitude.toString())
                parameters.append("lon", longitude.toString())
                parameters.append("appid", apiKey)
                parameters.append("units", "metric")  // 摂氏
                parameters.append("lang", "ja")       // 日本語
            }
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
    val weather: List<WeatherDescription> = emptyList(),
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
