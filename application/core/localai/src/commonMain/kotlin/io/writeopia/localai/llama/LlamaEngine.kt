package io.writeopia.localai.llama

import kotlinx.coroutines.flow.Flow

expect class LlamaEngine() {
    fun isAvailable(): Boolean

    suspend fun loadModel(
        modelPath: String,
        params: LlamaModelParams = LlamaModelParams()
    ): Result<LlamaModel>

    suspend fun createContext(
        model: LlamaModel,
        params: LlamaContextParams = LlamaContextParams()
    ): Result<LlamaContext>

    suspend fun generate(
        context: LlamaContext,
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): Result<String>

    fun generateStream(
        context: LlamaContext,
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): Flow<Result<String>>

    suspend fun freeModel(model: LlamaModel)

    suspend fun freeContext(context: LlamaContext)

    fun getLastError(): String?
}
