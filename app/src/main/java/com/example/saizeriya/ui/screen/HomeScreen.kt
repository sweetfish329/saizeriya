package com.example.saizeriya.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    initialQrUrl: String? = null,
    onStartOrder: (qrUrl: String, peopleCount: Int) -> Unit,
    onViewLogs: () -> Unit
) {
    var qrUrl by remember { mutableStateOf(initialQrUrl ?: "") }
    var peopleCount by remember { mutableStateOf(1) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("サイゼリヤ自動注文") })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // QR URL 入力
            OutlinedTextField(
                value = qrUrl,
                onValueChange = { qrUrl = it },
                label = { Text("注文URL") },
                placeholder = { Text("QRコードのURLを入力") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // 人数選択
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("人数:")
                (1..6).forEach { count ->
                    FilterChip(
                        selected = peopleCount == count,
                        onClick = { peopleCount = count },
                        label = { Text("$count") }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 注文開始ボタン
            Button(
                onClick = { onStartOrder(qrUrl, peopleCount) },
                modifier = Modifier.fillMaxWidth(),
                enabled = qrUrl.isNotBlank()
            ) {
                Text("注文を開始する")
            }

            // ログ確認ボタン
            OutlinedButton(
                onClick = onViewLogs,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("システムログを確認")
            }
        }
    }
}
