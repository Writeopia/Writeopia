package io.writeopia.localai

import io.writeopia.localai.llama.LlamaContext
import io.writeopia.localai.llama.LlamaContextParams
import io.writeopia.localai.llama.LlamaEngine
import io.writeopia.localai.llama.LlamaModel
import io.writeopia.localai.llama.LlamaModelParams
import io.writeopia.localai.model.LocalAiConfig
import io.writeopia.localai.persistence.LocalAiDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LocalAiRepository(
    private val localAiDao: LocalAiDao?,
    private val llamaEngine: LlamaEngine = LlamaEngine()
) {
    private var currentModel: LlamaModel? = null
    private var currentContext: LlamaContext? = null

    fun isEngineAvailable(): Boolean = llamaEngine.isAvailable()

    suspend fun runTest(modelPath: String): Result<String> = withContext(Dispatchers.Default) {
        // Expand ~ to user home directory (platform-specific)
        val expandedPath = expandPath(modelPath)
        println("[LocalAiRepo] runTest called with modelPath: $modelPath -> expanded: $expandedPath")
        println("[LocalAiRepo] Engine available: ${llamaEngine.isAvailable()}")

        if (!llamaEngine.isAvailable()) {
            val error = llamaEngine.getLastError()
            println("[LocalAiRepo] Engine not available. Last error: $error")
            return@withContext Result.failure(
                IllegalStateException(
                    "Local AI engine not available. " +
                        "Ensure llama.cpp library is installed and accessible. Error: $error"
                )
            )
        }

        try {
            // Load model if not already loaded or path changed
            println("[LocalAiRepo] Current model: ${currentModel?.modelPath}")
            val model = currentModel?.takeIf { it.modelPath == expandedPath }
                ?: run {
                    println("[LocalAiRepo] Loading new model...")
                    // Free previous model if exists
                    currentModel?.let {
                        println("[LocalAiRepo] Freeing previous model")
                        llamaEngine.freeModel(it)
                    }
                    currentContext?.let {
                        println("[LocalAiRepo] Freeing previous context")
                        llamaEngine.freeContext(it)
                    }
                    currentContext = null

                    println("[LocalAiRepo] Calling llamaEngine.loadModel...")
                    val loadResult = llamaEngine.loadModel(
                        expandedPath,
                        LlamaModelParams(nGpuLayers = 0)
                    )
                    println("[LocalAiRepo] loadModel result: $loadResult")
                    loadResult.getOrElse { error ->
                        println("[LocalAiRepo] Model load failed: ${error.message}")
                        return@withContext Result.failure(error)
                    }.also {
                        currentModel = it
                        println("[LocalAiRepo] Model loaded successfully: ${it.modelPath}, isLoaded: ${it.isLoaded}")
                    }
                }

            // Create context if needed
            println("[LocalAiRepo] Current context: $currentContext")
            val context = currentContext ?: run {
                println("[LocalAiRepo] Creating new context...")
                val contextResult = llamaEngine.createContext(
                    model,
                    LlamaContextParams(contextLength = 2048, nThreads = 4)
                )
                println("[LocalAiRepo] createContext result: $contextResult")
                contextResult.getOrElse { error ->
                    println("[LocalAiRepo] Context creation failed: ${error.message}")
                    return@withContext Result.failure(error)
                }.also {
                    currentContext = it
                    println("[LocalAiRepo] Context created: contextLength=${it.contextLength}, isValid=${it.isValid}")
                }
            }

            // Run test generation
            val prompt = "What is 2 + 2? Answer briefly:"
            println("[LocalAiRepo] Testing with prompt: $prompt")

            val response = llamaEngine.generate(
                context = context,
                prompt = prompt,
                maxTokens = 64,
                temperature = 0.7f
            )

            response.onSuccess { text ->
                println("[LocalAiRepo] Response: $text")
            }.onFailure { error ->
                println("[LocalAiRepo] Generation error: ${error.message}")
            }

            response
        } catch (e: Exception) {
            println("[LocalAiRepo] Exception: ${e.message}")
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun generate(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): Result<String> = withContext(Dispatchers.Default) {
        val context = currentContext
            ?: return@withContext Result.failure(
                IllegalStateException("No model loaded. Call runTest first to initialize.")
            )

        llamaEngine.generate(context, prompt, maxTokens, temperature)
    }

    fun generateStream(
        prompt: String,
        maxTokens: Int = 256,
        temperature: Float = 0.7f
    ): Flow<Result<String>> {
        val context = currentContext
            ?: throw IllegalStateException("No model loaded. Call runTest first to initialize.")

        return llamaEngine.generateStream(context, prompt, maxTokens, temperature)
    }

    suspend fun cleanup() {
        currentContext?.let { llamaEngine.freeContext(it) }
        currentModel?.let { llamaEngine.freeModel(it) }
        currentContext = null
        currentModel = null
    }

    fun getLastError(): String? = llamaEngine.getLastError()

    suspend fun saveConfiguration(userId: String, config: LocalAiConfig) {
        localAiDao?.saveConfiguration(userId, config)
    }

    suspend fun getConfiguration(userId: String): LocalAiConfig? {
        return localAiDao?.getConfiguration(userId)
    }
}
