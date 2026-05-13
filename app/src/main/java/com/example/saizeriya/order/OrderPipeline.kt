package com.example.saizeriya.order

import com.example.saizeriya.context.ContextCollector
import com.example.saizeriya.data.model.ContextData
import com.example.saizeriya.data.model.MenuItem
import com.example.saizeriya.data.repository.MenuRepository
import com.example.saizeriya.llm.LlmEngine
import com.example.saizeriya.llm.LlmMenuResponse
import com.example.saizeriya.llm.ModelDownloader
import com.example.saizeriya.llm.PromptBuilder
import com.example.saizeriya.llm.ResponseParser
import com.example.saizeriya.util.AppLogger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.Json

/**
 * 指数バックオフでリトライする。
 */
suspend fun <T> retryWithBackoff(
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000,
    block: suspend () -> T
): T {
    var currentDelay = initialDelayMs
    repeat(maxRetries - 1) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            delay(currentDelay)
            currentDelay *= 2
        }
    }
    return block() // 最後の1回はそのまま例外を投げる
}

/**
 * 注文パイプラインの全工程を管理するクラス。
 *
 * パイプライン:
 * 1. 文脈データ収集（ContextCollector）
 * 2. メニューデータ取得（MenuRepository）
 * 3. プロンプト生成（PromptBuilder）
 * 4. LLM推論（LlmEngine）
 * 5. レスポンスパース（ResponseParser）
 * 6. 注文実行（OrderExecutor）
 */
