package io.writeopia.localai.llama

data class LlamaModelParams(
    val nGpuLayers: Int = 0,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false
)

data class LlamaContextParams(
    val contextLength: Int = 2048,
    val batchSize: Int = 512,
    val nThreads: Int = 4
)
