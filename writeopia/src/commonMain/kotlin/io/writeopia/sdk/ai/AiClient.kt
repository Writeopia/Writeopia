package io.writeopia.sdk.ai

import io.writeopia.sdk.models.utils.ResultData

interface AiClient {

    suspend fun generateReply(
        model: String,
        prompt: String,
        url: String
    ): ResultData<List<String>> =
        ResultData.Complete(listOf())
}
