# Phase 6: UI/UX 実装（Jetpack Compose）

## 概要

Jetpack Compose を使用してAndroid UIを実装する。
注文URL入力 → 文脈確認 → メニュー提案 → 注文確認 → 結果表示の画面フローを構築する。

## 前提条件

- Phase 0（Compose依存関係）完了
- Phase 5（OrderPipeline）完了

## 想定期間: 4日

---

## 実装タスク一覧

- [ ] Task 6-1: テーマ設定（Theme.kt）
- [ ] Task 6-2: Navigation設定
- [ ] Task 6-3: OrderViewModel 実装
- [ ] Task 6-4: HomeScreen（QR URL入力）
- [ ] Task 6-5: OrderScreen（推論・注文進捗）
- [ ] Task 6-6: ResultScreen（注文結果）

---

## 画面フロー

```
HomeScreen          OrderScreen          ResultScreen
┌──────────┐      ┌──────────────┐      ┌──────────────┐
│ QR URL   │      │ 進捗表示     │      │ 注文完了     │
│ 入力     │─────▶│ 文脈収集中... │─────▶│ 選定メニュー │
│          │      │ 推論中...    │      │ 選定理由     │
│ [注文開始]│      │ 注文実行中... │      │ [ホームに戻る]│
└──────────┘      └──────────────┘      └──────────────┘
```

---

## Task 6-1: テーマ設定

### ファイル: `ui/theme/Theme.kt`

```kotlin
package com.example.saizeriya.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// サイゼリヤのブランドカラーに近い配色
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),       // 緑（サイゼリヤのイメージ）
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA5D6A7),
    secondary = Color(0xFFD32F2F),     // 赤（イタリアンカラー）
    background = Color(0xFFFFFDE7),    // クリーム
    surface = Color.White,
    onBackground = Color(0xFF1B1B1B),
    onSurface = Color(0xFF1B1B1B)
)

@Composable
fun SaizeriyaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        content = content
    )
}
```

---

## Task 6-2: Navigation 設定

### ファイル: `MainActivity.kt`

```kotlin
package com.example.saizeriya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.saizeriya.ui.theme.SaizeriyaTheme
import com.example.saizeriya.ui.screen.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ディープリンクからのQR URL取得
        val qrUrl = intent?.data?.toString()

        setContent {
            SaizeriyaTheme {
                AppNavigation(initialQrUrl = qrUrl)
            }
        }
    }
}

@Composable
fun AppNavigation(initialQrUrl: String? = null) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                initialQrUrl = initialQrUrl,
                onStartOrder = { qrUrl, peopleCount ->
                    navController.navigate("order/$qrUrl/$peopleCount")
                }
            )
        }
        composable("order/{qrUrl}/{peopleCount}") { backStackEntry ->
            val qrUrl = backStackEntry.arguments?.getString("qrUrl") ?: ""
            val peopleCount = backStackEntry.arguments?.getString("peopleCount")?.toIntOrNull() ?: 1
            OrderScreen(
                qrUrl = qrUrl,
                peopleCount = peopleCount,
                onComplete = { result ->
                    navController.navigate("result")
                },
                onError = { error ->
                    // エラー処理
                }
            )
        }
        composable("result") {
            ResultScreen(
                onBackToHome = {
                    navController.popBackStack("home", inclusive = false)
                }
            )
        }
    }
}
```

> **注意**: QR URLにはスラッシュが含まれるため、Base64エンコードするか
> ViewModelで状態共有する方式に変更すること。

---

## Task 6-3: OrderViewModel 実装

### ファイル: `ui/viewmodel/OrderViewModel.kt`

```kotlin
package com.example.saizeriya.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.saizeriya.order.OrderPipeline
import com.example.saizeriya.order.PipelineResult
import com.example.saizeriya.order.PipelineState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class OrderViewModel(
    private val orderPipeline: OrderPipeline
) : ViewModel() {

    private val _uiState = MutableStateFlow<OrderUiState>(OrderUiState.Input)
    val uiState: StateFlow<OrderUiState> = _uiState

    private val _pipelineState = orderPipeline.state
    val pipelineState: StateFlow<PipelineState> = _pipelineState

    fun startOrder(qrUrl: String, peopleCount: Int, lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.value = OrderUiState.Processing
            val result = orderPipeline.execute(qrUrl, peopleCount, lat, lon)
            _uiState.value = when (result) {
                is PipelineResult.Success -> OrderUiState.Success(result)
                is PipelineResult.Error -> OrderUiState.Error(result.message)
            }
        }
    }
}

sealed class OrderUiState {
    data object Input : OrderUiState()
    data object Processing : OrderUiState()
    data class Success(val result: PipelineResult.Success) : OrderUiState()
    data class Error(val message: String) : OrderUiState()
}
```

---

## Task 6-4: HomeScreen

### ファイル: `ui/screen/HomeScreen.kt`

```kotlin
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
    onStartOrder: (qrUrl: String, peopleCount: Int) -> Unit
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
        }
    }
}
```

---

## Task 6-5: OrderScreen

### ファイル: `ui/screen/OrderScreen.kt`

```kotlin
package com.example.saizeriya.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.saizeriya.order.PipelineState

@Composable
fun OrderScreen(
    qrUrl: String,
    peopleCount: Int,
    pipelineState: PipelineState,
    onComplete: () -> Unit,
    onError: (String) -> Unit
) {
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
```

---

## Task 6-6: ResultScreen

### ファイル: `ui/screen/ResultScreen.kt`

```kotlin
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
```

---

## 完了条件

- [ ] テーマ設定が適用されている
- [ ] 画面遷移（Home → Order → Result）が動作する
- [ ] HomeScreen でQR URL入力と人数選択が可能
- [ ] OrderScreen でパイプライン進捗がリアルタイム表示される
- [ ] ResultScreen で選定メニューと理由が表示される
- [ ] ディープリンク（QR URL直接起動）が動作する

## 参考リンク

- [Jetpack Compose ドキュメント](https://developer.android.com/jetpack/compose)
- [Material 3 Components](https://m3.material.io/components)
- [Navigation Compose](https://developer.android.com/jetpack/compose/navigation)
