package com.example.saizeriya

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.saizeriya.context.ContextCollector
import com.example.saizeriya.context.GmailProvider
import com.example.saizeriya.context.HealthDataProvider
import com.example.saizeriya.context.WeatherProvider
import com.example.saizeriya.data.repository.MenuRepository
import com.example.saizeriya.llm.LlmEngine
import com.example.saizeriya.llm.ModelDownloader
import com.example.saizeriya.llm.PromptBuilder
import com.example.saizeriya.llm.ResponseParser
import com.example.saizeriya.order.OrderExecutor
import com.example.saizeriya.order.OrderPipeline
import com.example.saizeriya.order.PipelineResult
import com.example.saizeriya.order.SaizeriyaClient
import com.example.saizeriya.ui.screen.HomeScreen
import com.example.saizeriya.ui.screen.LogScreen
import com.example.saizeriya.ui.screen.OrderScreen
import com.example.saizeriya.ui.screen.ResultScreen
import com.example.saizeriya.ui.theme.SaizeriyaTheme
import com.example.saizeriya.ui.viewmodel.OrderUiState
import com.example.saizeriya.ui.viewmodel.OrderViewModel
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ディープリンクからのQR URL取得
        val qrUrl = intent?.data?.toString()

        // Manual Dependency Injection setup for Phase 6
        // Normally this would be done with Hilt or Dagger in an AppContainer or ViewModel Factory
        val orderPipeline = createOrderPipeline()
        val viewModelFactory = OrderViewModel.Factory(orderPipeline)

        setContent {
            SaizeriyaTheme {
                AppNavigation(initialQrUrl = qrUrl, viewModelFactory = viewModelFactory)
            }
        }
    }

    private fun createOrderPipeline(): OrderPipeline {
        // Providers
        val healthProvider = HealthDataProvider(this)
        val weatherProvider = WeatherProvider(BuildConfig.OPENWEATHER_API_KEY)
        val gmailProvider = GmailProvider()
        val contextCollector = ContextCollector(healthProvider, weatherProvider, gmailProvider)

        // Data & Order
        val menuRepository = MenuRepository(this)
        val saizeriyaClient = SaizeriyaClient()
        val orderExecutor = OrderExecutor(saizeriyaClient)

        // LLM
        // Note: LlmEngine requires a path to the model. We provide a dummy string here.
        val llmEngine = LlmEngine(this)
        val promptBuilder = PromptBuilder()
        val responseParser = ResponseParser()
        val modelDownloader = ModelDownloader(this)

        return OrderPipeline(
            contextCollector = contextCollector,
            menuRepository = menuRepository,
            llmEngine = llmEngine,
            promptBuilder = promptBuilder,
            responseParser = responseParser,
            orderExecutor = orderExecutor,
            modelDownloader = modelDownloader
        )
    }
}

@Composable
fun AppNavigation(initialQrUrl: String? = null, viewModelFactory: OrderViewModel.Factory) {
    val navController = rememberNavController()
    val orderViewModel: OrderViewModel = viewModel(factory = viewModelFactory)

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                initialQrUrl = initialQrUrl,
                onStartOrder = { qrUrl, peopleCount ->
                    // Encode URL to pass safely in navigation route
                    val encodedUrl = URLEncoder.encode(qrUrl, StandardCharsets.UTF_8.toString())
                    orderViewModel.startOrder(qrUrl, peopleCount, 35.6812, 139.7671)
                    navController.navigate("order/$encodedUrl/$peopleCount")
                },
                onViewLogs = {
                    navController.navigate("logs")
                }
            )
        }
        composable("logs") {
            LogScreen(onBack = { navController.popBackStack() })
        }
        composable("order/{qrUrl}/{peopleCount}") { backStackEntry ->
            val encodedUrl = backStackEntry.arguments?.getString("qrUrl") ?: ""
            val qrUrl = URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString())
            val peopleCount = backStackEntry.arguments?.getString("peopleCount")?.toIntOrNull() ?: 1

            val uiState by orderViewModel.uiState.collectAsState()
            val pipelineState by orderViewModel.pipelineState.collectAsState()

            OrderScreen(
                qrUrl = qrUrl,
                peopleCount = peopleCount,
                uiState = uiState,
                pipelineState = pipelineState,
                onComplete = {
                    navController.navigate("result") {
                        popUpTo("home") { inclusive = false }
                    }
                },
                onError = { error ->
                    // For simplicity, we just navigate back to home on error or show it in the UI
                    navController.popBackStack()
                }
            )
        }
        composable("result") {
            val uiState by orderViewModel.uiState.collectAsState()

            if (uiState is OrderUiState.Success) {
                val successState = uiState as OrderUiState.Success
                val selectedItems = successState.result.selectedMenuItems
                val reasoning = successState.result.reasoning
                val totalPrice = selectedItems.sumOf { it.price }

                ResultScreen(
                    selectedItems = selectedItems,
                    reasoning = reasoning,
                    totalPrice = totalPrice,
                    onBackToHome = {
                        orderViewModel.reset()
                        navController.popBackStack("home", inclusive = false)
                    }
                )
            } else {
                // Should not reach here, but fallback to home
                navController.popBackStack("home", inclusive = false)
            }
        }
    }
}
