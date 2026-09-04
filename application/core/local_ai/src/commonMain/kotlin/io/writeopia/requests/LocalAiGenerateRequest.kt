package io.writeopia.requests

import kotlinx.serialization.Serializable

@Serializable
data class LocalAiGenerateRequest(val model: String, val prompt: String, val stream: Boolean)
