package com.example.saizeriya.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.saizeriya.order.PipelineState
import com.example.saizeriya.ui.viewmodel.OrderUiState

@Composable
fun OrderScreen(
    qrUrl: String,
    peopleCount: Int,
    uiState: OrderUiState,
    pipelineState: PipelineState,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
    // 処理中のバック操作を無効化
    BackHandler(enabled = uiState is OrderUiState.Processing) {
        // Do nothing
    }

    LaunchedEffect(uiState) {
        when (uiState) {
            is OrderUiState.Success -> onComplete()
            is OrderUiState.Error -> onError(uiState.message)
            else -> {} // Do nothing
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 進捗インジケータ
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(24.dp))

        // 現在のステップ表示
        Text(
            text = when (pipelineState) {
                is PipelineState.Idle -> "準備中..."
                is PipelineState.CollectingContext -> "📊 文脈データを収集中..."
                is PipelineState.FetchingMenu -> "📋 メニューを取得中..."
                is PipelineState.BuildingPrompt -> "✍️ プロンプトを生成中..."
                is PipelineState.RunningInference -> "🧠 AIがメニューを選定中..."
                is PipelineState.ParsingResponse -> "📝 結果を解析中..."
                is PipelineState.PlacingOrder -> "🛒 注文を送信中..."
                is PipelineState.Completed -> "✅ 注文完了！"
                is PipelineState.Failed -> "❌ エラー: ${pipelineState.message}"
            },
            style = MaterialTheme.typography.titleMedium
        )
    }
}
