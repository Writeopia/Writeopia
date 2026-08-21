package io.writeopia.localai.llama

import com.sun.jna.Pointer

actual class LlamaContext(
    actual val contextLength: Int,
    actual val isValid: Boolean
) {
    internal constructor(jvmContext: LlamaContextJvm) : this(
        contextLength = jvmContext.contextLength,
        isValid = jvmContext.isValid
    )
}

internal class LlamaContextJvm(
    val contextLength: Int,
    val pointer: Pointer?,
    val modelPointer: Pointer?,
    val isValid: Boolean
)
