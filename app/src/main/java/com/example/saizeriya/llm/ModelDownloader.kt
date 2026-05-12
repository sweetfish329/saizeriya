package com.example.saizeriya.llm

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ModelDownloader(private val context: Context) {

    suspend fun downloadModel(
        url: String,
        fileName: String,
        onProgress: (Int) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val modelFile = File(context.filesDir, fileName)

        // Return early if file already exists
        if (modelFile.exists() && modelFile.length() > 0) {
            onProgress(100)
            return@withContext modelFile.absolutePath
        }

        var connection: HttpURLConnection? = null
        try {
            val urlObj = URL(url)
            connection = urlObj.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 60000 // 60 seconds

            // Follow redirects
            connection.instanceFollowRedirects = true

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                var redirectUrl = connection.getHeaderField("Location")
                if (redirectUrl != null) {
                    connection.disconnect()
                    connection = URL(redirectUrl).openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 15000
                    connection.readTimeout = 60000
                    connection.instanceFollowRedirects = true
                } else {
                    throw Exception("サーバーからの応答が不正です: ${connection.responseCode}")
                }
            }

            val fileLength = connection.contentLength
            val input = connection.inputStream
            val output = FileOutputStream(modelFile)

            val data = ByteArray(4096)
            var total: Long = 0
            var count: Int
            var lastProgress = -1

            while (input.read(data).also { count = it } != -1) {
                total += count.toLong()
                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt()
                    if (progress != lastProgress) {
                        onProgress(progress)
                        lastProgress = progress
                    }
                }
                output.write(data, 0, count)
            }
            output.flush()
            output.close()
            input.close()

            if (fileLength > 0 && total != fileLength.toLong()) {
                modelFile.delete()
                throw Exception("ダウンロードが中断されました")
            }

            return@withContext modelFile.absolutePath

        } catch (e: Exception) {
            if (modelFile.exists()) {
                modelFile.delete()
            }
            throw Exception("モデルのダウンロードに失敗しました: ${e.message}")
        } finally {
            connection?.disconnect()
        }
    }
}
