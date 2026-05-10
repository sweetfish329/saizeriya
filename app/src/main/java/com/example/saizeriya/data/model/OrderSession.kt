package com.example.saizeriya.data.model

import kotlinx.serialization.Serializable

@Serializable
data class OrderSession(
    val shopId: String,
    val tableNo: String,
    val sessionId: String,
    val pageKind: String,
    val peopleCount: Int,
    val nextId: String = "",
    val token: String? = null,
    val baseUrl: String = "",
    val cart: List<CartItem> = emptyList()
)
