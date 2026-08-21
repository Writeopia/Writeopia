package io.writeopia.localai.llama

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

actual class LlamaEngine actual constructor() {

    private val bridge: LlamaJnaBridge? = run {
        println("[LlamaEngineJvm] Initializing LlamaEngine...")
        println("[LlamaEngineJvm] Attempting to get bridge instance...")
        val b = LlamaJnaBridge.getInstance()
        println("[LlamaEngineJvm] Bridge instance: ${if (b != null) "obtained" else "null"}")
        if (b == null) {
            println("[LlamaEngineJvm] Failed to get bridge. JNA available: ${LlamaJnaBridge.isAvailable()}")
        }
        b
    }

    // Store internal JVM models/contexts keyed by their public wrapper's identity
    private val modelMap = mutableMapOf<String, LlamaModelJvm>()
    private val contextMap = mutableMapOf<Int, LlamaContextJvm>()
    private var contextIdCounter = 0

    actual fun isAvailable(): Boolean {
        val available = bridge != null
        println("[LlamaEngineJvm] isAvailable() = $available")
        return available
    }

    actual suspend fun loadModel(
        modelPath: String,
        params: LlamaModelParams
    ): Result<LlamaModel> {
        val b = bridge ?: return Result.failure(LlamaError.LibraryNotAvailable)

        return b.loadModel(modelPath, params).map { jvmModel ->
            modelMap[modelPath] = jvmModel
            LlamaModel(jvmModel)
        }
    }

    actual suspend fun createContext(
        model: LlamaModel,
        params: LlamaContextParams
    ): Result<LlamaContext> {
        val b = bridge ?: return Result.failure(LlamaError.LibraryNotAvailable)

        val jvmModel = modelMap[model.modelPath]
            ?: return Result.failure(LlamaError.ContextCreationFailed("Model not found"))

        return b.createContext(jvmModel, params).map { jvmContext ->
            val id = contextIdCounter++
            contextMap[id] = jvmContext
            LlamaContext(jvmContext)
        }
    }

    actual suspend fun generate(
        context: LlamaContext,
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Result<String> {
        val b = bridge ?: return Result.failure(LlamaError.LibraryNotAvailable)

        // Find matching JVM context by contextLength (simple heuristic)
        val jvmContext = contextMap.values.firstOrNull { it.contextLength == context.contextLength }
            ?: return Result.failure(LlamaError.GenerationFailed("Context not found"))

        return b.generate(jvmContext, prompt, maxTokens, temperature)
    }

    actual fun generateStream(
        context: LlamaContext,
        prompt: String,
        maxTokens: Int,
        temperature: Float
    ): Flow<Result<String>> {
        val b = bridge ?: return emptyFlow()

        val jvmContext = contextMap.values.firstOrNull { it.contextLength == context.contextLength }
            ?: return emptyFlow()

        return b.generateStream(jvmContext, prompt, maxTokens, temperature)
    }

    actual suspend fun freeModel(model: LlamaModel) {
        val b = bridge ?: return
        val jvmModel = modelMap.remove(model.modelPath) ?: return
        b.freeModel(jvmModel)
    }

    actual suspend fun freeContext(context: LlamaContext) {
        val b = bridge ?: return
        val entry = contextMap.entries.firstOrNull { it.value.contextLength == context.contextLength }
            ?: return
        contextMap.remove(entry.key)
        b.freeContext(entry.value)
    }

    actual fun getLastError(): String? = bridge?.getLastError()
}
