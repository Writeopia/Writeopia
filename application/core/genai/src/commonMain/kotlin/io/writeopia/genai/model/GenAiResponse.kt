package io.writeopia.genai.model

import kotlinx.serialization.Serializable

@Serializable
data class GenAiResponse(
    val response: String? = null,
    val done: Boolean = false,
    val error: String? = null
)
