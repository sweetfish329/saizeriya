package com.example.saizeriya

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.saizeriya.llm.LlmEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class NpuBenchmark {

    private lateinit var context: Context
    private lateinit var llmEngine: LlmEngine
    private val tag = "NpuBenchmark"

    // Set this to your actual model path when running on a physical device.
    private val modelPath = "/data/local/tmp/gemma-2b-it-gpu-int4.bin"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        llmEngine = LlmEngine(context)
    }

    @After
    fun tearDown() {
        llmEngine.close()
    }

    @Test
    fun benchmarkNpuPerformance() = runBlocking {
        val modelFile = File(modelPath)
        if (!modelFile.exists()) {
            Log.w(tag, "Model file not found at ${'$'}modelPath. Skipping benchmark.")
            return@runBlocking
        }

        Log.i(tag, "Starting NPU Benchmark...")

        // 1. Initialization Time
        val initStart = System.currentTimeMillis()
        llmEngine.initialize(modelPath)
        val initTime = System.currentTimeMillis() - initStart
        Log.i(tag, "Model Initialization: ${'$'}initTime ms")

        val systemPrompt = "あなたはサイゼリヤのアシスタントです。JSON形式で回答してください。"
        val userPrompt = "現在の歩数は8000歩です。おすすめを教えてください。"

        // 2. Time to First Token (TTFT)
        val ttftStart = System.currentTimeMillis()
        val flow = llmEngine.generateResponseStream(systemPrompt, userPrompt)

        // Emits tokens as they are generated
        flow.first()
        val ttft = System.currentTimeMillis() - ttftStart
        Log.i(tag, "Time to First Token (TTFT): ${'$'}ttft ms")

        // 3. Full Generation Time and Speed
        val inferStart = System.currentTimeMillis()
        val fullResponse = llmEngine.generateResponse(systemPrompt, userPrompt)
        val inferTime = System.currentTimeMillis() - inferStart

        // Approximate token count calculation for Japanese
        val tokenCount = (fullResponse.length * 1.5).toInt()
        val tokensPerSecond = (tokenCount.toDouble() / inferTime) * 1000

        Log.i(tag, "Full Inference Time: ${'$'}inferTime ms")
        Log.i(tag, "Total Characters Generated: ${'$'}{fullResponse.length}")
        Log.i(tag, "Estimated Tokens/Sec: ${'$'}tokensPerSecond")

        Log.i(tag, "Benchmark Complete.")
    }
}
