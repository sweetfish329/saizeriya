package com.example.saizeriya.llm

import android.content.Context
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class LlmEngineTest {

    private lateinit var mockContext: Context
    private lateinit var llmEngine: LlmEngine

    @Before
    fun setUp() {
        mockContext = mock()
        llmEngine = LlmEngine(mockContext)
    }

    @Test
    fun `インスタンスが生成できる`() {
        assertNotNull(llmEngine)
    }
}
