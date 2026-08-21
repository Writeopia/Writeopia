package io.writeopia.localai.llama

import kotlinx.cinterop.COpaquePointer

actual class LlamaContext(
    actual val contextLength: Int,
    actual val isValid: Boolean,
    internal val pointer: COpaquePointer? = null,
    internal val modelPointer: COpaquePointer? = null
)
