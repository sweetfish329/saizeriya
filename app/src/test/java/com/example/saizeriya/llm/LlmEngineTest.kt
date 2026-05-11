package com.example.saizeriya.llm

import android.content.Context
import android.content.pm.ApplicationInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class LlmEngineTest {

    private lateinit var context: Context
    private lateinit var engine: LlmEngine

    @Before
    fun setup() {
        context = mock(Context::class.java)
        val appInfo = ApplicationInfo()
        appInfo.nativeLibraryDir = "/fake/lib/dir"
        `when`(context.applicationInfo).thenReturn(appInfo)

        val cacheDir = java.io.File(System.getProperty("java.io.tmpdir"))
        `when`(context.cacheDir).thenReturn(cacheDir)

        engine = LlmEngine(context)
    }

    @Test
    fun initializeAndGenerateResponse() = runBlocking {
        // Create a dummy file for initialize
        val dummyModel = java.io.File.createTempFile("model", ".litertlm")
        dummyModel.deleteOnExit()

        engine.initialize(dummyModel.absolutePath)
        assertTrue(engine.isInitialized())

        val response = engine.generateResponse("System Prompt", "User Prompt")
        assertEquals("{\"codes\": [\"1202\", \"3201\"], \"reasoning\": \"Mock response\"}", response)

        engine.close()
    }

    @Test
    fun generateResponseStream() = runBlocking {
        val dummyModel = java.io.File.createTempFile("model", ".litertlm")
        dummyModel.deleteOnExit()

        engine.initialize(dummyModel.absolutePath)

        val stream = engine.generateResponseStream("System", "User")
        val response = stream.first()
        assertEquals("{\"codes\": [\"1202\", \"3201\"], \"reasoning\": \"Mock response\"}", response)

        engine.close()
    }
}
