package io.writeopia.api.ai.model

import io.writeopia.api.genai.model.AiGenerateResponse
import io.writeopia.api.genai.model.TokenUsage
import kotlinx.coroutines.flow.Flow

sealed class AiRequestResult {
    data class Success(
        val response: AiGenerateResponse,
        val usage: TokenUsage
    ) : AiRequestResult()

    data class StreamSuccess(
        val flow: Flow<String>,
        val onComplete: suspend () -> TokenUsage?
    ) : AiRequestResult()

    data class InvalidRequest(val message: String) : AiRequestResult()

    data class Unauthorized(val message: String) : AiRequestResult()

    data class Forbidden(val message: String) : AiRequestResult()

    data class QuotaExceeded(val message: String) : AiRequestResult()

    data class Error(val message: String) : AiRequestResult()
}
