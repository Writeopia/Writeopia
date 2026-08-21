package io.writeopia.localai.llama

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual class LlamaEngine actual constructor() {

    actual fun isAvailable(): Boolean = false

    actual suspend fun loadModel(
        modelPath: String,
        params: LlamaModelParams
    ): Result<LlamaModel> = Result.failure(LlamaError.PlatformNotSupported)

    actual suspend fun createContext(
        model: LlamaModel,
        params: LlamaContextParams
    ): Result<LlamaContext> = Result.failure(LlamaError.PlatformNotSupported)

    actual suspend fun generate(
        context: LlamaContext,
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Result<String> = Result.failure(LlamaError.PlatformNotSupported)

    actual fun generateStream(
        context: LlamaContext,
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Flow<Result<String>> = emptyFlow()

    actual suspend fun freeModel(model: LlamaModel) {}

    actual suspend fun freeContext(context: LlamaContext) {}

    actual fun getLastError(): String? = "Local AI is not supported on this platform"
}
