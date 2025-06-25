package io.writeopia.sdk.ai

import io.writeopia.sdk.models.utils.ResultData

interface AiClient {

    suspend fun generateListItems(
        model: String,
        context: String,
        url: String
    ): ResultData<List<String>>

    suspend fun getSelectedModel(userId: String): String?

    suspend fun getConfiguredUrl(id: String): String?
}
