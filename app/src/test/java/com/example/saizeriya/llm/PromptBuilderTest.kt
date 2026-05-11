package com.example.saizeriya.llm

import com.example.saizeriya.data.model.ContextData
import com.example.saizeriya.data.model.HealthData
import com.example.saizeriya.data.model.MenuItem
import com.example.saizeriya.data.model.PurchaseData
import com.example.saizeriya.data.model.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    private val builder = PromptBuilder()

    private val sampleContextData = ContextData(
        health = HealthData(stepsLast24h = 8500, caloriesBurnedLast24h = 2100.0, sleepDurationMinutes = 420),
        weather = WeatherData(description = "晴れ", temperatureCelsius = 28.5, feelsLikeCelsius = 30.0, humidity = 65),
        purchase = PurchaseData(recentMeals = listOf("焼肉"), recentSpending = 5000),
        collectedAt = "2024-05-11T12:00:00Z"
    )

    private val sampleMenuItems = listOf(
        MenuItem("2101", "ﾐﾗﾉ風ﾄﾞﾘｱ", 300, "パスタ"),
        MenuItem("2201", "ﾍﾟﾍﾟﾛﾝﾁｰﾉ", 400, "パスタ"),
        MenuItem("1202", "小ｴﾋﾞのｻﾗﾀﾞ", 350, "サラダ"),
        MenuItem("3201", "ﾃｨﾗﾐｽｸﾗｼｺ", 300, "デザート")
    )

    @Test
    fun `test build generates expected system and user prompts`() {
        val (systemPrompt, userPrompt) = builder.build(sampleContextData, sampleMenuItems)

        assertTrue(systemPrompt.contains("あなたはサイゼリヤの注文アシスタントです"))
        assertTrue(systemPrompt.contains("出力形式（厳守）"))

        assertTrue(userPrompt.contains("あなたの現在の状況"))
        assertTrue(userPrompt.contains("サイゼリヤ メニュー一覧"))

        // Ensure Context Data is serialized
        assertTrue(userPrompt.contains("8500"))
        assertTrue(userPrompt.contains("28.5"))
        assertTrue(userPrompt.contains("焼肉"))

        // Ensure Text format menu is used
        assertTrue(userPrompt.contains("【パスタ】"))
        assertTrue(userPrompt.contains("2101 ﾐﾗﾉ風ﾄﾞﾘｱ ¥300"))
        assertTrue(userPrompt.contains("【サラダ】"))
        assertTrue(userPrompt.contains("1202 小ｴﾋﾞのｻﾗﾀﾞ ¥350"))
    }

    @Test
    fun `test toTextFormat correctly groups and formats items`() {
        val textMenu = builder.toTextFormat(sampleMenuItems)
        val expected = """
            【パスタ】
            2101 ﾐﾗﾉ風ﾄﾞﾘｱ ¥300
            2201 ﾍﾟﾍﾟﾛﾝﾁｰﾉ ¥400

            【サラダ】
            1202 小ｴﾋﾞのｻﾗﾀﾞ ¥350

            【デザート】
            3201 ﾃｨﾗﾐｽｸﾗｼｺ ¥300
        """.trimIndent()

        assertEquals(expected, textMenu)
    }

    @Test
    fun `test estimateTokenCount returns reasonable estimation`() {
        val estimatedTokens = builder.estimateTokenCount(sampleContextData, sampleMenuItems)
        val (system, user) = builder.build(sampleContextData, sampleMenuItems)
        val expectedTokens = ((system.length + user.length) * 1.5).toInt()

        assertEquals(expectedTokens, estimatedTokens)
        assertTrue("Estimated tokens should be greater than 0", estimatedTokens > 0)
    }
}
