package io.writeopia.localai.llama

expect class LlamaContext {
    val contextLength: Int
    val isValid: Boolean
}
