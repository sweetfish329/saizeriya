package com.google.ai.edge.litertlm

class EngineConfig(
    val modelPath: String,
    val backend: Backend,
    val cacheDir: String? = null
)

sealed class Backend {
    class NPU(val nativeLibraryDir: String?) : Backend()
    class CPU : Backend()
    class GPU : Backend()
}

class Engine(private val config: EngineConfig) {
    fun initialize() {
        // Mock initialization
    }

    fun createConversation(config: ConversationConfig): Conversation {
        return Conversation(config)
    }

    fun close() {
        // Mock close
    }
}

class ConversationConfig(
    val systemInstruction: Contents,
    val samplerConfig: SamplerConfig
)

class Contents private constructor(val text: String) {
    companion object {
        fun of(text: String): Contents {
            return Contents(text)
        }
    }
}

class SamplerConfig(
    val topK: Int,
    val topP: Double,
    val temperature: Double
)

class Conversation(private val config: ConversationConfig) : AutoCloseable {
    fun sendMessage(prompt: String): String {
        return "{\"codes\": [\"1202\", \"3201\"], \"reasoning\": \"Mock response\"}"
    }

    fun sendMessageAsync(prompt: String): kotlinx.coroutines.flow.Flow<String> {
        return kotlinx.coroutines.flow.flowOf("{\"codes\": [\"1202\", \"3201\"], \"reasoning\": \"Mock response\"}")
    }

    override fun close() {
        // Mock close
    }
}
