package com.example.saizeriya.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.saizeriya.data.model.MenuItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    selectedItems: List<MenuItem>,
    reasoning: String,
    totalPrice: Int,
    onBackToHome: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("注文完了") }) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 選定メニュー一覧
            item { Text("選定されたメニュー", style = MaterialTheme.typography.titleLarge) }
            items(selectedItems) { item ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(item.name, style = MaterialTheme.typography.bodyLarge)
                            Text(item.category, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("¥${item.price}", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // 合計金額
            item {
                Text("合計: ¥$totalPrice", style = MaterialTheme.typography.headlineSmall)
            }

            // 選定理由
            if (reasoning.isNotBlank()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("AIの選定理由", style = MaterialTheme.typography.titleMedium)
                            Text(reasoning, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // ホームに戻る
            item {
                Button(onClick = onBackToHome, modifier = Modifier.fillMaxWidth()) {
                    Text("ホームに戻る")
                }
            }
        }
    }
}
