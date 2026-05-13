package com.example.saizeriya.llm

import android.content.Context
import com.example.saizeriya.util.AppLogger
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM エンジンのライフサイクル管理クラス。
 * NPUバックエンドでの推論を担当する。
 */
class LlmEngine(private val context: Context) {

    private var engine: Engine? = null

    /**
     * LiteRT-LM エンジンを初期化する。
     * NPUバックエンドを使用。NPU非対応の場合はCPUにフォールバック。
     *
     * @param modelPath .litertlm ファイルの絶対パス
     * @throws IllegalStateException モデルファイルが存在しない場合
     */
    suspend fun initialize(modelPath: String) = withContext(Dispatchers.IO) {
        AppLogger.i("Initializing LlmEngine with model: $modelPath (NPU)")
        val modelFile = java.io.File(modelPath)
        require(modelFile.exists()) {
            "モデルファイルが存在しません: $modelPath"
        }

        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.NPU(
                nativeLibraryDir = context.applicationInfo.nativeLibraryDir
            ),
            cacheDir = context.cacheDir.absolutePath
        )

        try {
            engine = Engine(engineConfig).also {
                it.initialize()
            }
            AppLogger.i("LlmEngine initialized successfully with NPU")
        } catch (e: Exception) {
            AppLogger.e("Failed to initialize LlmEngine with NPU", e)
            throw e
        }
    }

    /**
     * CPUバックエンドで初期化する（NPU非対応環境用）。
     */
    suspend fun initializeWithCpu(modelPath: String) = withContext(Dispatchers.IO) {
        AppLogger.i("Initializing LlmEngine with model: $modelPath (CPU)")
        val engineConfig = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            cacheDir = context.cacheDir.absolutePath
        )
        try {
            engine = Engine(engineConfig).also { it.initialize() }
            AppLogger.i("LlmEngine initialized successfully with CPU")
        } catch (e: Exception) {
            AppLogger.e("Failed to initialize LlmEngine with CPU", e)
            throw e
        }
    }

    /**
     * 同期的にプロンプトを送信し、完全なレスポンスを取得する。
     *
     * @param systemPrompt システム指示
     * @param userPrompt ユーザープロンプト（文脈 + メニュー）
     * @return LLMの応答テキスト
     */
    suspend fun generateResponse(
        systemPrompt: String,
        userPrompt: String
    ): String = withContext(Dispatchers.IO) {
        AppLogger.i("Generating LLM response...")
        val eng = engine ?: throw IllegalStateException("エンジンが初期化されていません")

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            samplerConfig = SamplerConfig(
                topK = 10,
                topP = 0.95,
                temperature = 0.3
            )
        )

        try {
            eng.createConversation(conversationConfig).use { conversation ->
                val response = conversation.sendMessage(userPrompt)
                AppLogger.i("LLM response generated.")
                (response.contents.contents.first() as Content.Text).text
            }
        } catch (e: Exception) {
            AppLogger.e("Error during LLM generation", e)
            throw e
        }
    }

    /**
     * ストリーミングでレスポンスを取得する（UI表示用）。
     *
     * @param systemPrompt システム指示
     * @param userPrompt ユーザープロンプト
     * @return トークンのFlow
     */
    suspend fun generateResponseStream(
        systemPrompt: String,
        userPrompt: String
    ): Flow<String> = flow {
        val eng = engine ?: throw IllegalStateException("エンジンが初期化されていません")

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemPrompt),
            samplerConfig = SamplerConfig(
                topK = 10,
                topP = 0.95,
                temperature = 0.3
            )
        )

        eng.createConversation(conversationConfig).use { conversation ->
            val responseFlow = conversation.sendMessageAsync(userPrompt)
                .map { (it.contents.contents.first() as Content.Text).text }
            emitAll(responseFlow)
        }
    }

    /**
     * エンジンのリソースを解放する。
     * 必ず使用後に呼ぶこと。
     */
    fun close() {
        engine?.close()
        engine = null
    }

    /** エンジンが初期化済みか確認する */
    fun isInitialized(): Boolean = engine != null
}
