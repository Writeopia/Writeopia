package io.writeopia.ai

import io.writeopia.sdk.models.utils.ResultData
import kotlinx.coroutines.flow.Flow

/**
 * Streams the answer of an AI command. Each emission carries the whole answer received so far,
 * not only the new part, so the editor can replace the text in place.
 */
interface AiStreaming {
    fun stream(command: AiCommand, prompt: String): Flow<ResultData<String>>
}
