package io.writeopia.localai.llama

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Bridge class that wraps JNA calls to llama.cpp with a higher-level API.
 * Uses singleton pattern with lazy initialization.
 */
internal class LlamaJnaBridge private constructor() {

    private val library: LlamaJnaLibrary = LlamaJnaLibrary.INSTANCE
        ?: throw LlamaError.LibraryNotAvailable

    @Volatile
    private var lastError: String? = null

    @Volatile
    private var backendInitialized = false

    init {
        initBackend()
    }

    private fun initBackend() {
        if (!backendInitialized) {
            library.llama_backend_init()
            backendInitialized = true
        }
    }

    suspend fun loadModel(
        modelPath: String,
        params: LlamaModelParams
    ): Result<LlamaModelJvm> = withContext(Dispatchers.IO) {
        try {
            val nativeParams = library.llama_model_default_params().apply {
                n_gpu_layers = params.nGpuLayers
                use_mmap = params.useMmap
                use_mlock = params.useMlock
            }

            val modelPtr = library.llama_load_model_from_file(modelPath, nativeParams)
            if (modelPtr == null) {
                lastError = "Failed to load model from: $modelPath"
                return@withContext Result.failure(LlamaError.ModelLoadFailed(lastError!!))
            }

            Result.success(LlamaModelJvm(modelPath, modelPtr, true))
        } catch (e: Exception) {
            lastError = e.message
            Result.failure(LlamaError.ModelLoadFailed(e.message ?: "Unknown error"))
        }
    }

    suspend fun createContext(
        model: LlamaModelJvm,
        params: LlamaContextParams
    ): Result<LlamaContextJvm> = withContext(Dispatchers.IO) {
        try {
            if (!model.isLoaded || model.pointer == null) {
                lastError = "Model is not loaded"
                return@withContext Result.failure(LlamaError.ContextCreationFailed(lastError!!))
            }

            val nativeParams = library.llama_context_default_params().apply {
                n_ctx = params.contextLength
                n_batch = params.batchSize
                n_threads = params.nThreads
                n_threads_batch = params.nThreads
            }

            val ctxPtr = library.llama_new_context_with_model(model.pointer, nativeParams)
            if (ctxPtr == null) {
                lastError = "Failed to create context"
                return@withContext Result.failure(LlamaError.ContextCreationFailed(lastError!!))
            }

            val actualCtxLen = library.llama_n_ctx(ctxPtr)
            Result.success(LlamaContextJvm(actualCtxLen, ctxPtr, model.pointer, true))
        } catch (e: Exception) {
            lastError = e.message
            Result.failure(LlamaError.ContextCreationFailed(e.message ?: "Unknown error"))
        }
    }

    suspend fun generate(
        context: LlamaContextJvm,
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!context.isValid || context.pointer == null || context.modelPointer == null) {
                lastError = "Context is not valid"
                return@withContext Result.failure(LlamaError.GenerationFailed(lastError!!))
            }

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