class OrderPipeline(
    private val contextCollector: ContextCollector,
    private val menuRepository: MenuRepository,
    private val llmEngine: LlmEngine,
    private val promptBuilder: PromptBuilder,
    private val responseParser: ResponseParser,
    private val orderExecutor: OrderExecutor,
    private val modelDownloader: ModelDownloader? = null
) {
    /** パイプラインの現在の状態 */
    private val _state = MutableStateFlow<PipelineState>(PipelineState.Idle)
    val state: StateFlow<PipelineState> = _state

    /**
     * パイプラインを実行する。
     *
     * @param qrUrl サイゼリヤQRコードURL
     * @param peopleCount 人数
     * @param latitude 現在の緯度
     * @param longitude 現在の経度
     * @return 注文結果
     */
    suspend fun execute(
        qrUrl: String,
        peopleCount: Int,
        latitude: Double,
        longitude: Double
    ): PipelineResult {
        AppLogger.i("Starting OrderPipeline execution. QR: $qrUrl, People: $peopleCount")
        try {
            // Initialize engine if not ready
            if (!llmEngine.isInitialized() && modelDownloader != null) {
                AppLogger.i("LLM Engine not initialized. Downloading model...")
                _state.value = PipelineState.DownloadingModel(0)
                val modelUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm?download=true"
                val modelPath = modelDownloader.downloadModel(
                    url = modelUrl,
                    fileName = "gemma-4-E2B-it.litertlm",
                    onProgress = { progress -> _state.value = PipelineState.DownloadingModel(progress) }
                )
                AppLogger.i("Model downloaded to: $modelPath. Initializing engine...")
                _state.value = PipelineState.InitializingEngine
                llmEngine.initialize(modelPath)
            }

            // ステップ1: 文脈データ収集
            AppLogger.i("Step 1: Collecting context data (Lat: $latitude, Lon: $longitude)")
            _state.value = PipelineState.CollectingContext
            val contextData = contextCollector.collectAll(latitude, longitude)
            AppLogger.d("Context data: $contextData")

            // ステップ2: メニューデータ取得
            AppLogger.i("Step 2: Fetching menu items")
            _state.value = PipelineState.FetchingMenu
            val menuItems = retryWithBackoff {
                menuRepository.getAllMenuItems()
            }

            if (menuItems.isEmpty()) {
                AppLogger.e("Menu data is empty.")
                return PipelineResult.Error("メニューデータの取得に失敗しました")
            }
            AppLogger.d("Fetched ${menuItems.size} menu items")

            // ステップ3: プロンプト生成
            AppLogger.i("Step 3: Building prompt")
            _state.value = PipelineState.BuildingPrompt
            val (systemPrompt, userPrompt) = promptBuilder.build(contextData, menuItems)
            AppLogger.d("System Prompt: $systemPrompt")
            AppLogger.d("User Prompt: $userPrompt")

            // ステップ4: LLM推論
            AppLogger.i("Step 4: Running LLM inference")
            _state.value = PipelineState.RunningInference
            val llmOutput = llmEngine.generateResponse(systemPrompt, userPrompt)
            AppLogger.i("LLM Output received.")
            AppLogger.d("Raw LLM Output: $llmOutput")

            // ステップ5: レスポンスパース
            AppLogger.i("Step 5: Parsing LLM response")
            _state.value = PipelineState.ParsingResponse
            val menuCodes = responseParser.parseMenuCodes(llmOutput)
            AppLogger.d("Parsed menu codes: $menuCodes")

            // コードのバリデーション
            val validCodes = validateCodes(menuCodes, menuItems)
            AppLogger.d("Valid menu codes: $validCodes")
            if (validCodes.isEmpty()) {
                AppLogger.e("No valid menu codes found in LLM output.")
                _state.value = PipelineState.Failed("LLMが有効なメニューコードを出力しませんでした")
                return PipelineResult.Error("LLMが有効なメニューコードを出力しませんでした")
            }

            // ステップ6: 注文実行
            AppLogger.i("Step 6: Placing order for codes: $validCodes")
            _state.value = PipelineState.PlacingOrder
            retryWithBackoff {
                orderExecutor.execute(qrUrl, peopleCount, validCodes)
            }

            AppLogger.i("OrderPipeline execution completed successfully.")
            _state.value = PipelineState.Completed
            return PipelineResult.Success(
                selectedMenuCodes = validCodes,
                selectedMenuItems = menuItems.filter { it.code in validCodes },
                reasoning = extractReasoning(llmOutput),
                contextData = contextData
            )
        } catch (e: Exception) {
            AppLogger.e("Error during OrderPipeline execution", e)
            _state.value = PipelineState.Failed(e.message ?: "不明なエラー")
            return PipelineResult.Error(e.message ?: "パイプライン実行中にエラーが発生しました")
        }
    }

    /**
     * メニューコードのバリデーション。
     * 存在しないコードを除外する。
     */
    private fun validateCodes(
        codes: List<String>,
        menuItems: List<MenuItem>
    ): List<String> {
        val validCodeSet = menuItems.map { it.code }.toSet()
        return codes.filter { it in validCodeSet }
    }

    /**
     * LLM出力からreasoningを抽出する。
     */
    private fun extractReasoning(llmOutput: String): String {
        return try {
            val codeBlockRegex = Regex("```json\\s*(\\{[\\s\\S]*?\\})\\s*```")
            val jsonRegex = Regex("\\{[\\s\\S]*\"codes\"[\\s\\S]*\\}")
            val jsonBlock = codeBlockRegex.find(llmOutput)?.groupValues?.get(1)
                ?: jsonRegex.find(llmOutput)?.value
                ?: return ""

            val response = Json {
                ignoreUnknownKeys = true
                isLenient = true
            }.decodeFromString<LlmMenuResponse>(jsonBlock)
            response.reasoning
        } catch (e: Exception) {
            ""
        }
    }
}

/** パイプラインの実行状態 */
sealed class PipelineState {
    data object Idle : PipelineState()
    data class DownloadingModel(val progress: Int) : PipelineState()
    data object InitializingEngine : PipelineState()
    data object CollectingContext : PipelineState()
    data object FetchingMenu : PipelineState()
    data object BuildingPrompt : PipelineState()
    data object RunningInference : PipelineState()
    data object ParsingResponse : PipelineState()
    data object PlacingOrder : PipelineState()
    data object Completed : PipelineState()
    data class Failed(val message: String) : PipelineState()
}

/** パイプラインの実行結果 */
sealed class PipelineResult {
    data class Success(
        val selectedMenuCodes: List<String>,
        val selectedMenuItems: List<MenuItem>,
        val reasoning: String,
        val contextData: ContextData
    ) : PipelineResult()

    data class Error(val message: String) : PipelineResult()
}
