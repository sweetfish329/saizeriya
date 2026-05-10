package com.example.saizeriya.context

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherProviderTest {

    @Test
    fun `collect parses weather data correctly`() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("https://api.openweathermap.org/data/2.5/weather?lat=35.6895&lon=139.6917&appid=test_api_key&units=metric&lang=ja", request.url.toString())
            respond(
                content = """
                    {
                        "weather": [{"description": "晴れ"}],
                        "main": {"temp": 25.0, "feels_like": 27.0, "humidity": 60}
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val weatherProvider = WeatherProvider(apiKey = "test_api_key", clientEngine = mockEngine)
        val weatherData = weatherProvider.collect(latitude = 35.6895, longitude = 139.6917)

        assertEquals("晴れ", weatherData.description)
        assertEquals(25.0, weatherData.temperatureCelsius, 0.0)
        assertEquals(27.0, weatherData.feelsLikeCelsius, 0.0)
        assertEquals(60, weatherData.humidity)
    }

    @Test
    fun `collect handles unknown description`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    {
                        "weather": [],
                        "main": {"temp": 20.0, "feels_like": 20.0, "humidity": 50}
                    }
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val weatherProvider = WeatherProvider(apiKey = "test_api_key", clientEngine = mockEngine)
        val weatherData = weatherProvider.collect(latitude = 35.6895, longitude = 139.6917)

        assertEquals("不明", weatherData.description)
        assertEquals(20.0, weatherData.temperatureCelsius, 0.0)
        assertEquals(20.0, weatherData.feelsLikeCelsius, 0.0)
        assertEquals(50, weatherData.humidity)
    }
}
