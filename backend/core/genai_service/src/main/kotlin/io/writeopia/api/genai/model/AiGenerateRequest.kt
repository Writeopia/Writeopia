package io.writeopia.api.genai.model

import kotlinx.serialization.Serializable

@Serializable
data class AiGenerateRequest(
    val prompt: String,
    val model: String? = null,
    val stream: Boolean = false
)
