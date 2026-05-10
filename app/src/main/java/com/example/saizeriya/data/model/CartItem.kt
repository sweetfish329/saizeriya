package com.example.saizeriya.data.model

import kotlinx.serialization.Serializable

@Serializable
data class CartItem(
    val code: String,
    val name: String = "",
    val price: Int = 0,
    val count: Int,
    val reorder: Int = 0
)
