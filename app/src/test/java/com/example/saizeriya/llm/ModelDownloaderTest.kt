package com.example.saizeriya.llm

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner
import java.io.File
import java.nio.file.Files

@RunWith(MockitoJUnitRunner::class)
class ModelDownloaderTest {

    @Mock
    private lateinit var mockContext: Context

    private lateinit var downloader: ModelDownloader
    private lateinit var tempDir: File

    @Before
    fun setup() {
        tempDir = Files.createTempDirectory("testFiles").toFile()
        `when`(mockContext.filesDir).thenReturn(tempDir)
        downloader = ModelDownloader(mockContext)
    }

    @Test
    fun testExistingFileReturnsImmediately() = runBlocking {
        // Create dummy file
        val dummyFile = File(tempDir, "test.bin")
        dummyFile.writeText("dummy content")

        var progressCalled = false
        val path = downloader.downloadModel("http://example.com", "test.bin") {
            progressCalled = true
            assertEquals(100, it.progress)
            assertEquals(dummyFile.length(), it.downloadedBytes)
        }

        assertTrue(progressCalled)
        assertEquals(dummyFile.absolutePath, path)
    }
}
