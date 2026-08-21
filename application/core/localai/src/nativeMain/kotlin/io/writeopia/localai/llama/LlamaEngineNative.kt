@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package io.writeopia.localai.llama

import io.writeopia.localai.llama.native.*
import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import platform.posix.memcpy

actual class LlamaEngine actual constructor() {

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var backendInitialized = false

    init {
        initBackend()
    }

    private fun initBackend() {
        if (!backendInitialized) {
            try {
                llama_backend_init()
                backendInitialized = true
            } catch (e: Exception) {
                lastError = "Failed to initialize llama backend: ${e.message}"
            }
        }
    }

    actual fun isAvailable(): Boolean = backendInitialized

    actual suspend fun loadModel(
        modelPath: String,
        params: LlamaModelParams
    ): Result<LlamaModel> = withContext(Dispatchers.IO) {
        if (!backendInitialized) {
            return@withContext Result.failure(LlamaError.LibraryNotAvailable)
        }

        try {
            memScoped {
                val nativeParams = llama_model_default_params().apply {
                    n_gpu_layers = params.nGpuLayers
                    use_mmap = if (params.useMmap) 1 else 0
                    use_mlock = if (params.useMlock) 1 else 0
                }

                val modelPtr = llama_load_model_from_file(modelPath, nativeParams)
                if (modelPtr == null) {
                    lastError = "Failed to load model from: $modelPath"
                    return@withContext Result.failure(LlamaError.ModelLoadFailed(lastError!!))
                }

                Result.success(LlamaModel(modelPath, true, modelPtr))
            }
        } catch (e: Exception) {
            lastError = e.message
            Result.failure(LlamaError.ModelLoadFailed(e.message ?: "Unknown error"))
        }
    }

    actual suspend fun createContext(
        model: LlamaModel,
        params: LlamaContextParams
    ): Result<LlamaContext> = withContext(Dispatchers.IO) {
        if (!backendInitialized) {
            return@withContext Result.failure(LlamaError.LibraryNotAvailable)
        }

        if (!model.isLoaded || model.pointer == null) {
            lastError = "Model is not loaded"
            return@withContext Result.failure(LlamaError.ContextCreationFailed(lastError!!))
        }

        try {
            memScoped {
                val nativeParams = llama_context_default_params().apply {
                    n_ctx = params.contextLength
                    n_batch = params.batchSize
                    n_threads = params.nThreads
                    n_threads_batch = params.nThreads
                }

                val ctxPtr = llama_new_context_with_model(
                    model.pointer.reinterpret(),
                    nativeParams
                )
                if (ctxPtr == null) {
                    lastError = "Failed to create context"
                    return@withContext Result.failure(LlamaError.ContextCreationFailed(lastError!!))
                }

                val actualCtxLen = llama_n_ctx(ctxPtr)
                Result.success(LlamaContext(actualCtxLen, true, ctxPtr, model.pointer))
            }
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
        if (!backendInitialized) {
            return@withContext Result.failure(LlamaError.LibraryNotAvailable)
        }

        if (!context.isValid || context.pointer == null || context.modelPointer == null) {
            lastError = "Context is not valid"
            return@withContext Result.failure(LlamaError.GenerationFailed(lastError!!))
        }

        try {
            val result = StringBuilder()
            generateTokens(context, prompt, maxTokens, temperature) { token ->
                result.append(token)
            }
            Result.success(result.toString())
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
        if (!backendInitialized) {
            emit(Result.failure(LlamaError.LibraryNotAvailable))
            return@flow
        }

        if (!context.isValid || context.pointer == null || context.modelPointer == null) {
            lastError = "Context is not valid"
            emit(Result.failure(LlamaError.GenerationFailed(lastError!!)))
            return@flow
        }

        try {
            generateTokens(context, prompt, maxTokens, temperature) { token ->
                emit(Result.success(token))
            }
        } catch (e: Exception) {
            lastError = e.message
            emit(Result.failure(LlamaError.GenerationFailed(e.message ?: "Unknown error")))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun generateTokens(
        context: LlamaContext,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        onToken: suspend (String) -> Unit
    ) = memScoped {
        val ctx = context.pointer!!.reinterpret<cnames.structs.llama_context>()
        val model = context.modelPointer!!.reinterpret<cnames.structs.llama_model>()

        // Clear KV cache
        llama_kv_cache_clear(ctx)

        // Tokenize prompt
        val tokens = IntArray(context.contextLength)
        val nTokens = tokens.usePinned { pinned ->
            llama_tokenize(
                model, prompt, prompt.length,
                pinned.addressOf(0), tokens.size, 1, 0
            )
        }

        if (nTokens < 0) {
            throw LlamaError.GenerationFailed("Failed to tokenize prompt")
        }

        // Create and fill batch for prompt
        var batch = llama_batch_init(nTokens, 0, 1)

        for (i in 0 until nTokens) {
            batch.token!![i] = tokens[i]
            batch.pos!![i] = i
            batch.n_seq_id!![i] = 1
            batch.logits!![i] = if (i == nTokens - 1) 1 else 0
        }
        batch.n_tokens = nTokens

        // Decode prompt
        if (llama_decode(ctx, batch) != 0) {
            llama_batch_free(batch)
            throw LlamaError.GenerationFailed("Failed to decode prompt")
        }

        llama_batch_free(batch)

        // Initialize sampler
        val samplerParams = alloc<llama_sampler_chain_params>().apply {
            no_perf = 1
        }
        val sampler = llama_sampler_chain_init(samplerParams.readValue())
            ?: throw LlamaError.GenerationFailed("Failed to create sampler")

        val tempSampler = llama_sampler_init_temp(temperature)
        val distSampler = llama_sampler_init_dist(platform.posix.time(null).toInt())

        if (tempSampler != null) llama_sampler_chain_add(sampler, tempSampler)
        if (distSampler != null) llama_sampler_chain_add(sampler, distSampler)

        val eosToken = llama_token_eos(model)
        var nCur = nTokens
        val pieceBuffer = ByteArray(256)

        // Generate tokens
        for (i in 0 until maxTokens) {
            val newToken = llama_sampler_sample(sampler, ctx, -1)

            if (newToken == eosToken) break

            // Convert token to string
            val pieceLen = pieceBuffer.usePinned { pinned ->
                llama_token_to_piece(model, newToken, pinned.addressOf(0), pieceBuffer.size, 0, 0)
            }

            if (pieceLen > 0) {
                val piece = pieceBuffer.decodeToString(0, pieceLen)
                onToken(piece)
            }

            // Prepare batch for next token
            batch = llama_batch_init(1, 0, 1)
            batch.token!![0] = newToken
            batch.pos!![0] = nCur
            batch.n_seq_id!![0] = 1
            batch.logits!![0] = 1
            batch.n_tokens = 1

            if (llama_decode(ctx, batch) != 0) {
                llama_batch_free(batch)
                break
            }

            llama_batch_free(batch)
            nCur++
        }

        llama_sampler_chain_free(sampler)
    }

    actual suspend fun freeModel(model: LlamaModel) = withContext(Dispatchers.IO) {
        model.pointer?.let {
            llama_free_model(it.reinterpret())
        }
    }

    actual suspend fun freeContext(context: LlamaContext) = withContext(Dispatchers.IO) {
        context.pointer?.let {
            llama_free(it.reinterpret())
        }
    }

    actual fun getLastError(): String? = lastError
}
