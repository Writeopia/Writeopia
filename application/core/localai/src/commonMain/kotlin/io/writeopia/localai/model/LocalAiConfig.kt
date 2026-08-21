package io.writeopia.localai.model

data class LocalAiConfig(
    val modelPath: String = "",
    val temperature: Float = 0.7f,
    val maxTokens: Int = 256,
    val contextLength: Int = 2048
)
