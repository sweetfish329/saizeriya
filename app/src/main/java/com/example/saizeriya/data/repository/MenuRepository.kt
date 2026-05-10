package com.example.saizeriya.data.repository

import android.content.Context
import com.example.saizeriya.data.model.MenuItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.InputStreamReader

class MenuRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private var cachedMenu: List<MenuItem>? = null

    suspend fun getAllMenuItems(): List<MenuItem> = withContext(Dispatchers.IO) {
        cachedMenu?.let { return@withContext it }
        try {
            val inputStream = context.assets.open("menu.json")
            val jsonString = InputStreamReader(inputStream).readText()
            val items = json.decodeFromString(ListSerializer(MenuItem.serializer()), jsonString)
            cachedMenu = items
            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getMenuByCategory(): Map<String, List<MenuItem>> {
        return getAllMenuItems().groupBy { it.category }
    }

    suspend fun toPromptJson(): String {
        val items = getAllMenuItems()
        return json.encodeToString(
            ListSerializer(MenuItem.serializer()),
            items
        )
    }
}
