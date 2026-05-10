package com.example.saizeriya.context

import android.content.Context
import com.example.saizeriya.data.model.HealthData
import com.example.saizeriya.data.model.PurchaseData
import com.example.saizeriya.data.model.WeatherData
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito

class ContextCollectorTest {

    @Test
    fun `collectAll gathers data from all providers successfully`() = runTest {
        val mockContext = Mockito.mock(Context::class.java)

        val healthProvider = object : HealthDataProvider(mockContext) {
            override suspend fun collect(): HealthData = HealthData(5000, 300.0, 480)
        }
        val weatherProvider = object : WeatherProvider("dummy_key") {
            override suspend fun collect(latitude: Double, longitude: Double): WeatherData = WeatherData("曇り", 22.0, 22.5, 55)
        }
        val gmailProvider = object : GmailProvider() {
            override suspend fun collect(): PurchaseData = PurchaseData(listOf("ミラノ風ドリア"), 1500)
        }

        val collector = ContextCollector(healthProvider, weatherProvider, gmailProvider)
        val contextData = collector.collectAll(35.0, 139.0)

        assertNotNull(contextData.health)
        assertEquals(5000, contextData.health?.stepsLast24h)

        assertNotNull(contextData.weather)
        assertEquals("曇り", contextData.weather?.description)

        assertNotNull(contextData.purchase)
        assertEquals(1500, contextData.purchase?.recentSpending)

        assertNotNull(contextData.collectedAt)
    }

    @Test
    fun `collectAll handles partial failures gracefully`() = runTest {
        val mockContext = Mockito.mock(Context::class.java)

        val healthProvider = object : HealthDataProvider(mockContext) {
            override suspend fun collect(): HealthData = throw RuntimeException("Health Connect not available")
        }
        val weatherProvider = object : WeatherProvider("dummy_key") {
            override suspend fun collect(latitude: Double, longitude: Double): WeatherData = WeatherData("曇り", 22.0, 22.5, 55)
        }
        val gmailProvider = object : GmailProvider() {
            override suspend fun collect(): PurchaseData = throw RuntimeException("Auth error")
        }

        val collector = ContextCollector(healthProvider, weatherProvider, gmailProvider)
        val contextData = collector.collectAll(35.0, 139.0)

        assertNull(contextData.health)
        assertNotNull(contextData.weather)
        assertNull(contextData.purchase)
    }

    @Test
    fun `toPromptJson serializes correctly`() = runTest {
        val mockContext = Mockito.mock(Context::class.java)

        val healthProvider = object : HealthDataProvider(mockContext) {
            override suspend fun collect(): HealthData = HealthData(5000, 300.0, 480)
        }
        val weatherProvider = object : WeatherProvider("dummy_key") {
            override suspend fun collect(latitude: Double, longitude: Double): WeatherData = WeatherData("曇り", 22.0, 22.5, 55)
        }
        val gmailProvider = object : GmailProvider() {
            override suspend fun collect(): PurchaseData = PurchaseData(listOf("ミラノ風ドリア"), 1500)
        }

        val collector = ContextCollector(healthProvider, weatherProvider, gmailProvider)
        val contextData = collector.collectAll(35.0, 139.0)

        val json = collector.toPromptJson(contextData)

        assertTrue(json.contains("stepsLast24h\":5000"))
        assertTrue(json.contains("description\":\"曇り\""))
        assertTrue(json.contains("recentSpending\":1500"))
    }
}
