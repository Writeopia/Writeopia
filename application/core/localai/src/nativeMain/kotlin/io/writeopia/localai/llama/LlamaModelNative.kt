package io.writeopia.localai.llama

import kotlinx.cinterop.COpaquePointer

actual class LlamaModel(
    actual val modelPath: String,
    actual val isLoaded: Boolean,
    internal val pointer: COpaquePointer? = null
)