    fun generateStream(
        context: LlamaContextJvm,
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Flow<Result<String>> = flow {
        try {
            if (!context.isValid || context.pointer == null || context.modelPointer == null) {
                lastError = "Context is not valid"
                emit(Result.failure(LlamaError.GenerationFailed(lastError!!)))
                return@flow
            }

            generateTokens(context, prompt, maxTokens, temperature) { token ->
                emit(Result.success(token))
            }
        } catch (e: Exception) {
            lastError = e.message
            emit(Result.failure(LlamaError.GenerationFailed(e.message ?: "Unknown error")))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun generateTokens(
        context: LlamaContextJvm,
        prompt: String,
        maxTokens: Int,
        temperature: Float,
        onToken: suspend (String) -> Unit
    ) {
        val ctx = context.pointer!!
        val model = context.modelPointer!!
        val pointerSize = Native.POINTER_SIZE

        // Clear KV cache
        library.llama_kv_cache_clear(ctx)

        // Tokenize prompt
        val tokens = IntArray(context.contextLength)
        val nTokens = library.llama_tokenize(
            model, prompt, prompt.length, tokens, tokens.size, true, false
        )

        if (nTokens < 0) {
            throw LlamaError.GenerationFailed("Failed to tokenize prompt")
        }

        // Create batch for prompt
        var batch = library.llama_batch_init(nTokens, 0, 1)

        // Fill batch with prompt tokens
        val tokenMemory = Memory((nTokens * 4).toLong())
        val posMemory = Memory((nTokens * 4).toLong())
        val nSeqIdMemory = Memory((nTokens * 4).toLong())
        val seqIdMemory = Memory((nTokens * pointerSize).toLong())
        val logitsMemory = Memory(nTokens.toLong())

        val seqIdArray = Memory(4)
        seqIdArray.setInt(0, 0)

        for (i in 0 until nTokens) {
            tokenMemory.setInt((i * 4).toLong(), tokens[i])
            posMemory.setInt((i * 4).toLong(), i)
            nSeqIdMemory.setInt((i * 4).toLong(), 1)
            seqIdMemory.setPointer((i * pointerSize).toLong(), seqIdArray)
            logitsMemory.setByte(i.toLong(), if (i == nTokens - 1) 1 else 0)
        }

        batch.n_tokens = nTokens
        batch.token = tokenMemory
        batch.pos = posMemory
        batch.n_seq_id = nSeqIdMemory
        batch.seq_id = seqIdMemory
        batch.logits = logitsMemory

        // Decode prompt
        if (library.llama_decode(ctx, batch) != 0) {
            library.llama_batch_free(batch)
            throw LlamaError.GenerationFailed("Failed to decode prompt")
        }

        library.llama_batch_free(batch)

        // Initialize sampler
        val samplerParams = LlamaSamplerChainParamsNative.ByValue()
        val sampler = library.llama_sampler_chain_init(samplerParams)
            ?: throw LlamaError.GenerationFailed("Failed to create sampler")

        val tempSampler = library.llama_sampler_init_temp(temperature)
        val distSampler = library.llama_sampler_init_dist(System.currentTimeMillis().toInt())

        if (tempSampler != null) library.llama_sampler_chain_add(sampler, tempSampler)
        if (distSampler != null) library.llama_sampler_chain_add(sampler, distSampler)

        val eosToken = library.llama_token_eos(model)
        var nCur = nTokens
        val pieceBuffer = ByteArray(256)

        // Generate tokens
        for (i in 0 until maxTokens) {
            val newToken = library.llama_sampler_sample(sampler, ctx, -1)

            if (newToken == eosToken) break

            // Convert token to string
            val pieceLen = library.llama_token_to_piece(model, newToken, pieceBuffer, pieceBuffer.size, 0, false)
            if (pieceLen > 0) {
                val piece = String(pieceBuffer, 0, pieceLen, Charsets.UTF_8)
                onToken(piece)
            }

            // Prepare batch for next token
            batch = library.llama_batch_init(1, 0, 1)

            val singleTokenMem = Memory(4)
            singleTokenMem.setInt(0, newToken)
            val singlePosMem = Memory(4)
            singlePosMem.setInt(0, nCur)
            val singleNSeqMem = Memory(4)
            singleNSeqMem.setInt(0, 1)
            val singleSeqMem = Memory(pointerSize.toLong())
            singleSeqMem.setPointer(0, seqIdArray)
            val singleLogitsMem = Memory(1)
            singleLogitsMem.setByte(0, 1)

            batch.n_tokens = 1
            batch.token = singleTokenMem
            batch.pos = singlePosMem
            batch.n_seq_id = singleNSeqMem
            batch.seq_id = singleSeqMem
            batch.logits = singleLogitsMem

            if (library.llama_decode(ctx, batch) != 0) {
                library.llama_batch_free(batch)
                break
            }

            library.llama_batch_free(batch)
            nCur++
        }

        library.llama_sampler_chain_free(sampler)
    }

    fun freeModel(model: LlamaModelJvm) {
        model.pointer?.let { library.llama_free_model(it) }
    }

    fun freeContext(context: LlamaContextJvm) {
        context.pointer?.let { library.llama_free(it) }
    }

    fun getLastError(): String? = lastError

    fun cleanup() {
        if (backendInitialized) {
            library.llama_backend_free()
            backendInitialized = false
        }
    }

    companion object {
        @Volatile
        private var instance: LlamaJnaBridge? = null

        fun getInstance(): LlamaJnaBridge? {
            if (!LlamaJnaLibrary.isAvailable()) return null

            return instance ?: synchronized(this) {
                instance ?: try {
                    LlamaJnaBridge().also { instance = it }
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }

        fun isAvailable(): Boolean = LlamaJnaLibrary.isAvailable()
    }
}
