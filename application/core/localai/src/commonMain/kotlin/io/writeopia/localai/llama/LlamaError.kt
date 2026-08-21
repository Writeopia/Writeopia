package io.writeopia.localai.llama

sealed class LlamaError : Exception() {
    data object LibraryNotAvailable : LlamaError() {
        override val message: String = "llama.cpp library not available"
    }

    data object PlatformNotSupported : LlamaError() {
        override val message: String = "Local AI is not supported on this platform"
    }

    data class ModelLoadFailed(override val message: String) : LlamaError()

    data class ContextCreationFailed(override val message: String) : LlamaError()

    data class GenerationFailed(override val message: String) : LlamaError()
}
