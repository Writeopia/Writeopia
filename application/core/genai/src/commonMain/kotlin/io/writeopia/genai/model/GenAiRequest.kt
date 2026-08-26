package io.writeopia.genai.model

import kotlinx.serialization.Serializable

@Serializable
data class GenAiRequest(
    val prompt: String,
    val model: String? = null,
    val stream: Boolean = false
)
