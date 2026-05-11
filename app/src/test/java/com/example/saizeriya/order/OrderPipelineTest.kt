package com.example.saizeriya.order

import com.example.saizeriya.context.ContextCollector
import com.example.saizeriya.data.model.ContextData
import com.example.saizeriya.data.model.MenuItem
import com.example.saizeriya.data.model.OrderSession
import com.example.saizeriya.data.repository.MenuRepository
import com.example.saizeriya.llm.LlmEngine
import com.example.saizeriya.llm.PromptBuilder
import com.example.saizeriya.llm.ResponseParser
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class OrderPipelineTest {

    private lateinit var contextCollector: ContextCollector
    private lateinit var menuRepository: MenuRepository
    private lateinit var llmEngine: LlmEngine
    private lateinit var orderExecutor: OrderExecutor

    private lateinit var promptBuilder: PromptBuilder
    private lateinit var responseParser: ResponseParser

    private lateinit var pipeline: OrderPipeline

    @Before
    fun setUp() {
        contextCollector = mock()
        menuRepository = mock()
        llmEngine = mock()
        orderExecutor = mock()

        promptBuilder = PromptBuilder()
        responseParser = ResponseParser()

        pipeline = OrderPipeline(
            contextCollector = contextCollector,
            menuRepository = menuRepository,
            llmEngine = llmEngine,
            promptBuilder = promptBuilder,
            responseParser = responseParser,
            orderExecutor = orderExecutor
        )
    }

    @Test
    fun `パイプラインが正常に注文を完了する`() = runTest {
        // モック設定
        val mockContextData = ContextData(null, null, null, "2023-10-01T12:00:00Z")
        whenever(contextCollector.collectAll(any(), any())).thenReturn(mockContextData)

        val mockMenuItems = listOf(
            MenuItem("1202", "ミラノ風ドリア", 300, "ドリア"),
            MenuItem("3201", "マルゲリータピザ", 400, "ピザ")
        )
        whenever(menuRepository.getAllMenuItems()).thenReturn(mockMenuItems)

        val mockLlmOutput = """
            ```json
            {
              "codes": ["1202", "3201"],
              "reasoning": "定番の組み合わせです。"
            }
            ```
        """.trimIndent()
        whenever(llmEngine.generateResponse(any(), any())).thenReturn(mockLlmOutput)

        val mockSession = OrderSession(
            shopId = "123",
            tableNo = "4",
            sessionId = "session1",
            pageKind = "done",
            peopleCount = 2,
            nextId = "next1",
            token = "token1",
            baseUrl = "http://example.com"
        )
        whenever(orderExecutor.execute(any(), any(), any())).thenReturn(mockSession)

        val result = pipeline.execute("http://example.com/qr", 2, 35.6, 139.7)

        assertTrue(result is PipelineResult.Success)
        val successResult = result as PipelineResult.Success
        assertEquals(listOf("1202", "3201"), successResult.selectedMenuCodes)
        assertEquals("定番の組み合わせです。", successResult.reasoning)
        // Check fields instead of equality to avoid weird pointer issues
        assertEquals(mockContextData.collectedAt, successResult.contextData.collectedAt)
        assertEquals(PipelineState.Completed, pipeline.state.value)
    }

    @Test
    fun `LLM出力異常のフォールバック`() = runTest {
        // JSON形式ではなく、プレーンテキストで回答してきた場合
        val mockContextData = ContextData(null, null, null, "2023-10-01T12:00:00Z")
        whenever(contextCollector.collectAll(any(), any())).thenReturn(mockContextData)

        val mockMenuItems = listOf(
            MenuItem("1202", "ミラノ風ドリア", 300, "ドリア"),
            MenuItem("3201", "マルゲリータピザ", 400, "ピザ")
        )
        whenever(menuRepository.getAllMenuItems()).thenReturn(mockMenuItems)

        val mockLlmOutput = "おすすめは 1202 と 3201 です。"
        whenever(llmEngine.generateResponse(any(), any())).thenReturn(mockLlmOutput)

        val mockSession = OrderSession("123", "4", "session1", "done", 2)
        whenever(orderExecutor.execute(any(), any(), any())).thenReturn(mockSession)

        val result = pipeline.execute("http://example.com/qr", 2, 35.6, 139.7)

        assertTrue(result is PipelineResult.Success)
        val successResult = result as PipelineResult.Success
        assertEquals(listOf("1202", "3201"), successResult.selectedMenuCodes)
        assertEquals("", successResult.reasoning) // フォールバック時はreasoningなしになる
    }

    @Test
    fun `無効なメニューコードが含まれる場合`() = runTest {
        val mockContextData = ContextData(null, null, null, "2023-10-01T12:00:00Z")
        whenever(contextCollector.collectAll(any(), any())).thenReturn(mockContextData)

        val mockMenuItems = listOf(
            MenuItem("1202", "ミラノ風ドリア", 300, "ドリア")
        )
        whenever(menuRepository.getAllMenuItems()).thenReturn(mockMenuItems)

        // 9999は存在しないコード
        val mockLlmOutput = """
            {
              "codes": ["1202", "9999"]
            }
        """.trimIndent()
        whenever(llmEngine.generateResponse(any(), any())).thenReturn(mockLlmOutput)

        val mockSession = OrderSession("123", "4", "session1", "done", 2)
        whenever(orderExecutor.execute(any(), any(), any())).thenReturn(mockSession)

        val result = pipeline.execute("http://example.com/qr", 2, 35.6, 139.7)

        assertTrue(result is PipelineResult.Success)
        val successResult = result as PipelineResult.Success
        // 有効な1202のみで注文される
        assertEquals(listOf("1202"), successResult.selectedMenuCodes)
    }

    @Test
    fun `すべてのコードが無効な場合エラーになる`() = runTest {
        val mockContextData = ContextData(null, null, null, "2023-10-01T12:00:00Z")
        whenever(contextCollector.collectAll(any(), any())).thenReturn(mockContextData)

        val mockMenuItems = listOf(
            MenuItem("1202", "ミラノ風ドリア", 300, "ドリア")
        )
        whenever(menuRepository.getAllMenuItems()).thenReturn(mockMenuItems)

        val mockLlmOutput = """
            {
              "codes": ["9999", "8888"]
            }
        """.trimIndent()
        whenever(llmEngine.generateResponse(any(), any())).thenReturn(mockLlmOutput)

        val result = pipeline.execute("http://example.com/qr", 2, 35.6, 139.7)

        assertTrue(result is PipelineResult.Error)
        val errorResult = result as PipelineResult.Error
        assertEquals("LLMが有効なメニューコードを出力しませんでした", errorResult.message)
        assertTrue(pipeline.state.value is PipelineState.Failed)
    }

    @Test
    fun `ネットワークエラーで注文失敗した場合エラーになる`() = runTest {
        val mockContextData = ContextData(null, null, null, "2023-10-01T12:00:00Z")
        whenever(contextCollector.collectAll(any(), any())).thenReturn(mockContextData)

        val mockMenuItems = listOf(
            MenuItem("1202", "ミラノ風ドリア", 300, "ドリア")
        )
        whenever(menuRepository.getAllMenuItems()).thenReturn(mockMenuItems)

        val mockLlmOutput = """
            {
              "codes": ["1202"]
            }
        """.trimIndent()
        whenever(llmEngine.generateResponse(any(), any())).thenReturn(mockLlmOutput)

        // orderExecutorが例外を投げる
        whenever(orderExecutor.execute(any(), any(), any())).thenThrow(RuntimeException("Network Error"))

        val result = pipeline.execute("http://example.com/qr", 2, 35.6, 139.7)

        assertTrue(result is PipelineResult.Error)
        assertTrue(pipeline.state.value is PipelineState.Failed)
    }
}
