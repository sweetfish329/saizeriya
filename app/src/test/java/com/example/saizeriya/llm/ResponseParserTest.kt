package com.example.saizeriya.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class ResponseParserTest {

    private val parser = ResponseParser()

    @Test
    fun parseMenuCodes_withValidJson() {
        val input = """
            Here is your response:
            ```json
            {
              "codes": ["1202", "3201"],
              "reasoning": "Looks good"
            }
            ```
        """.trimIndent()

        val codes = parser.parseMenuCodes(input)
        assertEquals(listOf("1202", "3201"), codes)
    }

    @Test
    fun parseMenuCodes_withRawJson() {
        val input = """
            {
              "codes": ["2101", "4101"],
              "reasoning": "Healthy choice"
            }
        """.trimIndent()

        val codes = parser.parseMenuCodes(input)
        assertEquals(listOf("2101", "4101"), codes)
    }

    @Test
    fun parseMenuCodes_fallbackToRegex() {
        val input = "I suggest ordering 1101 and 3302 for your meal."

        val codes = parser.parseMenuCodes(input)
        assertEquals(listOf("1101", "3302"), codes)
    }
}
