package com.example.saizeriya.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val TAG = "SaizeriyaApp"
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    /**
     * Set to true in unit tests to avoid android.util.Log dependency.
     */
    var isTestMode: Boolean = false

    fun init(context: Context) {
        val logDir = File(context.getExternalFilesDir(null), "logs")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        logFile = File(logDir, "app_log.txt")
        i("AppLogger initialized. Log file: ${logFile?.absolutePath}")
    }

    fun i(message: String) {
        if (isTestMode) {
            println("[$TAG] INFO: $message")
            return
        }
        Log.i(TAG, message)
        writeToFile("INFO", message)
    }

    fun d(message: String) {
        if (isTestMode) {
            println("[$TAG] DEBUG: $message")
            return
        }
        Log.d(TAG, message)
        writeToFile("DEBUG", message)
    }

    fun e(message: String, throwable: Throwable? = null) {
        if (isTestMode) {
            println("[$TAG] ERROR: $message")
            throwable?.printStackTrace()
            return
        }
        Log.e(TAG, message, throwable)
        val fullMessage = if (throwable != null) {
            "$message\n${Log.getStackTraceString(throwable)}"
        } else {
            message
        }
        writeToFile("ERROR", fullMessage)
    }

    private fun writeToFile(level: String, message: String) {
        val file = logFile ?: return
        try {
            val timestamp = dateFormat.format(Date())
            val logEntry = "[$timestamp] [$level] $message\n"
            FileWriter(file, true).use { writer ->
                writer.append(logEntry)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log to file", e)
        }
    }

    fun getLogContent(): String {
        val file = logFile ?: return "Log file not initialized"
        return if (file.exists()) {
            try {
                file.readText()
            } catch (e: Exception) {
                "Error reading log file: ${e.message}"
            }
        } else {
            "Log file does not exist"
        }
    }

    fun clearLogs() {
        try {
            logFile?.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear logs", e)
        }
    }
}
