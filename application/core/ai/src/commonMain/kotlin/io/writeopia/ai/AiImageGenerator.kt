package io.writeopia.ai

import io.writeopia.genai.repository.GenAiRepository
import io.writeopia.sdk.models.utils.ResultData

/**
 * Draws a picture for a paragraph. Only the cloud draws: the local models of the app only write
 * text, so there is no local implementation and no fallback.
 */
interface AiImageGenerator {
    /**
     * The URL of a picture for [paragraph]. The title and the text of the whole document go
     * along, so the picture fits the subject and tone of the text.
     */
    suspend fun generateImage(paragraph: String, documentTitle: String, documentText: String): ResultData<String>
}

/** The Writeopia backend drawing with Gemini and storing the picture with the user's media. */
class CloudImageGenerator(private val repository: GenAiRepository) : AiImageGenerator {
    override suspend fun generateImage(
        paragraph: String,
        documentTitle: String,
        documentText: String
    ): ResultData<String> = repository.generateImage(paragraph, documentTitle, documentText)
}
