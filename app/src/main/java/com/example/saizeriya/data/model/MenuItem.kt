package com.example.saizeriya.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MenuItem(
    val code: String,
    val name: String,
    val price: Int,
    val category: String = "未分類"
)
