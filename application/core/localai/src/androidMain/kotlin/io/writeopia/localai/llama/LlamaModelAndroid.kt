package io.writeopia.localai.llama

actual class LlamaModel(
    actual val modelPath: String,
    actual val isLoaded: Boolean,
    internal val nativeHandle: Long = 0L
)
