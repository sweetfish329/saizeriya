package com.example.saizeriya

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.saizeriya.context.ContextCollector
import com.example.saizeriya.context.GmailProvider
import com.example.saizeriya.context.HealthDataProvider
import com.example.saizeriya.context.WeatherProvider
import com.example.saizeriya.data.repository.MenuRepository
import com.example.saizeriya.llm.LlmEngine
import com.example.saizeriya.llm.PromptBuilder
import com.example.saizeriya.llm.ModelDownloader
import com.example.saizeriya.llm.ResponseParser
import com.example.saizeriya.order.OrderExecutor
import com.example.saizeriya.order.OrderPipeline
import com.example.saizeriya.order.PipelineResult
import com.example.saizeriya.order.SaizeriyaClient
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * End-to-End Test for the full Order Pipeline.
 * Best run on a physical device with the required LLM model and valid network for APIs.
 */
@RunWith(AndroidJUnit4::class)
class E2EOrderPipelineTest {

    private lateinit var context: Context
    private lateinit var pipeline: OrderPipeline
    private lateinit var llmEngine: LlmEngine

    private val modelPath = "/data/local/tmp/gemma-2b-it-gpu-int4.bin"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Real implementations for E2E
        val healthProvider = HealthDataProvider(context)
        val weatherProvider = WeatherProvider("mock_api_key")
        val gmailProvider = GmailProvider()
        val contextCollector = ContextCollector(healthProvider, weatherProvider, gmailProvider)

        val menuRepository = MenuRepository(context)
        val saizeriyaClient = SaizeriyaClient()
        val orderExecutor = OrderExecutor(saizeriyaClient)

        llmEngine = LlmEngine(context)
        val promptBuilder = PromptBuilder()
        val responseParser = ResponseParser()
        val modelDownloader = ModelDownloader(context)

        pipeline = OrderPipeline(
            contextCollector = contextCollector,
            menuRepository = menuRepository,
            llmEngine = llmEngine,
            promptBuilder = promptBuilder,
            responseParser = responseParser,
            orderExecutor = orderExecutor,
            modelDownloader = modelDownloader
        )
    }

    @After
    fun tearDown() {
        llmEngine.close()
    }

    @Test
    fun testFullPipelineExecution() = runBlocking {
        // Skip test if model file isn't present
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            println("Model not found at ${'$'}modelPath. Skipping E2E test.")
            return@runBlocking
        }

        llmEngine.initialize(modelPath)

        val result = pipeline.execute(
            qrUrl = "https://example.com/saizeriya3/qr?id=123",
            peopleCount = 2,
            latitude = 35.6812,
            longitude = 139.7671
        )

        assertTrue("Pipeline execution should be successful", result is PipelineResult.Success)

        if (result is PipelineResult.Success) {
            assertTrue("Should select at least one menu item", result.selectedMenuCodes.isNotEmpty())
            assertTrue("Should have some reasoning", result.reasoning.isNotEmpty())
            println("Selected Menus: ${'$'}{result.selectedMenuCodes}")
            println("Reasoning: ${'$'}{result.reasoning}")
        }
    }
}
