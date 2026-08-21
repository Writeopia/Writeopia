package io.writeopia.localai.llama

actual class LlamaContext(
    actual val contextLength: Int,
    actual val isValid: Boolean,
    internal val nativeHandle: Long = 0L,
    internal val modelHandle: Long = 0L
)
