package com.example.saizeriya.data.model

import kotlinx.serialization.Serializable

@Serializable
data class HealthData(
    val stepsLast24h: Int,
    val caloriesBurnedLast24h: Double,
    val sleepDurationMinutes: Int
)

@Serializable
data class WeatherData(
    val description: String,
    val temperatureCelsius: Double,
    val feelsLikeCelsius: Double,
    val humidity: Int
)

@Serializable
data class PurchaseData(
    val recentMeals: List<String>,
    val recentSpending: Int
)

@Serializable
data class ContextData(
    val health: HealthData?,
    val weather: WeatherData?,
    val purchase: PurchaseData?,
    val collectedAt: String
)
