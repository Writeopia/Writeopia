package io.writeopia.api.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class AiGenerateResponse(
    val response: String? = null,
    val done: Boolean = false,
    val error: String? = null
)
