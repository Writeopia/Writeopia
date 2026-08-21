package io.writeopia.localai.llama

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

actual class LlamaEngine actual constructor() {

    @Volatile
    private var lastError: String? = null

    actual fun isAvailable(): Boolean = isLibraryLoaded

    actual suspend fun loadModel(
        modelPath: String,
        params: LlamaModelParams
    ): Result<LlamaModel> = withContext(Dispatchers.IO) {
        if (!isLibraryLoaded) {
            return@withContext Result.failure(LlamaError.LibraryNotAvailable)
        }

        try {
            val handle = nativeLoadModel(
                modelPath,
                params.nGpuLayers,
                params.useMmap,
                params.useMlock
            )

            if (handle == 0L) {
                lastError = nativeGetLastError() ?: "Failed to load model"
                return@withContext Result.failure(LlamaError.ModelLoadFailed(lastError!!))
            }

            Result.success(LlamaModel(modelPath, true, handle))
        } catch (e: Exception) {
            lastError = e.message
            Result.failure(LlamaError.ModelLoadFailed(e.message ?: "Unknown error"))
        }
    }

    actual suspend fun createContext(
        model: LlamaModel,
        params: LlamaContextParams
    ): Result<LlamaContext> = withContext(Dispatchers.IO) {
        if (!isLibraryLoaded) {
            return@withContext Result.failure(LlamaError.LibraryNotAvailable)
        }

        if (model.nativeHandle == 0L) {
            lastError = "Model is not loaded"
            return@withContext Result.failure(LlamaError.ContextCreationFailed(lastError!!))
        }

        try {
            val handle = nativeCreateContext(
                model.nativeHandle,
                params.contextLength,
                params.batchSize,
                params.nThreads
            )

            if (handle == 0L) {
                lastError = nativeGetLastError() ?: "Failed to create context"
                return@withContext Result.failure(LlamaError.ContextCreationFailed(lastError!!))
            }

            val actualCtxLen = nativeGetContextLength(handle)
            Result.success(LlamaContext(actualCtxLen, true, handle, model.nativeHandle))
        } catch (e: Exception) {
            lastError = e.message
            Result.failure(LlamaError.ContextCreationFailed(e.message ?: "Unknown error"))
        }
    }

    actual suspend fun generate(
        context: LlamaContext,
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!isLibraryLoaded) {
            return@withContext Result.failure(LlamaError.LibraryNotAvailable)
        }

        if (context.nativeHandle == 0L) {
            lastError = "Context is not valid"
            return@withContext Result.failure(LlamaError.GenerationFailed(lastError!!))
        }

        try {
            val result = nativeGenerate(
                context.nativeHandle,
                context.modelHandle,
                prompt,
                maxTokens,
                temperature
            )

            if (result == null) {
                lastError = nativeGetLastError() ?: "Generation failed"
                return@withContext Result.failure(LlamaError.GenerationFailed(lastError!!))
            }

            Result.success(result)
        } catch (e: Exception) {
            lastError = e.message
            Result.failure(LlamaError.GenerationFailed(e.message ?: "Unknown error"))
        }
    }

    actual fun generateStream(
        context: LlamaContext,
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Flow<Result<String>> = flow {
        if (!isLibraryLoaded) {
            emit(Result.failure(LlamaError.LibraryNotAvailable))
            return@flow
        }

        if (context.nativeHandle == 0L) {
            lastError = "Context is not valid"
            emit(Result.failure(LlamaError.GenerationFailed(lastError!!)))
            return@flow
        }

        try {
            nativeGenerateStream(
                context.nativeHandle,
                context.modelHandle,
                prompt,
                maxTokens,
                temperature
            ) { token ->
                emit(Result.success(token))
            }
        } catch (e: Exception) {
            lastError = e.message
            emit(Result.failure(LlamaError.GenerationFailed(e.message ?: "Unknown error")))
        }
    }.flowOn(Dispatchers.IO)

    actual suspend fun freeModel(model: LlamaModel) = withContext(Dispatchers.IO) {
        if (isLibraryLoaded && model.nativeHandle != 0L) {
            nativeFreeModel(model.nativeHandle)
        }
    }

    actual suspend fun freeContext(context: LlamaContext) = withContext(Dispatchers.IO) {
        if (isLibraryLoaded && context.nativeHandle != 0L) {
            nativeFreeContext(context.nativeHandle)
        }
    }

    actual fun getLastError(): String? = lastError

    companion object {
        @Volatile
        private var isLibraryLoaded = false

        init {
            try {
                System.loadLibrary("llama")
                nativeBackendInit()
                isLibraryLoaded = true
            } catch (e: UnsatisfiedLinkError) {
                println("[LlamaAndroid] Failed to load llama library: ${e.message}")
            }
        }

        @JvmStatic
        private external fun nativeBackendInit()

        @JvmStatic
        private external fun nativeBackendFree()

        @JvmStatic
        private external fun nativeLoadModel(
            modelPath: String,
            nGpuLayers: Int,
            useMmap: Boolean,
            useMlock: Boolean
        ): Long

        @JvmStatic
        private external fun nativeFreeModel(modelHandle: Long)

        @JvmStatic
        private external fun nativeCreateContext(
            modelHandle: Long,
            contextLength: Int,
            batchSize: Int,
            nThreads: Int
        ): Long

        @JvmStatic
        private external fun nativeFreeContext(contextHandle: Long)

        @JvmStatic
        private external fun nativeGetContextLength(contextHandle: Long): Int

        @JvmStatic
        private external fun nativeGenerate(
            contextHandle: Long,
            modelHandle: Long,
            prompt: String,
            maxTokens: Int,
            temperature: Float
        ): String?

        @JvmStatic
        private external fun nativeGenerateStream(
            contextHandle: Long,
            modelHandle: Long,
            prompt: String,
            maxTokens: Int,
            temperature: Float,
            callback: (String) -> Unit
        )

        @JvmStatic
        private external fun nativeGetLastError(): String?
    }
}
