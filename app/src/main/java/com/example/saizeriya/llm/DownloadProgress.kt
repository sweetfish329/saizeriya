package com.example.saizeriya.llm

data class DownloadProgress(
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBps: Double
)
