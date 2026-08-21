package io.writeopia.localai.llama

import com.sun.jna.Pointer

actual class LlamaModel(
    actual val modelPath: String,
    actual val isLoaded: Boolean
) {
    internal constructor(jvmModel: LlamaModelJvm) : this(
        modelPath = jvmModel.modelPath,
        isLoaded = jvmModel.isLoaded
    )
}

internal class LlamaModelJvm(
    val modelPath: String,
    val pointer: Pointer?,
    val isLoaded: Boolean
)
